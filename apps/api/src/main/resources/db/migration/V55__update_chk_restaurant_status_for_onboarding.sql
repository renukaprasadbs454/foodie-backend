ALTER TABLE restaurant DROP CONSTRAINT IF EXISTS chk_restaurant_status;
ALTER TABLE restaurant ADD CONSTRAINT chk_restaurant_status CHECK (status IN ('ONBOARDING', 'PENDING', 'APPROVED', 'SUSPENDED', 'REJECTED'));
