ALTER TABLE restaurant
ADD COLUMN open_time TIME,
ADD COLUMN close_time TIME,
ADD COLUMN open_days VARCHAR(20)[];
