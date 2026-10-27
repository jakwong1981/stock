"""Read-only research tools shared by the chat assistant and the MCP server.

Every call goes through the backend REST API using the *end user's* token, so the backend
enforces data isolation; the AI service never has broader access than the user.
No tool can place orders or modify data.
"""

from __future__ import annotations

import json
import re
from decimal import Decimal
from typing import Any

import httpx

_SYMBOL = re.compile(r"^[A-Z0-9.\-]{1,20}$")
_INTERVALS = {"1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w", "1M"}


class ToolError(Exception):
    pass


def _symbol(v: Any) -> str:
    s = str(v or "").strip().upper()
    if not _SYMBOL.match(s):
        raise ToolError("invalid symbol")
    return s


class ResearchTools:
    def __init__(self, backend_url: str, user_token: str | None, news_lookup=None,
                 client: httpx.AsyncClient | None = None) -> None:
        headers = {"Authorization": f"Bearer {user_token}"} if user_token else {}
        self._client = client or httpx.AsyncClient(base_url=backend_url, timeout=10, headers=headers)
        self._news_lookup = news_lookup  # async callable(symbol, limit) -> list[dict]

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _get(self, path: str, params: dict | None = None) -> Any:
        r = await self._client.get(path, params=params)
        if r.status_code == 401:
            raise ToolError("not authorised")
        r.raise_for_status()
        return r.json()

    async def get_quote(self, symbols: list[str]) -> Any:
        syms = [_symbol(s) for s in symbols][:20]
        return await self._get("/api/quotes", {"symbols": ",".join(syms)})

    async def get_candles(self, symbol: str, interval: str = "1d", limit: int = 60) -> Any:
        if interval not in _INTERVALS:
            raise ToolError("invalid interval")
        limit = max(1, min(int(limit), 120))
        return await self._get("/api/candles", {"symbol": _symbol(symbol), "interval": interval, "limit": limit})

    async def get_instrument(self, symbol: str) -> Any:
        return await self._get(f"/api/instruments/{_symbol(symbol)}")

    async def get_portfolio(self) -> Any:
        data = await self._get("/api/portfolio/positions")
        # Send only what the model needs (no user identifiers)
        open_positions = [p for p in data.get("positions", []) if Decimal(str(p.get("quantity") or "0")) != 0]
        return {"positions": open_positions, "summaries": data.get("summaries", [])}

    async def search_news(self, symbol: str, limit: int = 5) -> Any:
        if self._news_lookup is None:
            return []
        return await self._news_lookup(_symbol(symbol), max(1, min(int(limit), 10)))

    async def dispatch(self, name: str, arguments: str) -> str:
        """Execute a tool call from the model and return a JSON string result."""
        try:
            args = json.loads(arguments or "{}")
            if not isinstance(args, dict):
                raise ToolError("arguments must be an object")
            match name:
                case "get_quote":
                    result = await self.get_quote(list(args.get("symbols") or []))
                case "get_candles":
                    result = await self.get_candles(args.get("symbol"), args.get("interval", "1d"),
                                                    args.get("limit", 60))
                case "get_instrument":
                    result = await self.get_instrument(args.get("symbol"))
                case "get_portfolio":
                    result = await self.get_portfolio()
                case "search_news":
                    result = await self.search_news(args.get("symbol"), args.get("limit", 5))
                case _:
                    raise ToolError(f"unknown tool {name}")
        except (ToolError, ValueError, TypeError) as e:
            return json.dumps({"error": str(e)})
        except httpx.HTTPError as e:
            return json.dumps({"error": f"backend unavailable ({type(e).__name__})"})
        return json.dumps(result, ensure_ascii=False, default=str)


TOOL_SCHEMAS: list[dict[str, Any]] = [
    {"type": "function", "function": {
        "name": "get_quote", "description": "Latest real-time quotes for up to 20 symbols.",
        "parameters": {"type": "object", "properties": {
            "symbols": {"type": "array", "items": {"type": "string"}, "description": "e.g. ['AAPL','0700.HK']"}},
            "required": ["symbols"]}}},
    {"type": "function", "function": {
        "name": "get_candles", "description": "Historical OHLCV candles for one symbol.",
        "parameters": {"type": "object", "properties": {
            "symbol": {"type": "string"},
            "interval": {"type": "string", "enum": sorted(_INTERVALS)},
            "limit": {"type": "integer", "minimum": 1, "maximum": 120}},
            "required": ["symbol"]}}},
    {"type": "function", "function": {
        "name": "get_instrument", "description": "Company name, exchange and currency for a symbol.",
        "parameters": {"type": "object", "properties": {"symbol": {"type": "string"}}, "required": ["symbol"]}}},
    {"type": "function", "function": {
        "name": "get_portfolio",
        "description": "The current user's open positions with cost basis and unrealised P&L (read-only).",
        "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {
        "name": "search_news", "description": "Recent news with AI sentiment for a symbol.",
        "parameters": {"type": "object", "properties": {
            "symbol": {"type": "string"}, "limit": {"type": "integer", "minimum": 1, "maximum": 10}},
            "required": ["symbol"]}}},
]
