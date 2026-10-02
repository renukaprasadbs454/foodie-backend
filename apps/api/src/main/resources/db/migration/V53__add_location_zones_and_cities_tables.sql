CREATE TABLE location_cities (
    id VARCHAR(255) PRIMARY KEY,
    city_name VARCHAR(255) NOT NULL,
    state VARCHAR(255),
    active_zones_count INT DEFAULT 0,
    active_merchants_count INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

CREATE TABLE location_zones (
    id VARCHAR(255) PRIMARY KEY,
    zone_name VARCHAR(255) NOT NULL,
    city_name VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    radius_km DOUBLE PRECISION,
    polygon_coordinates TEXT,
    active_drivers INT DEFAULT 0,
    surge_multiplier NUMERIC(10, 2) DEFAULT 1.00,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    restaurant_enabled BOOLEAN DEFAULT TRUE,
    delivery_partner_enabled BOOLEAN DEFAULT TRUE,
    customer_ordering_enabled BOOLEAN DEFAULT TRUE
);
