-- Invoice/receipt number captured from the printed receipt, kept as proof and
-- surfaced on each debt's explanation. Optional (manual receipts may lack one).
ALTER TABLE receipts ADD COLUMN invoice_number VARCHAR(80);
