-- V68: Add zone_id and zone_name to restaurant table for strict zone-level order assignment
ALTER TABLE restaurant
  ADD COLUMN IF NOT EXISTS zone_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS zone_name VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_restaurant_zone_id ON restaurant(zone_id);

-- Backfill existing restaurants based on address city or coordinates
UPDATE restaurant r
SET zone_id = CASE
    WHEN EXISTS (
        SELECT 1 FROM restaurant_address a
        WHERE a.id = r.address_id
        AND (LOWER(a.city) LIKE '%tumkur%' OR LOWER(a.city) LIKE '%tumakuru%' OR LOWER(a.formatted_address) LIKE '%tumkur%')
    ) OR (r.latitude > 13.2 AND r.latitude < 13.5 AND r.longitude > 77.0 AND r.longitude < 77.3)
    THEN 'dz-tumkur'
    ELSE 'dz-bangalore'
END,
zone_name = CASE
    WHEN EXISTS (
        SELECT 1 FROM restaurant_address a
        WHERE a.id = r.address_id
        AND (LOWER(a.city) LIKE '%tumkur%' OR LOWER(a.city) LIKE '%tumakuru%' OR LOWER(a.formatted_address) LIKE '%tumkur%')
    ) OR (r.latitude > 13.2 AND r.latitude < 13.5 AND r.longitude > 77.0 AND r.longitude < 77.3)
    THEN 'Tumkur Zone'
    ELSE 'Bangalore Zone'
END
WHERE r.zone_id IS NULL;
