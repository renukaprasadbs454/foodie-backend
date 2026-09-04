ALTER TABLE payment RENAME COLUMN razorpay_order_id TO payment_session_id;
ALTER TABLE payment RENAME COLUMN razorpay_payment_id TO cashfree_order_id;
ALTER TABLE refund_request RENAME COLUMN razorpay_refund_id TO cashfree_refund_id;
