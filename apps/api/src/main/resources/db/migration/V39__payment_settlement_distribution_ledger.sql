-- V39: Order Settlement, Payment Distribution & Financial Ledger
-- Module: Payment & Financial Settlement Engine

CREATE TABLE IF NOT EXISTS order_settlement (
    id                             UUID PRIMARY KEY,
    order_id                       UUID NOT NULL UNIQUE REFERENCES "order"(id) ON DELETE RESTRICT,
    payment_id                     UUID REFERENCES payment(id) ON DELETE SET NULL,
    customer_id                    UUID NOT NULL REFERENCES customer(id) ON DELETE RESTRICT,
    restaurant_id                  UUID NOT NULL REFERENCES restaurant(id) ON DELETE RESTRICT,
    delivery_partner_id            UUID REFERENCES delivery_partner(id) ON DELETE SET NULL,
    total_paid                     DECIMAL(10,2) NOT NULL,
    food_subtotal                  DECIMAL(10,2) NOT NULL,
    delivery_fee                   DECIMAL(10,2) NOT NULL,
    platform_fee                   DECIMAL(10,2) NOT NULL DEFAULT 40.00,
    tax_amount                     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    discount_amount                DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    restaurant_commission_rate     DECIMAL(5,2) NOT NULL DEFAULT 14.00,
    restaurant_commission_amount    DECIMAL(10,2) NOT NULL,
    restaurant_payout              DECIMAL(10,2) NOT NULL,
    delivery_commission_rate       DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    delivery_commission_amount     DECIMAL(10,2) NOT NULL,
    delivery_payout                DECIMAL(10,2) NOT NULL,
    admin_total_earnings           DECIMAL(10,2) NOT NULL,
    settlement_status              VARCHAR(30) NOT NULL DEFAULT 'SETTLED',
    payment_status                 VARCHAR(30) NOT NULL DEFAULT 'CAPTURED',
    distribution_status           VARCHAR(30) NOT NULL DEFAULT 'DISTRIBUTED',
    transaction_reference          VARCHAR(100),
    settled_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_settlement_status CHECK (settlement_status IN ('PENDING', 'CALCULATED', 'SETTLED', 'REFUNDED', 'FAILED')),
    CONSTRAINT chk_distribution_status CHECK (distribution_status IN ('PENDING', 'DISTRIBUTED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_order_settlement_order ON order_settlement(order_id);
CREATE INDEX IF NOT EXISTS idx_order_settlement_restaurant ON order_settlement(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_order_settlement_partner ON order_settlement(delivery_partner_id);
CREATE INDEX IF NOT EXISTS idx_order_settlement_status ON order_settlement(settlement_status);
