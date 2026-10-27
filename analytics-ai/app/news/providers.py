"""News sources: Yahoo Finance RSS (real, no key), Finnhub (real, key) and a mock wire (dev)."""

from __future__ import annotations

import logging
import random
import uuid
from datetime import UTC, date, datetime, timedelta
from email.utils import parsedate_to_datetime
from typing import Protocol
from urllib.parse import urlparse

import httpx
from defusedxml import ElementTree

from app.news.models import RawNews

log = logging.getLogger(__name__)

US_SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "AMZN", "GOOGL", "META", "JPM"]
HK_SYMBOLS = ["0700.HK", "9988.HK", "3690.HK", "0005.HK", "1810.HK"]


class NewsProvider(Protocol):
    name: str

    async def fetch(self) -> list[RawNews]: ...


class MockNewsProvider:
    """Generates clearly-labelled synthetic headlines. Never presented as real news."""

    name = "mock"
    _TEMPLATES = [
        ("{s} beats quarterly revenue expectations on strong demand",
         "{s} reported revenue above analyst estimates, citing robust demand and margin expansion."),
        ("{s} shares slide after regulator opens inquiry",
         "Regulators opened a preliminary inquiry into {s}; the company said it will cooperate fully."),
        ("{s} announces share buyback programme",
         "{s} unveiled a new share repurchase programme alongside steady guidance for the year."),
        ("Analysts mixed on {s} ahead of product launch",
         "Analysts are split on {s} ahead of its upcoming launch, with valuations seen as stretched."),
        ("{s} cuts full-year outlook citing supply constraints",
         "{s} lowered its full-year forecast, pointing to supply constraints and softer pricing."),
    ]

    def __init__(self, seed: int | None = None) -> None:
        self._rnd = random.Random(seed)  # noqa: S311 - not used for security

    async def fetch(self) -> list[RawNews]:
        out = []
        for _ in range(self._rnd.randint(1, 2)):
            sym = self._rnd.choice(US_SYMBOLS + HK_SYMBOLS)
            title, body = self._rnd.choice(self._TEMPLATES)
            out.append(RawNews(
                title=title.format(s=sym),
                body=body.format(s=sym),
                source="CyberStock Mock Wire",
                url=f"https://mock.cyberstock.local/news/{uuid.uuid4()}",
                published_at=datetime.now(UTC),
                symbols=[sym],
            ))
        return out


class YahooNewsProvider:
    """Real headlines from Yahoo Finance's per-symbol RSS feed (no API key).

    Unofficial feed: no SLA and personal use only. One request per symbol per poll. Only
    recent items are returned, capped per symbol, which bounds DeepSeek usage: each new
    article costs one analysis call, and articles already stored are never re-analysed.
    """

    name = "yahoo"
    URL = "https://feeds.finance.yahoo.com/rss/2.0/headline"
    USER_AGENT = "Mozilla/5.0 (compatible; CyberStockPulse/0.1)"
    _SOURCE_NAMES = {"finance.yahoo.com": "Yahoo Finance"}

    def __init__(self, symbols: list[str] | None = None, max_per_symbol: int = 5, max_age_days: int = 3,
                 client: httpx.AsyncClient | None = None) -> None:
        self._symbols = symbols or US_SYMBOLS + HK_SYMBOLS
        self._max_per_symbol = max_per_symbol
        self._max_age = timedelta(days=max_age_days)
        self._client = client or httpx.AsyncClient(timeout=10)

    async def fetch(self) -> list[RawNews]:
        by_url: dict[str, RawNews] = {}
        for sym in self._symbols:
            try:
                # Yahoo rejects requests without a browser-like User-Agent
                r = await self._client.get(self.URL, params={"s": sym, "region": "US", "lang": "en-US"},
                                           headers={"User-Agent": self.USER_AGENT})
                r.raise_for_status()
                items = self.parse(r.text, sym)
            except (httpx.HTTPError, ElementTree.ParseError, ValueError) as e:
                log.warning("Yahoo news failed for %s: %s", sym, type(e).__name__)
                continue
            for n in items:
                # The same article often appears in several symbols' feeds: keep one, merge symbols
                if n.url in by_url:
                    by_url[n.url].symbols = sorted(set(by_url[n.url].symbols) | set(n.symbols))
                else:
                    by_url[n.url] = n
        return sorted(by_url.values(), key=lambda n: n.published_at)

    def parse(self, xml_text: str, symbol: str, now: datetime | None = None) -> list[RawNews]:
        root = ElementTree.fromstring(xml_text)
        cutoff = (now or datetime.now(UTC)) - self._max_age
        out: list[RawNews] = []
        for item in root.iterfind("./channel/item"):
            title = (item.findtext("title") or "").strip()
            link = (item.findtext("link") or "").strip()
            if not title or not link.startswith("https://"):
                continue
            try:
                published = parsedate_to_datetime(item.findtext("pubDate") or "").astimezone(UTC)
            except (TypeError, ValueError):
                continue
            if published < cutoff:
                continue
            out.append(RawNews(
                title=title[:500],
                body=(item.findtext("description") or "").strip()[:4000],
                source=self._source(link),
                url=link[:1000],
                published_at=published,
                symbols=[symbol],
            ))
        out.sort(key=lambda n: n.published_at, reverse=True)
        return out[: self._max_per_symbol]

    @classmethod
    def _source(cls, link: str) -> str:
        host = (urlparse(link).hostname or "").removeprefix("www.")
        return cls._SOURCE_NAMES.get(host, host or "Yahoo Finance")[:100]


class FinnhubNewsProvider:
    """Finnhub general + company news. API key is sent via header, never in URLs."""

    name = "finnhub"
    BASE = "https://finnhub.io/api/v1"

    def __init__(self, api_key: str, symbols: list[str] | None = None, client: httpx.AsyncClient | None = None):
        if not api_key:
            raise ValueError("FINNHUB_API_KEY is required when NEWS_PROVIDER=finnhub")
        self._symbols = symbols or US_SYMBOLS
        self._client = client or httpx.AsyncClient(timeout=10, headers={"X-Finnhub-Token": api_key})

    async def fetch(self) -> list[RawNews]:
        out: list[RawNews] = []
        today = date.today()
        frm = (today - timedelta(days=2)).isoformat()
        for sym in self._symbols:
            try:
                r = await self._client.get(f"{self.BASE}/company-news",
                                           params={"symbol": sym, "from": frm, "to": today.isoformat()})
                r.raise_for_status()
                for a in r.json()[:5]:
                    out.append(self._to_raw(a, [sym]))
            except (httpx.HTTPError, ValueError, KeyError) as e:
                log.warning("Finnhub news failed for %s: %s", sym, type(e).__name__)
        return out

    @staticmethod
    def _to_raw(a: dict, symbols: list[str]) -> RawNews:
        related = [s for s in str(a.get("related") or "").split(",") if s]
        return RawNews(
            title=str(a["headline"])[:500],
            body=str(a.get("summary") or "")[:4000],
            source=str(a.get("source") or "Finnhub")[:100],
            url=str(a["url"])[:1000],
            published_at=datetime.fromtimestamp(int(a.get("datetime") or 0), tz=UTC),
            symbols=sorted(set(symbols + related)),
        )
