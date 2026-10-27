-- Financial news (written by analytics-ai). Many-to-many with instruments.
CREATE TABLE financial_news (
    id               UUID PRIMARY KEY,
    title            VARCHAR(500)  NOT NULL,
    summary          TEXT,
    source           VARCHAR(100)  NOT NULL,
    url              VARCHAR(1000) NOT NULL UNIQUE,
    language         VARCHAR(10),
    sentiment_score  NUMERIC(3, 2) CHECK (sentiment_score BETWEEN -1 AND 1),
    sentiment_label  VARCHAR(10),                  -- BULLISH | BEARISH | NEUTRAL | UNANALYZED
    is_breaking      BOOLEAN NOT NULL DEFAULT FALSE,
    published_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_news_published ON financial_news (published_at DESC);

CREATE TABLE news_symbols (
    news_id  UUID        NOT NULL REFERENCES financial_news(id) ON DELETE CASCADE,
    symbol   VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    PRIMARY KEY (news_id, symbol)
);

-- AI summaries per locale; generated once per (news, locale)
CREATE TABLE news_summaries (
    news_id  UUID        NOT NULL REFERENCES financial_news(id) ON DELETE CASCADE,
    locale   VARCHAR(10) NOT NULL,
    summary  TEXT        NOT NULL,
    PRIMARY KEY (news_id, locale)
);
