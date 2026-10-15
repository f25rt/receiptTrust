-- Admin role, account moderation flags, and activity tracking for metrics.
ALTER TABLE users ADD COLUMN role          VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN locked        BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN flagged       BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMPTZ;

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_last_active ON users (last_active_at);
