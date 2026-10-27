-- Instrument master (with logo and marquee flag)
CREATE TABLE instruments (
    symbol      VARCHAR(20) PRIMARY KEY,          -- e.g. 'AAPL', '0700.HK'
    name        VARCHAR(120) NOT NULL,            -- canonical English name
    exchange    VARCHAR(20)  NOT NULL,            -- 'NASDAQ', 'NYSE', 'HKEX'
    currency    VARCHAR(10)  NOT NULL,            -- 'USD', 'HKD'
    timezone    VARCHAR(50)  NOT NULL,            -- 'America/New_York', 'Asia/Hong_Kong'
    logo_url    VARCHAR(500),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    is_marquee  BOOLEAN NOT NULL DEFAULT FALSE
);

-- Localised instrument names (zh-HK / zh-TW / zh-CN / en-US)
CREATE TABLE instrument_names (
    symbol      VARCHAR(20) NOT NULL REFERENCES instruments(symbol) ON DELETE CASCADE,
    locale      VARCHAR(10) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    short_name  VARCHAR(60),
    PRIMARY KEY (symbol, locale)
);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_instrument_names_trgm ON instrument_names USING gin (name gin_trgm_ops);
CREATE INDEX idx_instruments_symbol_trgm ON instruments USING gin (symbol gin_trgm_ops);
