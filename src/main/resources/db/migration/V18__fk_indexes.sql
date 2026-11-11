-- Indexes for foreign keys flagged by the Supabase performance advisor.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_friendships_addressee ON friendships (addressee_id);
CREATE INDEX IF NOT EXISTS idx_debt_items_debt ON debt_items (debt_id);
CREATE INDEX IF NOT EXISTS idx_debt_comments_author ON debt_comments (author_id);
CREATE INDEX IF NOT EXISTS idx_payments_submitter ON payments (submitter_id);
