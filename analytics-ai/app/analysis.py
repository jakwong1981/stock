"""News sentiment scoring and multilingual summaries via DeepSeek (single call per article)."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from decimal import ROUND_HALF_EVEN, Decimal, InvalidOperation

from app import i18n
from app.llm import FallbackLLM, LLMError

log = logging.getLogger(__name__)

BULLISH, BEARISH, NEUTRAL, UNANALYZED = "BULLISH", "BEARISH", "NEUTRAL", "UNANALYZED"
_TWO_DP = Decimal("0.01")

SYSTEM_PROMPT = """You are a financial news analyst. Analyse the article and reply with JSON only:
{"score": <number between -1 and 1, negative = bearish for the mentioned stocks>,
 "summary_en": "<neutral 1-2 sentence English summary>",
 "summary_zh": "<the same summary in Simplified Chinese>"}
Be factual. Do not give investment advice."""


@dataclass(frozen=True)
class Analysis:
    score: Decimal | None
    label: str
    summaries: dict[str, str]  # locale -> summary


def label_for(score: Decimal | None) -> str:
    if score is None:
        return UNANALYZED
    if score >= Decimal("0.25"):
        return BULLISH
    if score <= Decimal("-0.25"):
        return BEARISH
    return NEUTRAL


def parse_score(raw: object) -> Decimal | None:
    """Parse the model's score into a Decimal clamped to [-1, 1] with 2 dp (NUMERIC(3,2))."""
    try:
        # str() first so floats are converted from their shortest repr, not binary expansion
        d = Decimal(str(raw))
    except (InvalidOperation, ValueError, TypeError):
        return None
    if not d.is_finite():
        return None
    d = max(Decimal(-1), min(Decimal(1), d))
    return d.quantize(_TWO_DP, rounding=ROUND_HALF_EVEN)


def summaries_from(en: str, zh_simplified: str) -> dict[str, str]:
    zh = zh_simplified or ""
    return {
        "en-US": en,
        "zh-CN": zh,
        "zh-HK": i18n.from_simplified(zh, "zh-HK"),
        "zh-TW": i18n.from_simplified(zh, "zh-TW"),
    }


class NewsAnalyzer:
    def __init__(self, llm: FallbackLLM) -> None:
        self.llm = llm

    async def analyze(self, title: str, body: str, symbols: list[str]) -> Analysis:
        fallback_text = body or title
        if not self.llm.available:
            return Analysis(None, UNANALYZED, {loc: fallback_text for loc in i18n.SUPPORTED})
        user = f"Stocks: {', '.join(symbols) or 'n/a'}\nTitle: {title}\nArticle: {body[:4000]}"
        try:
            data = await self.llm.complete_json(SYSTEM_PROMPT, user)
        except LLMError as e:
            log.warning("News analysis degraded: %s", type(e).__name__)
            return Analysis(None, UNANALYZED, {loc: fallback_text for loc in i18n.SUPPORTED})
        score = parse_score(data.get("score"))
        en = str(data.get("summary_en") or fallback_text)[:1000]
        zh = str(data.get("summary_zh") or "")[:1000]
        sums = summaries_from(en, zh) if zh else {loc: en for loc in i18n.SUPPORTED}
        return Analysis(score, label_for(score), sums)
