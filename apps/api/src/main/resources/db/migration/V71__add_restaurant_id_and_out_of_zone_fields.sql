-- V71: Add restaurant_id and distance_from_zone_km to unserviceable_request, and out_of_zone fields to restaurant
ALTER TABLE unserviceable_request
    ADD COLUMN IF NOT EXISTS restaurant_id UUID,
    ADD COLUMN IF NOT EXISTS distance_from_zone_km DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_unserviceable_req_restaurant_id ON unserviceable_request(restaurant_id);

ALTER TABLE restaurant
    ADD COLUMN IF NOT EXISTS out_of_zone BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS nearest_zone_distance_km DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_restaurant_out_of_zone ON restaurant(out_of_zone);
