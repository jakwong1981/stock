"""News persistence: in-memory (dev/tests) or PostgreSQL (tables created by backend Flyway)."""

from __future__ import annotations

import logging
import uuid
from typing import Protocol

from app.news.models import NewsItem

log = logging.getLogger(__name__)


class NewsStore(Protocol):
    async def exists(self, url: str) -> bool: ...

    async def add(self, item: NewsItem) -> bool: ...

    async def list(self, symbol: str | None, limit: int) -> list[NewsItem]: ...


class InMemoryNewsStore:
    MAX_ITEMS = 2000

    def __init__(self) -> None:
        self._by_url: dict[str, NewsItem] = {}

    async def exists(self, url: str) -> bool:
        return url in self._by_url

    async def add(self, item: NewsItem) -> bool:
        if item.url in self._by_url:
            return False
        self._by_url[item.url] = item
        if len(self._by_url) > self.MAX_ITEMS:
            oldest = min(self._by_url.values(), key=lambda n: n.published_at)
            del self._by_url[oldest.url]
        return True

    async def list(self, symbol: str | None, limit: int) -> list[NewsItem]:
        items = [n for n in self._by_url.values() if symbol is None or symbol in n.symbols]
        items.sort(key=lambda n: n.published_at, reverse=True)
        return items[:limit]


class PostgresNewsStore:
    """Uses parameterised queries only. One short-lived connection per call (low volume)."""

    def __init__(self, dsn: str) -> None:
        self._dsn = dsn

    async def _conn(self):
        import psycopg

        return await psycopg.AsyncConnection.connect(self._dsn, autocommit=False)

    async def exists(self, url: str) -> bool:
        async with await self._conn() as c, c.cursor() as cur:
            await cur.execute("SELECT 1 FROM financial_news WHERE url = %s", (url,))
            return await cur.fetchone() is not None

    async def add(self, item: NewsItem) -> bool:
        async with await self._conn() as c:
            async with c.cursor() as cur:
                await cur.execute(
                    """INSERT INTO financial_news (id, title, summary, source, url, language, sentiment_score,
                                                   sentiment_label, is_breaking, published_at)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                       ON CONFLICT (url) DO NOTHING RETURNING id""",
                    (item.id, item.title, item.body, item.source, item.url, item.language, item.sentiment_score,
                     item.sentiment_label, item.is_breaking, item.published_at),
                )
                if await cur.fetchone() is None:
                    return False
                for s in item.symbols:
                    # Only link symbols that exist in the instrument master (FK)
                    await cur.execute(
                        """INSERT INTO news_symbols (news_id, symbol)
                           SELECT %s, symbol FROM instruments WHERE symbol = %s ON CONFLICT DO NOTHING""",
                        (item.id, s),
                    )
                for loc, text in item.summaries.items():
                    await cur.execute(
                        "INSERT INTO news_summaries (news_id, locale, summary) VALUES (%s, %s, %s) "
                        "ON CONFLICT DO NOTHING",
                        (item.id, loc, text),
                    )
            await c.commit()
        return True

    async def list(self, symbol: str | None, limit: int) -> list[NewsItem]:
        where, params = "", []
        if symbol:
            where = "WHERE EXISTS (SELECT 1 FROM news_symbols s WHERE s.news_id = n.id AND s.symbol = %s)"
            params.append(symbol)
        params.append(limit)
        async with await self._conn() as c, c.cursor() as cur:
            await cur.execute(
                f"""SELECT n.id, n.title, coalesce(n.summary, ''), n.source, n.url, n.published_at,
                           coalesce(n.language, 'en'), n.sentiment_score, coalesce(n.sentiment_label, 'UNANALYZED'),
                           n.is_breaking,
                           coalesce((SELECT array_agg(symbol) FROM news_symbols s WHERE s.news_id = n.id), '{{}}'),
                           coalesce((SELECT json_object_agg(locale, summary) FROM news_summaries m
                                     WHERE m.news_id = n.id), '{{}}'::json)
                    FROM financial_news n {where}
                    ORDER BY n.published_at DESC LIMIT %s""",  # noqa: S608 - `where` is a constant
                params,
            )
            rows = await cur.fetchall()
        return [
            NewsItem(id=r[0] if isinstance(r[0], uuid.UUID) else uuid.UUID(str(r[0])), title=r[1], body=r[2],
                     source=r[3], url=r[4], published_at=r[5], language=r[6], sentiment_score=r[7],
                     sentiment_label=r[8], is_breaking=r[9], symbols=list(r[10]), summaries=dict(r[11]))
            for r in rows
        ]
