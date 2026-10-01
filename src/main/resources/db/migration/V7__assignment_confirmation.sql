-- Assignment confirmation: a registered user who is not yet a friend of the
-- receipt owner must confirm an assignment before the receipt can be finalized.
-- Existing rows default to confirmed (friends/labels were implicitly accepted).
ALTER TABLE item_assignments
    ADD COLUMN confirmed BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_item_assignments_assignee ON item_assignments (assignee_id);
