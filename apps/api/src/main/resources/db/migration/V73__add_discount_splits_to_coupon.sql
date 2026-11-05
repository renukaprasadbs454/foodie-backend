-- V73: Add admin_discount and restaurant_discount columns to coupon table
ALTER TABLE coupon ADD COLUMN IF NOT EXISTS admin_discount DECIMAL(10, 2);
ALTER TABLE coupon ADD COLUMN IF NOT EXISTS restaurant_discount DECIMAL(10, 2);
