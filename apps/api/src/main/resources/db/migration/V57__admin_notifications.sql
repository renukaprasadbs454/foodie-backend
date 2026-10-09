-- Module 10: Admin Broadcast & Scheduled Notifications support
ALTER TABLE notification_log ALTER COLUMN template_id DROP NOT NULL;

ALTER TABLE notification_log ADD COLUMN IF NOT EXISTS action_url VARCHAR(500);
ALTER TABLE notification_log ADD COLUMN IF NOT EXISTS target_audience VARCHAR(50);
ALTER TABLE notification_log ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS notification_broadcast (
    id                  UUID PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    body                VARCHAR(500) NOT NULL,
    target_audience     VARCHAR(50) NOT NULL,
    action_url          VARCHAR(500),
    scheduled_at        TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    recipients_count    INT NOT NULL DEFAULT 0,
    delivery_status     VARCHAR(20) NOT NULL DEFAULT 'SENT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_broadcast_audience ON notification_broadcast(target_audience);
