-- Run once in phpMyAdmin against restaurant_db (if note column does not exist)
ALTER TABLE order_items ADD COLUMN note VARCHAR(200) NULL;
