-- V72: Allow multiple restaurants per owner credential
ALTER TABLE restaurant DROP CONSTRAINT IF EXISTS restaurant_owner_user_credential_id_key;
DROP INDEX IF EXISTS restaurant_owner_user_credential_id_key;
CREATE INDEX IF NOT EXISTS idx_restaurant_owner_user_credential_id ON restaurant(owner_user_credential_id);
