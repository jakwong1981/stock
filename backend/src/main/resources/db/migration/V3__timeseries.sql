-- Time-series tables. TimescaleDB hypertables are created only when the extension is
-- available, so the same migrations also run on plain PostgreSQL (e.g. in tests).
CREATE TABLE ticks (
    symbol      VARCHAR(20)    NOT NULL REFERENCES instruments(symbol),
    ts          TIMESTAMPTZ    NOT NULL,
    price       NUMERIC(18, 6) NOT NULL,
    volume      NUMERIC(20, 2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_ticks_symbol_ts ON ticks (symbol, ts DESC);

CREATE TABLE ohlcv_candles (
    symbol       VARCHAR(20)    NOT NULL REFERENCES instruments(symbol),
    interval     VARCHAR(10)    NOT NULL,         -- '1m', '5m', '1h', '1d'
    bucket_time  TIMESTAMPTZ    NOT NULL,
    open         NUMERIC(18, 6) NOT NULL,
    high         NUMERIC(18, 6) NOT NULL,
    low          NUMERIC(18, 6) NOT NULL,
    close        NUMERIC(18, 6) NOT NULL,
    volume       NUMERIC(20, 2) NOT NULL,
    PRIMARY KEY (symbol, interval, bucket_time)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb') THEN
        CREATE EXTENSION IF NOT EXISTS timescaledb;
        PERFORM create_hypertable('ticks', 'ts', if_not_exists => TRUE);
        PERFORM create_hypertable('ohlcv_candles', 'bucket_time', if_not_exists => TRUE);
    END IF;
END
$$;
