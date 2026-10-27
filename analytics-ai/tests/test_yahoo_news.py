"""Yahoo Finance RSS news provider, using a recorded feed and a mocked HTTP transport."""

from datetime import UTC, datetime

import httpx

from app.news.providers import YahooNewsProvider

NOW = datetime(2026, 10, 28, 0, 0, tzinfo=UTC)


def feed(*items: tuple[str, str, str]) -> str:
    body = "".join(
        f"<item><title>{t}</title><link>{link}</link><pubDate>{d}</pubDate>"
        f"<description>About {t}</description><guid>g</guid></item>"
        for t, link, d in items)
    return f'<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>{body}</channel></rss>'


HSBC = feed(
    ("HSBC targets savings", "https://finance.yahoo.com/a/hsbc-1", "Tue, 27 Oct 2026 09:00:00 +0000"),
    ("Old HSBC story", "https://finance.yahoo.com/a/hsbc-old", "Mon, 19 Oct 2026 09:00:00 +0000"),
    ("Insecure link", "http://example.com/x", "Tue, 27 Oct 2026 10:00:00 +0000"),
    ("Banks rally in Asia", "https://www.reuters.com/a/banks", "Tue, 27 Oct 2026 11:00:00 +0800"),
)


def test_parse_filters_old_and_insecure_items():
    items = YahooNewsProvider(max_age_days=3).parse(HSBC, "0005.HK", now=NOW)
    assert [n.title for n in items] == ["HSBC targets savings", "Banks rally in Asia"]  # newest first
    assert items[0].symbols == ["0005.HK"]
    assert items[0].source == "Yahoo Finance"
    assert items[1].source == "reuters.com"
    assert items[1].published_at == datetime(2026, 10, 27, 3, 0, tzinfo=UTC)  # +0800 → UTC
    assert items[0].body == "About HSBC targets savings"


def test_parse_caps_items_per_symbol():
    many = feed(*[(f"t{i}", f"https://finance.yahoo.com/a/{i}", "Tue, 27 Oct 2026 09:00:00 +0000")
                  for i in range(10)])
    assert len(YahooNewsProvider(max_per_symbol=5).parse(many, "AAPL", now=NOW)) == 5


def test_rejects_xml_entity_expansion():
    bomb = ('<?xml version="1.0"?><!DOCTYPE r [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;">]>'
            '<rss><channel><item><title>&b;</title></item></channel></rss>')
    try:
        YahooNewsProvider().parse(bomb, "AAPL", now=NOW)
        raise AssertionError("expected defusedxml to reject entities")
    except Exception as e:  # defusedxml raises EntitiesForbidden
        assert "Entit" in type(e).__name__


async def test_fetch_merges_symbols_for_shared_articles(monkeypatch):
    shared = ("Tech giants rally", "https://finance.yahoo.com/a/shared", "Tue, 27 Oct 2026 20:00:00 +0000")
    feeds = {
        "AAPL": feed(shared),
        "NVDA": feed(shared, ("Nvidia deal", "https://finance.yahoo.com/a/nv", "Tue, 27 Oct 2026 21:00:00 +0000")),
        "0005.HK": "not xml",  # a broken feed must not stop the others
    }

    def handler(req: httpx.Request) -> httpx.Response:
        assert req.headers["user-agent"].startswith("Mozilla/5.0")
        return httpx.Response(200, text=feeds[req.url.params["s"]])

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    p = YahooNewsProvider(symbols=["AAPL", "NVDA", "0005.HK"], max_age_days=36500, client=client)
    items = await p.fetch()
    by_title = {n.title: n for n in items}
    assert set(by_title) == {"Tech giants rally", "Nvidia deal"}
    assert by_title["Tech giants rally"].symbols == ["AAPL", "NVDA"]
