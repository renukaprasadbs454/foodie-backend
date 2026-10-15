-- V62: Fix restaurant foreign keys to allow permanent deletion of suspended restaurants

-- 1. support_conversation: decouple on delete so tickets and chat history are preserved
ALTER TABLE support_conversation DROP CONSTRAINT IF EXISTS support_conversation_restaurant_id_fkey;
ALTER TABLE support_conversation ADD CONSTRAINT support_conversation_restaurant_id_fkey
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE SET NULL;

-- 2. cart: clear restaurant on delete
ALTER TABLE cart DROP CONSTRAINT IF EXISTS cart_restaurant_id_fkey;
ALTER TABLE cart ADD CONSTRAINT cart_restaurant_id_fkey
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE SET NULL;

-- 3. review: cascade delete with restaurant
ALTER TABLE review DROP CONSTRAINT IF EXISTS review_restaurant_id_fkey;
ALTER TABLE review ADD CONSTRAINT review_restaurant_id_fkey
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE CASCADE;

-- 4. coupon: cascade delete with restaurant
ALTER TABLE coupon DROP CONSTRAINT IF EXISTS coupon_restaurant_id_fkey;
ALTER TABLE coupon ADD CONSTRAINT coupon_restaurant_id_fkey
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE CASCADE;

-- 5. restaurant_settlement: cascade delete with restaurant
ALTER TABLE restaurant_settlement DROP CONSTRAINT IF EXISTS restaurant_settlement_restaurant_id_fkey;
ALTER TABLE restaurant_settlement ADD CONSTRAINT restaurant_settlement_restaurant_id_fkey
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE CASCADE;
