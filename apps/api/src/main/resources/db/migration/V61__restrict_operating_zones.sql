-- V61: Restrict operating zones strictly to Bangalore Zone and Tumkur Zone
-- Safely deactivate unwanted/duplicate entries to preserve historical integrity.

-- 1. Ensure canonical Bangalore Zone exists and is ACTIVE
INSERT INTO location_zones (
    id, zone_name, city_name, latitude, longitude, radius_km, polygon_coordinates,
    active_drivers, surge_multiplier, status, restaurant_enabled, delivery_partner_enabled, customer_ordering_enabled
) VALUES
    ('dz-bangalore', 'Bangalore Zone', 'Bangalore', 12.9716, 77.5946, 25.0, '12.9716,77.5946 | 13.0000,77.6200 | 12.9500,77.5800', 45, 1.00, 'ACTIVE', TRUE, TRUE, TRUE)
ON CONFLICT (id) DO UPDATE SET
    zone_name = 'Bangalore Zone',
    city_name = 'Bangalore',
    status = 'ACTIVE',
    restaurant_enabled = TRUE,
    delivery_partner_enabled = TRUE,
    customer_ordering_enabled = TRUE;

-- 2. Ensure canonical Tumkur Zone exists and is ACTIVE
INSERT INTO location_zones (
    id, zone_name, city_name, latitude, longitude, radius_km, polygon_coordinates,
    active_drivers, surge_multiplier, status, restaurant_enabled, delivery_partner_enabled, customer_ordering_enabled
) VALUES
    ('dz-tumkur', 'Tumkur Zone', 'Tumkur', 13.3379, 77.1173, 20.0, '13.3379,77.1173 | 13.3500,77.1300 | 13.3200,77.1000', 22, 1.00, 'ACTIVE', TRUE, TRUE, TRUE)
ON CONFLICT (id) DO UPDATE SET
    zone_name = 'Tumkur Zone',
    city_name = 'Tumkur',
    status = 'ACTIVE',
    restaurant_enabled = TRUE,
    delivery_partner_enabled = TRUE,
    customer_ordering_enabled = TRUE;

-- 3. Safely mark any other/duplicate zone records as INACTIVE
-- This preserves historical references, foreign keys, and existing orders.
UPDATE location_zones
SET status = 'INACTIVE'
WHERE id NOT IN ('dz-bangalore', 'dz-tumkur');
