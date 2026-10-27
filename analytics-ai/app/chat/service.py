"""AI research assistant: DeepSeek tool-calling loop with streamed final answer."""

from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

from app.chat.tools import TOOL_SCHEMAS, ResearchTools
from app.llm import FallbackLLM, LLMError
from app.privacy import redact_pii

DISCLAIMERS = {
    "en-US": "For information only. This is not investment advice.",
    "zh-HK": "以上內容僅供參考，並不構成任何投資建議。",
    "zh-TW": "以上內容僅供參考，不構成任何投資建議。",
    "zh-CN": "以上内容仅供参考，不构成任何投资建议。",
}

LANGUAGE_NAMES = {
    "en-US": "English",
    "zh-HK": "Traditional Chinese as used in Hong Kong (香港繁體中文, use HK financial terms such as 孖展, 窩輪)",
    "zh-TW": "Traditional Chinese as used in Taiwan (台灣正體中文, use Taiwan financial terms such as 融資, 權證)",
    "zh-CN": "Simplified Chinese (简体中文)",
}


def system_prompt(locale: str) -> str:
    return (
        "You are CyberStock Pulse's research assistant for US and Hong Kong equities. "
        "Use the provided tools to look up live quotes, candles, news and the user's portfolio instead of guessing. "
        "Quote numbers exactly as returned by tools. Explain reasoning concisely. "
        "Never recommend specific buy/sell actions and never claim certainty about future prices. "
        f"Always answer in {LANGUAGE_NAMES.get(locale, 'English')}. "
        f"End every answer with this disclaimer on its own line: {DISCLAIMERS.get(locale, DISCLAIMERS['en-US'])}"
    )


def sanitize_history(messages: list[dict[str, Any]], max_messages: int, max_chars: int) -> list[dict[str, str]]:
    """Keep only user/assistant text turns, trimmed and with PII redacted."""
    clean = []
    for m in messages[-max_messages:]:
        role = m.get("role")
        content = m.get("content")
        if role not in ("user", "assistant") or not isinstance(content, str) or not content.strip():
            continue
        clean.append({"role": role, "content": redact_pii(content[:max_chars])})
    return clean


def sse(event: str, data: Any) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


class ChatService:
    def __init__(self, llm: FallbackLLM, max_tool_rounds: int = 4) -> None:
        self.llm = llm
        self.max_tool_rounds = max_tool_rounds

    async def stream(self, history: list[dict[str, str]], locale: str, tools: ResearchTools) -> AsyncIterator[str]:
        """Yields Server-Sent Events: `tool` (tool name in use), `delta` (text), `done`, `error`."""
        if not self.llm.available:
            yield sse("error", {"code": "LLM_NOT_CONFIGURED"})
            return
        messages: list[dict[str, Any]] = [{"role": "system", "content": system_prompt(locale)}, *history]
        try:
            for _ in range(self.max_tool_rounds + 1):
                text_parts: list[str] = []
                tool_calls: list[dict[str, Any]] = []
                async for ev in self.llm.stream_chat(messages, tools=TOOL_SCHEMAS):
                    if ev.text:
                        text_parts.append(ev.text)
                        yield sse("delta", {"text": ev.text})
                    if ev.tool_calls:
                        tool_calls = ev.tool_calls
                if not tool_calls:
                    yield sse("done", {})
                    return
                messages.append({"role": "assistant", "content": "".join(text_parts) or None,
                                 "tool_calls": tool_calls})
                for call in tool_calls:
                    name = call["function"]["name"]
                    yield sse("tool", {"name": name})
                    result = await tools.dispatch(name, call["function"]["arguments"])
                    messages.append({"role": "tool", "tool_call_id": call["id"], "content": result})
            yield sse("error", {"code": "TOO_MANY_TOOL_CALLS"})
        except LLMError:
            yield sse("error", {"code": "LLM_UNAVAILABLE"})
