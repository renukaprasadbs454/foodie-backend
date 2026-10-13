-- V60: Admin Zone-Based Login History and Canonical Operating Zones

CREATE TABLE IF NOT EXISTS admin_zone_login_history (
    id UUID PRIMARY KEY,
    admin_user_id UUID,
    admin_name VARCHAR(255) NOT NULL,
    admin_email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    zone_name VARCHAR(200) NOT NULL,
    login_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    ip_address VARCHAR(100),
    user_agent VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_admin_zone_login_admin ON admin_zone_login_history(admin_email);
CREATE INDEX IF NOT EXISTS idx_admin_zone_login_zone ON admin_zone_login_history(zone_id);
CREATE INDEX IF NOT EXISTS idx_admin_zone_login_time ON admin_zone_login_history(login_time DESC);

-- Ensure canonical operating zones exist and are ACTIVE
INSERT INTO location_zones (
    id, zone_name, city_name, latitude, longitude, radius_km, polygon_coordinates,
    active_drivers, surge_multiplier, status, restaurant_enabled, delivery_partner_enabled, customer_ordering_enabled
) VALUES
    ('dz-bangalore', 'Bangalore Zone', 'Bangalore', 12.9716, 77.5946, 25.0, '12.9716,77.5946 | 13.0000,77.6200 | 12.9500,77.5800', 45, 1.00, 'ACTIVE', TRUE, TRUE, TRUE),
    ('dz-tumkur', 'Tumkur Zone', 'Tumkur', 13.3379, 77.1173, 20.0, '13.3379,77.1173 | 13.3500,77.1300 | 13.3200,77.1000', 22, 1.00, 'ACTIVE', TRUE, TRUE, TRUE)
ON CONFLICT (id) DO UPDATE SET
    zone_name = EXCLUDED.zone_name,
    city_name = EXCLUDED.city_name,
    status = 'ACTIVE',
    restaurant_enabled = TRUE,
    delivery_partner_enabled = TRUE,
    customer_ordering_enabled = TRUE;
