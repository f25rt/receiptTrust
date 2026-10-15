-- Per-user switch for whether their country's VAT/tax applies to their receipts.
-- Defaults to TRUE: taxes apply by default (based on the user's country), and
-- the user can turn it off from their profile or per-receipt.
ALTER TABLE users ADD COLUMN tax_enabled BOOLEAN NOT NULL DEFAULT TRUE;
