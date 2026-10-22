- V68: Add zone_id to support_conversation and support_message for dynamic zone isolation
ALTER TABLE support_conversation ADD COLUMN IF NOT EXISTS zone_id VARCHAR(100);
ALTER TABLE support_message ADD COLUMN IF NOT EXISTS zone_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_support_conversation_zone_id ON support_conversation(zone_id);
CREATE INDEX IF NOT EXISTS idx_support_message_zone_id ON support_message(zone_id);
