-- Module 13: Coupons & Zone-Level Isolation
-- Add zone_id to coupon table for zone-level coupon data isolation

ALTER TABLE coupon
    ADD COLUMN zone_id VARCHAR(100);

CREATE INDEX idx_coupon_zone_id ON coupon(zone_id);
