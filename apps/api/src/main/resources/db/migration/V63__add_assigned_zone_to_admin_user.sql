-- Module 13: Admin (Zone-Based User Access)
-- Add assigned_zone_id to admin_user table for zone-level data isolation

ALTER TABLE admin_user
    ADD COLUMN assigned_zone_id VARCHAR(100);

CREATE INDEX idx_admin_user_assigned_zone ON admin_user(assigned_zone_id);
