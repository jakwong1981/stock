"""FastAPI entry point for the analytics & AI service."""

from __future__ import annotations

import asyncio
import hashlib
import logging
import time
from collections import defaultdict, deque
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app import i18n
from app.analysis import NewsAnalyzer
from app.auth import CurrentUser, make_require_user
from app.chat.service import ChatService, sanitize_history, sse
from app.chat.tools import ResearchTools
from app.config import Settings, get_settings
from app.llm import FallbackLLM, build_llm
from app.news.providers import FinnhubNewsProvider, MockNewsProvider, NewsProvider, YahooNewsProvider
from app.news.service import NewsService
from app.news.store import InMemoryNewsStore, NewsStore, PostgresNewsStore
from app.privacy import SecretMaskingFilter

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
for h in logging.getLogger().handlers:
    h.addFilter(SecretMaskingFilter())
log = logging.getLogger("cyberstock.ai")


class ChatRequest(BaseModel):
    messages: list[dict] = Field(default_factory=list, max_length=100)
    locale: str | None = None


class SentimentRequest(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    body: str = Field(default="", max_length=8000)
    symbols: list[str] = Field(default_factory=list, max_length=20)


class RateLimiter:
    """Sliding-window limiter per user (in-process; use Redis when scaling out)."""

    def __init__(self, limit: int, window_seconds: float) -> None:
        self.limit, self.window = limit, window_seconds
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        q = self._hits[key]
        while q and now - q[0] > self.window:
            q.popleft()
        if len(q) >= self.limit:
            return False
        q.append(now)
        return True


def _resolve_locale(locale: str | None, accept_language: str | None) -> str:
    return i18n.normalize_locale(locale or accept_language)


def _build_store(settings: Settings) -> NewsStore:
    if settings.database_url:
        return PostgresNewsStore(settings.database_url)
    return InMemoryNewsStore()


def _build_provider(settings: Settings) -> NewsProvider:
    match settings.news_provider.strip().lower():
        case "yahoo":
            return YahooNewsProvider()
        case "finnhub":
            return FinnhubNewsProvider(settings.finnhub_api_key.get_secret_value())
        case "mock":
            return MockNewsProvider()
        case other:
            raise ValueError(f"Unknown NEWS_PROVIDER: {other} (expected yahoo | mock | finnhub)")


def _build_seen_lock(settings: Settings):
    if not settings.redis_url:
        return None
    import redis.asyncio as redis

    client = redis.from_url(settings.redis_url)

    async def acquire(url: str) -> bool:
        key = "news:seen:" + hashlib.sha256(url.encode()).hexdigest()
        try:
            return bool(await client.set(key, "1", nx=True, ex=86400))
        except Exception:  # Redis down: process anyway, DB UNIQUE(url) still prevents duplicates
            log.warning("Redis unavailable for news de-duplication")
            return True

    return acquire


def create_app(settings: Settings | None = None, *, llm: FallbackLLM | None = None,
               news_provider: NewsProvider | None = None, store: NewsStore | None = None,
               tools_factory=None) -> FastAPI:
    settings = settings or get_settings()
    llm = llm or build_llm(settings)
    news = NewsService(store or _build_store(settings), news_provider or _build_provider(settings),
                       NewsAnalyzer(llm), seen_lock=_build_seen_lock(settings))
    chat = ChatService(llm, settings.chat_max_tool_rounds)
    limiter = RateLimiter(limit=20, window_seconds=300)
    require_user = make_require_user(settings)

    async def news_lookup(symbol: str, limit: int) -> list[dict]:
        return [n.localized("en-US") for n in await news.store.list(symbol, limit)]

    def make_tools(user: CurrentUser) -> ResearchTools:
        if tools_factory:
            return tools_factory(user, news_lookup)
        return ResearchTools(settings.backend_url, user.token, news_lookup)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        log.info("AI service starting (llm=%s, news=%s)", llm.name, news.provider.name)
        if settings.news_ingest_enabled:
            news.start(settings.news_poll_seconds)
        yield
        await news.stop()

    app = FastAPI(title="CyberStock Pulse AI", version="0.1.0", lifespan=lifespan,
                  docs_url="/ai/docs", openapi_url="/ai/openapi.json")
    app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["GET", "POST"],
                       allow_headers=["Authorization", "Content-Type", "Accept-Language"])
    app.state.news = news

    @app.get("/ai/health")
    async def health() -> dict:
        return {"status": "UP", "llm": llm.name, "llmConfigured": llm.available, "newsProvider": news.provider.name}

    @app.get("/ai/news")
    async def list_news(symbol: str | None = Query(default=None, max_length=20),
                        limit: int = Query(default=30, ge=1, le=100),
                        locale: str | None = None,
                        accept_language: str | None = Header(default=None)) -> list[dict]:
        loc = _resolve_locale(locale, accept_language)
        items = await news.store.list(symbol.upper() if symbol else None, limit)
        return [n.localized(loc) for n in items]

    @app.get("/ai/news/stream")
    async def stream_news(request: Request, locale: str | None = None,
                          accept_language: str | None = Header(default=None)) -> StreamingResponse:
        loc = _resolve_locale(locale, accept_language)

        async def gen() -> AsyncIterator[str]:
            q = news.subscribe()
            try:
                yield ": connected\n\n"
                while not await request.is_disconnected():
                    try:
                        item = await asyncio.wait_for(q.get(), timeout=15)
                        yield sse("news", item.localized(loc))
                    except TimeoutError:
                        yield ": keep-alive\n\n"  # keeps mobile/NAT connections open
            finally:
                news.unsubscribe(q)

        return StreamingResponse(gen(), media_type="text/event-stream",
                                 headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    @app.post("/ai/chat")
    async def chat_endpoint(req: ChatRequest, user: CurrentUser = Depends(require_user),
                            accept_language: str | None = Header(default=None)) -> StreamingResponse:
        if not limiter.allow(user.user_id):
            raise HTTPException(429, detail={"code": "RATE_LIMITED"})
        history = sanitize_history(req.messages, settings.chat_max_messages, settings.chat_max_chars)
        if not history or history[-1]["role"] != "user":
            raise HTTPException(400, detail={"code": "INVALID_CHAT_REQUEST"})
        loc = _resolve_locale(req.locale, accept_language)
        tools = make_tools(user)

        async def gen() -> AsyncIterator[str]:
            try:
                async for chunk in chat.stream(history, loc, tools):
                    yield chunk
            finally:
                await tools.aclose()

        return StreamingResponse(gen(), media_type="text/event-stream",
                                 headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    @app.post("/ai/sentiment")
    async def sentiment(req: SentimentRequest, user: CurrentUser = Depends(require_user)) -> dict:
        if not limiter.allow(user.user_id):
            raise HTTPException(429, detail={"code": "RATE_LIMITED"})
        a = await news.analyzer.analyze(req.title, req.body, req.symbols)
        return {"score": None if a.score is None else str(a.score), "label": a.label, "summaries": a.summaries}

    return app
# Run with: uvicorn app.main:create_app --factory
