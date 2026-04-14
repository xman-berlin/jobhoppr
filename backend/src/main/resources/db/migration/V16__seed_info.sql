-- V16: seed_info table for tracking seed state
CREATE TABLE IF NOT EXISTS seed_info (
    key VARCHAR(100) PRIMARY KEY,
    wert VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW()
);