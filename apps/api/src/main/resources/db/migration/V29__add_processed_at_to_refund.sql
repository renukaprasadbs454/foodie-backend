ALTER TABLE refund_request ADD COLUMN processed_at TIMESTAMPTZ;
ALTER TABLE payment ALTER COLUMN payment_session_id DROP NOT NULL;
