-- V59: Admin-Configured Delivery Zone Verification for Delivery Partners
ALTER TABLE delivery_partner
  ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS zone_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS zone_name VARCHAR(200),
  ADD COLUMN IF NOT EXISTS zone_verified BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_delivery_partner_zone_verified ON delivery_partner(zone_verified);
CREATE INDEX IF NOT EXISTS idx_delivery_partner_zone_id ON delivery_partner(zone_id);
