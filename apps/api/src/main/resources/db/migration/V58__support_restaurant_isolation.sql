-- V58: Restaurant Support Ticket Isolation and Privacy
ALTER TABLE support_conversation ADD COLUMN IF NOT EXISTS restaurant_id UUID REFERENCES restaurant(id);
ALTER TABLE support_conversation ADD COLUMN IF NOT EXISTS restaurant_name VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_support_conversation_restaurant_id ON support_conversation(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_support_conversation_category_restaurant ON support_conversation(category, restaurant_id);

ALTER TABLE support_message ADD COLUMN IF NOT EXISTS read_status BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_support_message_conv_read ON support_message(conversation_id, read_status);
