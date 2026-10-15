-- Single-device session enforcement: an access token carries the session
-- version it was issued with; a new login bumps this, invalidating tokens
-- issued to other devices.
ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;
