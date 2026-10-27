CREATE TABLE users (
    id                UUID PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    display_name      VARCHAR(80),
    preferred_locale  VARCHAR(10)  NOT NULL DEFAULT 'en-US',
    color_scheme      VARCHAR(20)  NOT NULL DEFAULT 'GREEN_UP',   -- GREEN_UP | RED_UP
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE portfolio_transactions (
    id           UUID PRIMARY KEY,
    user_id      UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol       VARCHAR(20)    NOT NULL REFERENCES instruments(symbol),
    side         VARCHAR(10)    NOT NULL CHECK (side IN ('BUY', 'SELL', 'DIVIDEND')),
    quantity     NUMERIC(18, 6) NOT NULL CHECK (quantity >= 0),
    price        NUMERIC(18, 6) NOT NULL CHECK (price >= 0),
    fee          NUMERIC(18, 6) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    currency     VARCHAR(10)    NOT NULL,
    executed_at  TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_portfolio_tx_user ON portfolio_transactions (user_id, executed_at);

CREATE TABLE watchlist_items (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol      VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    position    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, symbol)
);
