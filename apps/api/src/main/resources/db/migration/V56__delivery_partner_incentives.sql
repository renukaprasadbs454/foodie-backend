-- Module 9 / Delivery Partner Incentives
ALTER TABLE delivery_pricing_config
    ADD COLUMN IF NOT EXISTS pricing_basis VARCHAR(20) NOT NULL DEFAULT 'UNIVERSAL',
    ADD COLUMN IF NOT EXISTS config_data TEXT;

CREATE TABLE IF NOT EXISTS delivery_partner_incentive_earning (
    id                      UUID PRIMARY KEY,
    delivery_partner_id     UUID NOT NULL REFERENCES delivery_partner(id) ON DELETE CASCADE,
    rule_id                 VARCHAR(100) NOT NULL,
    rule_title              VARCHAR(200) NOT NULL,
    amount                  DECIMAL(10,2) NOT NULL,
    reference_type          VARCHAR(50) NOT NULL,
    reference_id            UUID NOT NULL,
    order_id                UUID,
    period_date             DATE NOT NULL,
    earned_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_partner_rule_ref UNIQUE (delivery_partner_id, rule_id, reference_type, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_partner_incentive_partner_date ON delivery_partner_incentive_earning (delivery_partner_id, period_date);
CREATE INDEX IF NOT EXISTS idx_partner_incentive_partner ON delivery_partner_incentive_earning (delivery_partner_id);
CREATE INDEX IF NOT EXISTS idx_partner_incentive_order ON delivery_partner_incentive_earning (order_id);
