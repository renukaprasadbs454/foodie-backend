ALTER TABLE payment ALTER COLUMN payment_session_id TYPE VARCHAR(255);
ALTER TABLE payment ALTER COLUMN cashfree_order_id TYPE VARCHAR(255);
ALTER TABLE refund_request ALTER COLUMN cashfree_refund_id TYPE VARCHAR(255);
