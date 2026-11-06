-- V74: Create user_login_devices table to capture admin and staff device logins
CREATE TABLE IF NOT EXISTS user_login_devices (
    id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    user_role VARCHAR(50),
    device_name VARCHAR(150),
    browser VARCHAR(100),
    os VARCHAR(100),
    ip_address VARCHAR(100),
    location VARCHAR(150),
    login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_current_active BOOLEAN NOT NULL DEFAULT TRUE,
    logout_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_user_login_devices_user_id ON user_login_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_user_login_devices_active ON user_login_devices(is_current_active);
CREATE INDEX IF NOT EXISTS idx_user_login_devices_last_active ON user_login_devices(last_active_at);

