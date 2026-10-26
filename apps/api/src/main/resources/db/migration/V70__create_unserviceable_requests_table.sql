-- V70: Create table for unserviceable requests and add location_approval_status to delivery_partner
CREATE TABLE IF NOT EXISTS unserviceable_request (
    id VARCHAR(100) PRIMARY KEY,
    delivery_partner_id UUID,
    restaurant_name VARCHAR(255),
    contact_person VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    address TEXT,
    city_name VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    request_source VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER',
    requester_name VARCHAR(255),
    requested_area TEXT,
    zone_id VARCHAR(100),
    zone_name VARCHAR(200),
    submitted_documents TEXT
);

CREATE INDEX IF NOT EXISTS idx_unserviceable_req_status ON unserviceable_request(status);
CREATE INDEX IF NOT EXISTS idx_unserviceable_req_source ON unserviceable_request(request_source);
CREATE INDEX IF NOT EXISTS idx_unserviceable_req_partner_id ON unserviceable_request(delivery_partner_id);
CREATE INDEX IF NOT EXISTS idx_unserviceable_req_created_at ON unserviceable_request(created_at);

ALTER TABLE delivery_partner
    ADD COLUMN IF NOT EXISTS location_approval_status VARCHAR(50) DEFAULT 'NONE';

CREATE INDEX IF NOT EXISTS idx_delivery_partner_loc_status ON delivery_partner(location_approval_status);
