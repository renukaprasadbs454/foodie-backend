-- Module 1 / Auth: Add phone_verified flag and support RESTAURANT_ADMIN user_type in user_credential

ALTER TABLE user_credential
    ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_credential
    DROP CONSTRAINT IF EXISTS chk_user_credential_user_type;

ALTER TABLE user_credential
    ADD CONSTRAINT chk_user_credential_user_type
    CHECK (user_type IN ('CUSTOMER', 'RESTAURANT', 'RESTAURANT_ADMIN', 'DELIVERY_PARTNER', 'ADMIN'));
