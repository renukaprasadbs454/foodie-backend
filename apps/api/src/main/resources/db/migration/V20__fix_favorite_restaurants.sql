-- V18: Fix favorite_restaurants table — add missing updated_at column
ALTER TABLE favorite_restaurants ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
