"""LLM provider abstraction. DeepSeek (OpenAI-compatible API) is always tried first."""

from __future__ import annotations

import json
import logging
from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass, field
from typing import Any, Protocol

import openai
from openai import AsyncOpenAI

from app.config import Settings

log = logging.getLogger(__name__)

ChatMessage = dict[str, Any]

# Errors that justify trying the fallback provider: timeouts, connection failures, rate
# limits, 5xx, and also auth/4xx errors (e.g. revoked key), since DeepSeek is unusable then.
_TRANSIENT = (openai.APIError,)


class LLMError(Exception):
    """Base LLM error. Messages never contain secrets."""


class LLMUnavailableError(LLMError):
    """No provider is configured or every provider failed."""


@dataclass
class StreamEvent:
    """One streamed event: a text delta, or the complete tool calls of a turn."""

    text: str = ""
    tool_calls: list[dict[str, Any]] = field(default_factory=list)


class LLMProvider(Protocol):
    name: str

    async def complete_json(self, system: str, user: str, *, model: str | None = None) -> dict[str, Any]: ...

    def stream_chat(
        self, messages: Sequence[ChatMessage], *, tools: list[dict[str, Any]] | None = None
    ) -> AsyncIterator[StreamEvent]: ...


class OpenAICompatibleProvider:
    """Works with DeepSeek and any other OpenAI-compatible endpoint."""

    def __init__(self, name: str, api_key: str, base_url: str, fast_model: str, chat_model: str,
                 timeout: float, max_retries: int, http_client: Any = None) -> None:
        self.name = name
        self.fast_model = fast_model
        self.chat_model = chat_model
        self._client = AsyncOpenAI(api_key=api_key, base_url=base_url, timeout=timeout, max_retries=max_retries,
                                   http_client=http_client)

    async def complete_json(self, system: str, user: str, *, model: str | None = None) -> dict[str, Any]:
        res = await self._client.chat.completions.create(
            model=model or self.fast_model,
            messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
            response_format={"type": "json_object"},
            temperature=0.1,
        )
        content = res.choices[0].message.content or "{}"
        try:
            return json.loads(content)
        except json.JSONDecodeError as e:
            raise LLMError(f"{self.name} returned invalid JSON") from e

    async def stream_chat(
        self, messages: Sequence[ChatMessage], *, tools: list[dict[str, Any]] | None = None
    ) -> AsyncIterator[StreamEvent]:
        kwargs: dict[str, Any] = {"model": self.chat_model, "messages": list(messages), "stream": True,
                                  "temperature": 0.3}
        if tools:
            kwargs["tools"] = tools
        stream = await self._client.chat.completions.create(**kwargs)
        calls: dict[int, dict[str, Any]] = {}
        async for chunk in stream:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta
            if delta.content:
                yield StreamEvent(text=delta.content)
            for tc in delta.tool_calls or []:
                c = calls.setdefault(
                    tc.index, {"id": "", "type": "function", "function": {"name": "", "arguments": ""}})
                if tc.id:
                    c["id"] = tc.id
                if tc.function and tc.function.name:
                    c["function"]["name"] += tc.function.name
                if tc.function and tc.function.arguments:
                    c["function"]["arguments"] += tc.function.arguments
        if calls:
            yield StreamEvent(tool_calls=[calls[i] for i in sorted(calls)])


class FallbackLLM:
    """Tries providers in order (DeepSeek first) on transient failures."""

    def __init__(self, providers: Sequence[LLMProvider]) -> None:
        self.providers = list(providers)
        self.name = "+".join(p.name for p in self.providers) or "none"

    @property
    def available(self) -> bool:
        return bool(self.providers)

    async def complete_json(self, system: str, user: str, *, model: str | None = None) -> dict[str, Any]:
        last: Exception | None = None
        for p in self.providers:
            try:
                return await p.complete_json(system, user, model=model if p is self.providers[0] else None)
            except (*_TRANSIENT, LLMError) as e:
                log.warning("LLM provider %s failed: %s", p.name, type(e).__name__)
                last = e
        raise LLMUnavailableError("no LLM provider available") from last

    async def stream_chat(
        self, messages: Sequence[ChatMessage], *, tools: list[dict[str, Any]] | None = None
    ) -> AsyncIterator[StreamEvent]:
        last: Exception | None = None
        for p in self.providers:
            started = False
            try:
                async for ev in p.stream_chat(messages, tools=tools):
                    started = True
                    yield ev
                return
            except _TRANSIENT as e:
                log.warning("LLM provider %s failed while streaming: %s", p.name, type(e).__name__)
                if started:  # cannot transparently switch after output was sent
                    raise LLMError("stream interrupted") from e
                last = e
        raise LLMUnavailableError("no LLM provider available") from last


def build_llm(settings: Settings) -> FallbackLLM:
    providers: list[LLMProvider] = []
    key = settings.deepseek_api_key.get_secret_value()
    if key:
        providers.append(OpenAICompatibleProvider(
            "deepseek", key, settings.deepseek_base_url, settings.deepseek_model_fast,
            settings.deepseek_model_chat, settings.llm_timeout_seconds, settings.llm_max_retries))
    fb_key = settings.llm_fallback_api_key.get_secret_value()
    if fb_key and settings.llm_fallback_base_url and settings.llm_fallback_model:
        providers.append(OpenAICompatibleProvider(
            "fallback", fb_key, settings.llm_fallback_base_url, settings.llm_fallback_model,
            settings.llm_fallback_model, settings.llm_timeout_seconds, settings.llm_max_retries))
    if not providers:
        log.warning("No LLM API key configured; AI features run in degraded mode")
    return FallbackLLM(providers)
