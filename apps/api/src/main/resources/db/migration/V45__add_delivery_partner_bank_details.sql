-- V45: Delivery Partner Bank Details table and verification status

CREATE TABLE IF NOT EXISTS delivery_partner_bank_details (
    id                    UUID PRIMARY KEY,
    delivery_partner_id   UUID NOT NULL UNIQUE REFERENCES delivery_partner(id) ON DELETE CASCADE,
    account_holder_name   VARCHAR(255) NOT NULL,
    account_number        VARCHAR(50) NOT NULL,
    ifsc_code             VARCHAR(20) NOT NULL,
    bank_name             VARCHAR(150) NOT NULL,
    branch_name           VARCHAR(150),
    account_type          VARCHAR(30) NOT NULL DEFAULT 'SAVINGS',
    verification_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_by           UUID,
    verified_at           TIMESTAMPTZ,
    rejection_reason      VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_dp_bank_verification_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_partner_bank_details_partner ON delivery_partner_bank_details(delivery_partner_id);
