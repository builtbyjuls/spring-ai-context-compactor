CREATE TABLE clearing_members (
    id BIGSERIAL PRIMARY KEY,
    member_code VARCHAR(32) NOT NULL UNIQUE,
    legal_name VARCHAR(128) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    initial_margin_collateral NUMERIC(19, 4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE cleared_trades (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL UNIQUE,
    member_code VARCHAR(32) NOT NULL,
    instrument_symbol VARCHAR(32) NOT NULL,
    asset_class VARCHAR(32) NOT NULL,
    notional_amount NUMERIC(19, 4) NOT NULL,
    required_margin NUMERIC(19, 4) NOT NULL,
    margin_engine_used VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,
    cleared_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE margin_call_records (
    id BIGSERIAL PRIMARY KEY,
    call_reference VARCHAR(64) NOT NULL UNIQUE,
    trade_id VARCHAR(64) NOT NULL,
    member_code VARCHAR(32) NOT NULL,
    deficit_amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE clearing_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
