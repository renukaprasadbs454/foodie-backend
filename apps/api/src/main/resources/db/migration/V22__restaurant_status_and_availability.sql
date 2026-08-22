-- Restaurant operational and availability status

ALTER TABLE restaurant
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_online BOOLEAN NOT NULL DEFAULT FALSE;