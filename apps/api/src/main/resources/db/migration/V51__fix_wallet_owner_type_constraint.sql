-- Fix wallet_account check constraints to allow RESTAURANT and CUSTOMER wallets
ALTER TABLE wallet_account DROP CONSTRAINT IF EXISTS chk_wallet_owner_type;
ALTER TABLE wallet_account DROP CONSTRAINT IF EXISTS chk_wallet_account_owner_type;
ALTER TABLE wallet_account ADD CONSTRAINT chk_wallet_account_owner_type 
    CHECK (owner_type IN ('DELIVERY_PARTNER', 'PLATFORM', 'CUSTOMER', 'RESTAURANT'));
