-- Refresh tokens are now stored as SHA-256 hashes instead of plaintext.
-- Existing rows hold raw tokens that can't be converted to the new format
-- without the token itself (and a leaked plaintext token is exactly what this
-- change guards against), so they are discarded. Users simply sign in again.
DELETE FROM refresh_tokens;
ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;
