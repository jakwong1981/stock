"""News ingestion loop: fetch → de-duplicate → DeepSeek analysis → store → push to subscribers."""

from __future__ import annotations

import asyncio
import contextlib
import logging
import uuid
from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.analysis import NewsAnalyzer
from app.news.models import NewsItem, RawNews
from app.news.providers import NewsProvider
from app.news.store import NewsStore

log = logging.getLogger(__name__)

BREAKING_THRESHOLD = Decimal("0.70")
BREAKING_MAX_AGE = timedelta(hours=2)


class NewsService:
    def __init__(self, store: NewsStore, provider: NewsProvider, analyzer: NewsAnalyzer,
                 seen_lock=None) -> None:
        self.store = store
        self.provider = provider
        self.analyzer = analyzer
        self._seen_lock = seen_lock  # optional async callable(url) -> bool, for multi-replica dedupe
        self._subscribers: set[asyncio.Queue[NewsItem]] = set()
        self._task: asyncio.Task | None = None

    # ---- pub/sub for SSE clients ----
    def subscribe(self) -> asyncio.Queue[NewsItem]:
        q: asyncio.Queue[NewsItem] = asyncio.Queue(maxsize=100)
        self._subscribers.add(q)
        return q

    def unsubscribe(self, q: asyncio.Queue[NewsItem]) -> None:
        self._subscribers.discard(q)

    def _publish(self, item: NewsItem) -> None:
        for q in list(self._subscribers):
            try:
                q.put_nowait(item)
            except asyncio.QueueFull:
                pass  # slow client: drop rather than block ingestion

    # ---- ingestion ----
    async def ingest_once(self) -> list[NewsItem]:
        added: list[NewsItem] = []
        for raw in await self.provider.fetch():
            if await self.store.exists(raw.url):
                continue
            if self._seen_lock is not None and not await self._seen_lock(raw.url):
                continue  # another replica is processing it
            item = await self._analyze(raw)
            if await self.store.add(item):
                added.append(item)
                self._publish(item)
        return added

    async def _analyze(self, raw: RawNews) -> NewsItem:
        a = await self.analyzer.analyze(raw.title, raw.body, raw.symbols)
        # Only recent strong-sentiment articles count as breaking (real feeds return days-old items)
        recent = datetime.now(UTC) - raw.published_at <= BREAKING_MAX_AGE
        breaking = recent and a.score is not None and abs(a.score) >= BREAKING_THRESHOLD
        return NewsItem(id=uuid.uuid4(), title=raw.title, body=raw.body, source=raw.source, url=raw.url,
                        published_at=raw.published_at, symbols=raw.symbols, language=raw.language,
                        sentiment_score=a.score, sentiment_label=a.label, is_breaking=breaking,
                        summaries=a.summaries)

    async def run(self, interval_seconds: int) -> None:
        while True:
            try:
                added = await self.ingest_once()
                if added:
                    log.info("Ingested %d news items", len(added))
            except Exception:  # keep the loop alive on any provider/store failure
                log.exception("News ingestion cycle failed")
            await asyncio.sleep(interval_seconds)

    def start(self, interval_seconds: int) -> None:
        if self._task is None:
            self._task = asyncio.create_task(self.run(interval_seconds), name="news-ingest")

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None
