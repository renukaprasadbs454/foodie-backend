-- V66: Delivery Partner Online Session Tracking for Shifts & Incentives

CREATE TABLE IF NOT EXISTS delivery_online_session (
    id                  UUID PRIMARY KEY,
    delivery_partner_id UUID NOT NULL REFERENCES delivery_partner(id) ON DELETE CASCADE,
    shift_id            UUID,
    online_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    offline_at          TIMESTAMPTZ,
    duration_seconds    BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_online_session_partner ON delivery_online_session(delivery_partner_id);
CREATE INDEX IF NOT EXISTS idx_online_session_online_at ON delivery_online_session(online_at);
CREATE INDEX IF NOT EXISTS idx_online_session_active ON delivery_online_session(delivery_partner_id, offline_at) WHERE offline_at IS NULL;
