from decimal import Decimal

import httpx
import openai

from app import i18n
from app.analysis import BEARISH, BULLISH, NEUTRAL, UNANALYZED, NewsAnalyzer, label_for, parse_score
from tests.conftest import FakeProvider, fallback


def test_parse_score_is_decimal_and_clamped():
    assert parse_score(0.123) == Decimal("0.12")
    assert parse_score("0.125") == Decimal("0.12")  # banker's rounding
    assert parse_score(3) == Decimal("1.00")
    assert parse_score(-7.5) == Decimal("-1.00")
    assert parse_score("nan") is None
    assert parse_score(None) is None
    assert parse_score("abc") is None


def test_labels():
    assert label_for(Decimal("0.5")) == BULLISH
    assert label_for(Decimal("-0.25")) == BEARISH
    assert label_for(Decimal("0.1")) == NEUTRAL
    assert label_for(None) == UNANALYZED


async def test_analyzer_produces_four_locales_from_one_call():
    p = FakeProvider(json_replies=[{"score": 0.8, "summary_en": "Apple beat estimates.",
                                    "summary_zh": "苹果业绩超出预期，软件收入增长。"}])
    a = await NewsAnalyzer(fallback(p)).analyze("Apple beats", "body", ["AAPL"])
    assert len(p.calls) == 1
    assert a.score == Decimal("0.80") and a.label == BULLISH
    assert a.summaries["en-US"] == "Apple beat estimates."
    assert a.summaries["zh-CN"] == "苹果业绩超出预期，软件收入增长。"
    assert a.summaries["zh-HK"].startswith("蘋果")
    assert "軟體" in a.summaries["zh-TW"]  # Taiwan phrasing via s2twp


async def test_analyzer_falls_back_to_secondary_provider():
    req = httpx.Request("POST", "https://api.deepseek.com")
    primary = FakeProvider("deepseek", fail=openai.APITimeoutError(request=req))
    secondary = FakeProvider("fallback", json_replies=[{"score": -0.4, "summary_en": "x", "summary_zh": "y"}])
    a = await NewsAnalyzer(fallback(primary, secondary)).analyze("t", "b", [])
    assert a.label == BEARISH
    assert len(primary.calls) == 1 and len(secondary.calls) == 1


async def test_analyzer_degrades_without_llm():
    a = await NewsAnalyzer(fallback()).analyze("Title", "Body text", [])
    assert a.label == UNANALYZED and a.score is None
    assert set(a.summaries) == set(i18n.SUPPORTED)


def test_regional_terms():
    assert i18n.from_simplified("首次公开募股", "zh-HK") == "首次公開招股"
    assert i18n.from_simplified("首次公开募股", "zh-TW") == "首次公開發行"
    assert i18n.from_simplified("衍生权证", "zh-HK") == "窩輪"
    assert i18n.to_simplified("騰訊控股") == "腾讯控股"


def test_locale_normalization():
    assert i18n.normalize_locale("zh-HK,zh;q=0.9") == "zh-HK"
    assert i18n.normalize_locale("zh-Hant-TW") == "zh-TW"
    assert i18n.normalize_locale("zh-Hant") == "zh-HK"
    assert i18n.normalize_locale("zh-Hans-SG") == "zh-CN"
    assert i18n.normalize_locale("fr-FR,en;q=0.5") == "en-US"
    assert i18n.normalize_locale(None) == "en-US"
