-- Module 8: Delivery Shift & Booking Management
-- Permanent database tables for delivery fleet shifts and partner bookings

CREATE TABLE IF NOT EXISTS delivery_shift (
    id              UUID PRIMARY KEY,
    shift_name      VARCHAR(100) NOT NULL,
    start_time      VARCHAR(50) NOT NULL,
    end_time        VARCHAR(50) NOT NULL,
    duration_hours  VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delivery_shift_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DEACTIVATED'))
);

CREATE TABLE IF NOT EXISTS delivery_shift_booking (
    id                  UUID PRIMARY KEY,
    delivery_partner_id UUID NOT NULL,
    shift_id            UUID NOT NULL REFERENCES delivery_shift(id) ON DELETE CASCADE,
    shift_name          VARCHAR(100) NOT NULL,
    start_time          VARCHAR(50) NOT NULL,
    end_time            VARCHAR(50) NOT NULL,
    duration_hours      VARCHAR(20),
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    booked_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delivery_shift_booking_status CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_shift_status ON delivery_shift(status);
CREATE INDEX IF NOT EXISTS idx_delivery_shift_created_at ON delivery_shift(created_at);
CREATE INDEX IF NOT EXISTS idx_shift_booking_partner ON delivery_shift_booking(delivery_partner_id);
CREATE INDEX IF NOT EXISTS idx_shift_booking_shift ON delivery_shift_booking(shift_id);
