from __future__ import annotations

import time
from typing import Any

import jwt
import pytest

from app.config import Settings
from app.llm import FallbackLLM, StreamEvent

JWT_SECRET = "test-secret-test-secret-test-secret-0123456789"


class FakeProvider:
    """Scripted LLM: returns queued JSON for complete_json and queued turns for stream_chat."""

    def __init__(self, name: str = "deepseek", json_replies: list[Any] | None = None,
                 turns: list[list[StreamEvent]] | None = None, fail: Exception | None = None) -> None:
        self.name = name
        self.json_replies = list(json_replies or [])
        self.turns = list(turns or [])
        self.fail = fail
        self.calls: list[Any] = []

    async def complete_json(self, system: str, user: str, *, model: str | None = None) -> dict:
        self.calls.append(user)
        if self.fail:
            raise self.fail
        return self.json_replies.pop(0)

    async def stream_chat(self, messages, *, tools=None):
        self.calls.append(list(messages))
        if self.fail:
            raise self.fail
        for ev in self.turns.pop(0):
            yield ev


@pytest.fixture
def settings() -> Settings:
    return Settings(jwt_secret=JWT_SECRET, news_ingest_enabled=False, database_url="", redis_url="",
                    cors_allowed_origins="http://localhost")


def make_token(sub: str = "11111111-1111-1111-1111-111111111111", secret: str = JWT_SECRET,
               issuer: str = "cyberstock-pulse", exp_offset: int = 600) -> str:
    now = int(time.time())
    return jwt.encode({"sub": sub, "iss": issuer, "iat": now, "exp": now + exp_offset}, secret, algorithm="HS256")


def fallback(*providers) -> FallbackLLM:
    return FallbackLLM(list(providers))
