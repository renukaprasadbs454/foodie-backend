-- Module 8: Delivery Shift Zone Isolation & Session Management
-- Add zone_id and zone_name to delivery_shift table for zone-level shift data isolation

ALTER TABLE delivery_shift
    ADD COLUMN IF NOT EXISTS zone_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS zone_name VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_delivery_shift_zone_id ON delivery_shift(zone_id);
