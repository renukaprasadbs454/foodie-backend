-- Module 7/Financial Foundation Migration: V21__financial_and_payment_foundation.sql
-- Tax Engine, Tax Snapshots, Legal Entities, Customer Tax Profiles, Payment Enhancements

CREATE TABLE IF NOT EXISTS tax_rules (
    id               UUID PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    component_type   VARCHAR(50) NOT NULL,
    tax_category     VARCHAR(50) NOT NULL,
    tax_type         VARCHAR(20) NOT NULL,
    cgst_rate        DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    sgst_rate        DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    igst_rate        DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    cess_rate        DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    effective_from   DATE NOT NULL,
    effective_to     DATE,
    seller_scope     VARCHAR(100),
    location_scope   VARCHAR(100),
    conditions       TEXT,
    priority         INT NOT NULL DEFAULT 0,
    version          INT NOT NULL DEFAULT 1,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tax_rules_component CHECK (component_type IN (
        'FOOD', 'DELIVERY', 'PACKAGING', 'PLATFORM_FEE', 'CONVENIENCE_FEE', 'CANCELLATION_FEE', 'OTHER_FEE'
    )),
    CONSTRAINT chk_tax_rules_tax_type CHECK (tax_type IN ('CGST_SGST', 'IGST', 'CESS'))
);

CREATE TABLE IF NOT EXISTS legal_entities (
    id                 UUID PRIMARY KEY,
    legal_name         VARCHAR(150) NOT NULL,
    trade_name         VARCHAR(150),
    pan                VARCHAR(10),
    gstin              VARCHAR(15),
    registered_address TEXT,
    state_code         VARCHAR(50) NOT NULL,
    entity_type        VARCHAR(50) NOT NULL,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS customer_tax_profiles (
    id                UUID PRIMARY KEY,
    customer_id       UUID NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    customer_type     VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    legal_name        VARCHAR(150),
    gstin             VARCHAR(15),
    billing_address   TEXT,
    state_code        VARCHAR(50),
    country_code      VARCHAR(10) DEFAULT 'IN',
    gstin_verified_at TIMESTAMPTZ,
    is_default        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tax_snapshots (
    id                       UUID PRIMARY KEY,
    order_id                 UUID NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    invoice_id               UUID,
    seller_entity_id         UUID REFERENCES legal_entities(id),
    customer_tax_profile_id UUID REFERENCES customer_tax_profiles(id),
    supply_context           TEXT,
    currency                 VARCHAR(3) NOT NULL DEFAULT 'INR',
    total_taxable_paise      BIGINT NOT NULL,
    total_cgst_paise         BIGINT NOT NULL,
    total_sgst_paise         BIGINT NOT NULL,
    total_igst_paise         BIGINT NOT NULL,
    total_cess_paise         BIGINT NOT NULL,
    rounding_adjustment_paise BIGINT NOT NULL DEFAULT 0,
    tax_rule_set_version     INT NOT NULL DEFAULT 1,
    calculated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tax_snapshot_items (
    id                 UUID PRIMARY KEY,
    tax_snapshot_id    UUID NOT NULL REFERENCES tax_snapshots(id) ON DELETE CASCADE,
    order_item_id      UUID,
    component_type     VARCHAR(50) NOT NULL,
    description        VARCHAR(255) NOT NULL,
    gross_paise        BIGINT NOT NULL,
    discount_paise     BIGINT NOT NULL DEFAULT 0,
    taxable_paise      BIGINT NOT NULL,
    tax_rule_id        UUID REFERENCES tax_rules(id),
    tax_rule_version   INT NOT NULL,
    cgst_paise         BIGINT NOT NULL DEFAULT 0,
    sgst_paise         BIGINT NOT NULL DEFAULT 0,
    igst_paise         BIGINT NOT NULL DEFAULT 0,
    cess_paise         BIGINT NOT NULL DEFAULT 0,
    total_paise        BIGINT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS gateway VARCHAR(50) NOT NULL DEFAULT 'RAZORPAY',
    ADD COLUMN IF NOT EXISTS amount_paise BIGINT,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS gateway_fee_paise BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS amount_refunded_paise BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS metadata TEXT;

-- Update existing payment rows amount_paise from amount column if null
UPDATE payment SET amount_paise = CAST(ROUND(amount * 100) AS BIGINT) WHERE amount_paise IS NULL;

-- Constraint for gateway + razorpay_order_id uniqueness
ALTER TABLE payment
    ADD CONSTRAINT uq_payment_gateway_order UNIQUE (gateway, razorpay_order_id);

CREATE INDEX IF NOT EXISTS idx_tax_rules_active ON tax_rules(component_type, is_active, priority);
CREATE INDEX IF NOT EXISTS idx_tax_snapshots_order ON tax_snapshots(order_id);
CREATE INDEX IF NOT EXISTS idx_tax_snapshot_items_snapshot ON tax_snapshot_items(tax_snapshot_id);

-- Seed initial test/dev tax rules
INSERT INTO tax_rules (id, name, component_type, tax_category, tax_type, cgst_rate, sgst_rate, igst_rate, cess_rate, effective_from, priority, version, is_active, created_at, updated_at)
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'Dev Food GST Intra-State', 'FOOD', 'RESTAURANT_FOOD', 'CGST_SGST', 0.0250, 0.0250, 0.0000, 0.0000, '2026-01-01', 1, 1, TRUE, NOW(), NOW()),
  ('22222222-2222-2222-2222-222222222222', 'Dev Delivery GST Intra-State', 'DELIVERY', 'SERVICE_DELIVERY', 'CGST_SGST', 0.0900, 0.0900, 0.0000, 0.0000, '2026-01-01', 1, 1, TRUE, NOW(), NOW()),
  ('33333333-3333-3333-3333-333333333333', 'Dev Platform Fee GST Intra-State', 'PLATFORM_FEE', 'SERVICE_PLATFORM', 'CGST_SGST', 0.0900, 0.0900, 0.0000, 0.0000, '2026-01-01', 1, 1, TRUE, NOW(), NOW()),
  ('44444444-4444-4444-4444-444444444444', 'Dev Packaging GST Intra-State', 'PACKAGING', 'PACKAGING_FEE', 'CGST_SGST', 0.0900, 0.0900, 0.0000, 0.0000, '2026-01-01', 1, 1, TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
