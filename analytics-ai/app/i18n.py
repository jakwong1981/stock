"""Locale handling and Simplified ↔ Traditional conversion with regional financial terms."""

from functools import lru_cache

import opencc

SUPPORTED = ("zh-HK", "zh-TW", "zh-CN", "en-US")
DEFAULT = "en-US"

# Regional financial vocabulary applied after OpenCC conversion (AGENT.MD §4.5).
# Kept deliberately small and specific to avoid altering unrelated text.
_HK_TERMS = {
    "衍生權證": "窩輪",
    "權證": "窩輪",
    "保證金交易": "孖展交易",
    "保證金帳戶": "孖展戶口",
    "首次公開募股": "首次公開招股",
    "交易所交易基金": "交易所買賣基金",
}
_TW_TERMS = {
    "首次公開募股": "首次公開發行",
    "保證金交易": "融資交易",
    "交易所交易基金": "指數股票型基金",
}


def normalize_locale(tag: str | None) -> str:
    """Map a BCP-47 tag (or Accept-Language header) to a supported locale."""
    if not tag:
        return DEFAULT
    for part in tag.split(","):
        t = part.split(";")[0].strip().lower()
        if not t:
            continue
        if t in ("zh-tw",) or t.startswith("zh-hant-tw"):
            return "zh-TW"
        if t in ("zh-hk", "zh-mo") or t.startswith("zh-hant"):
            return "zh-HK"
        if t in ("zh", "zh-cn", "zh-sg") or t.startswith("zh-hans"):
            return "zh-CN"
        if t.startswith("en"):
            return "en-US"
    return DEFAULT


@lru_cache(maxsize=4)
def _converter(config: str) -> opencc.OpenCC:
    return opencc.OpenCC(config)


def _apply_terms(text: str, terms: dict[str, str]) -> str:
    # Longest keys first so "衍生權證" wins over "權證"
    for k in sorted(terms, key=len, reverse=True):
        text = text.replace(k, terms[k])
    return text


def from_simplified(text: str, locale: str) -> str:
    """Convert Simplified Chinese text into the target Chinese locale."""
    if locale == "zh-HK":
        return _apply_terms(_converter("s2hk").convert(text), _HK_TERMS)
    if locale == "zh-TW":
        return _apply_terms(_converter("s2twp").convert(text), _TW_TERMS)
    return text


def to_simplified(text: str) -> str:
    return _converter("t2s").convert(text)
