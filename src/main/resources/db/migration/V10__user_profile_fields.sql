-- Extra profile fields and preferred display currency.
ALTER TABLE users ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE users ADD COLUMN mobile   VARCHAR(40);
ALTER TABLE users ADD COLUMN gender   VARCHAR(30);
ALTER TABLE users ADD COLUMN country  VARCHAR(80);
ALTER TABLE users ADD COLUMN city     VARCHAR(120);
