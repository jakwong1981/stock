"""Exercises the real OpenAI-compatible client against a stubbed DeepSeek HTTP endpoint."""

import json

import httpx

from app.llm import OpenAICompatibleProvider


def provider(handler) -> tuple[OpenAICompatibleProvider, list[httpx.Request]]:
    seen: list[httpx.Request] = []

    def wrapped(req: httpx.Request) -> httpx.Response:
        seen.append(req)
        return handler(req)

    client = httpx.AsyncClient(transport=httpx.MockTransport(wrapped))
    p = OpenAICompatibleProvider("deepseek", "sk-test-key", "https://api.deepseek.com", "deepseek-chat",
                                 "deepseek-chat", timeout=5, max_retries=0, http_client=client)
    return p, seen


async def test_complete_json_calls_deepseek_with_json_mode():
    def handler(req):
        return httpx.Response(200, json={
            "id": "x", "object": "chat.completion", "created": 0, "model": "deepseek-chat",
            "choices": [{"index": 0, "finish_reason": "stop",
                         "message": {"role": "assistant", "content": '{"score": 0.3}'}}]})

    p, seen = provider(handler)
    assert await p.complete_json("sys", "user") == {"score": 0.3}
    body = json.loads(seen[0].content)
    assert str(seen[0].url) == "https://api.deepseek.com/chat/completions"
    assert seen[0].headers["authorization"] == "Bearer sk-test-key"
    assert body["model"] == "deepseek-chat" and body["response_format"] == {"type": "json_object"}


async def test_stream_accumulates_tool_call_fragments():
    def chunk(delta):
        return "data: " + json.dumps({"id": "x", "object": "chat.completion.chunk", "created": 0,
                                      "model": "deepseek-chat",
                                      "choices": [{"index": 0, "delta": delta, "finish_reason": None}]}) + "\n\n"

    sse = (chunk({"role": "assistant", "content": "Hi"})
           + chunk({"tool_calls": [{"index": 0, "id": "call_1", "type": "function",
                                    "function": {"name": "get_quote", "arguments": '{"sym'}}]})
           + chunk({"tool_calls": [{"index": 0, "function": {"arguments": 'bols":["AAPL"]}'}}]})
           + "data: [DONE]\n\n")

    p, _ = provider(lambda req: httpx.Response(200, text=sse, headers={"content-type": "text/event-stream"}))
    events = [e async for e in p.stream_chat([{"role": "user", "content": "q"}], tools=[])]
    assert events[0].text == "Hi"
    assert events[-1].tool_calls == [{"id": "call_1", "type": "function",
                                      "function": {"name": "get_quote", "arguments": '{"symbols":["AAPL"]}'}}]
