"""MCP server exposing CyberStock Pulse's read-only research tools.

Run locally for an MCP client (e.g. Kiro / Claude Desktop):

    CYBERSTOCK_BACKEND_URL=http://localhost:8080 CYBERSTOCK_USER_TOKEN=<jwt> \
        python -m app.mcp_server

The token is the signed-in user's JWT; the backend scopes portfolio data to that user.
All tools are read-only; there is no trading capability.
"""

from __future__ import annotations

import json
import os

from mcp.server.mcpserver import MCPServer
from mcp.types import ToolAnnotations

from app.chat.tools import ResearchTools

READ_ONLY = ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=False)


def build_server(tools: ResearchTools) -> MCPServer:
    server = MCPServer(name="cyberstock-pulse", instructions=(
        "Read-only market data and portfolio tools for CyberStock Pulse (US & HK equities). "
        "Monetary values are decimal strings. Not investment advice."))

    @server.tool(description="Latest real-time quotes for up to 20 symbols, e.g. ['AAPL','0700.HK'].",
                 annotations=READ_ONLY)
    async def get_quote(symbols: list[str]) -> str:
        return await tools.dispatch("get_quote", json.dumps({"symbols": symbols}))

    @server.tool(description="Historical OHLCV candles. interval: 1m,5m,15m,30m,1h,4h,1d,1w,1M; limit<=120.",
                 annotations=READ_ONLY)
    async def get_candles(symbol: str, interval: str = "1d", limit: int = 60) -> str:
        return await tools.dispatch("get_candles", json.dumps({"symbol": symbol, "interval": interval,
                                                               "limit": limit}))

    @server.tool(description="Company name, exchange and currency for a symbol.", annotations=READ_ONLY)
    async def get_instrument(symbol: str) -> str:
        return await tools.dispatch("get_instrument", json.dumps({"symbol": symbol}))

    @server.tool(description="The signed-in user's open positions and P&L (requires CYBERSTOCK_USER_TOKEN).",
                 annotations=READ_ONLY)
    async def get_portfolio() -> str:
        return await tools.dispatch("get_portfolio", "{}")

    return server


def main() -> None:
    backend = os.environ.get("CYBERSTOCK_BACKEND_URL", "http://localhost:8080")
    token = os.environ.get("CYBERSTOCK_USER_TOKEN") or None
    build_server(ResearchTools(backend, token)).run("stdio")


if __name__ == "__main__":
    main()
