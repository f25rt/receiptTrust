-- Receipt images are now optional: a receipt can be created with fully manual
-- details and no uploaded proof image.
ALTER TABLE receipts ALTER COLUMN image_path DROP NOT NULL;
ALTER TABLE receipts ALTER COLUMN image_content_type DROP NOT NULL;
