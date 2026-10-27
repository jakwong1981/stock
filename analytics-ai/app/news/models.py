from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal


@dataclass
class RawNews:
    """Article as returned by a news provider, before AI analysis."""

    title: str
    body: str
    source: str
    url: str
    published_at: datetime  # timezone-aware UTC
    symbols: list[str] = field(default_factory=list)
    language: str = "en"


@dataclass
class NewsItem:
    id: uuid.UUID
    title: str
    body: str
    source: str
    url: str
    published_at: datetime
    symbols: list[str]
    language: str
    sentiment_score: Decimal | None
    sentiment_label: str
    is_breaking: bool
    summaries: dict[str, str]

    def localized(self, locale: str) -> dict:
        """JSON-ready view for one locale. Decimals are serialised as strings."""
        return {
            "id": str(self.id),
            "title": self.title,
            "summary": self.summaries.get(locale) or self.summaries.get("en-US") or self.body,
            "source": self.source,
            "url": self.url,
            "publishedAt": self.published_at.isoformat(),
            "symbols": self.symbols,
            "language": self.language,
            "sentimentScore": None if self.sentiment_score is None else str(self.sentiment_score),
            "sentimentLabel": self.sentiment_label,
            "isBreaking": self.is_breaking,
        }
