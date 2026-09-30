-- Support non-registered "label" debtors on assignments and debts.

-- item_assignments: assignee becomes optional, add free-text label
ALTER TABLE item_assignments ALTER COLUMN assignee_id DROP NOT NULL;
ALTER TABLE item_assignments ADD COLUMN assignee_label VARCHAR(255);

-- debts: debtor becomes optional, add free-text label
ALTER TABLE debts ALTER COLUMN debtor_id DROP NOT NULL;
ALTER TABLE debts ADD COLUMN debtor_label VARCHAR(255);

-- Integrity: exactly one of (user, label) must be present.
ALTER TABLE item_assignments ADD CONSTRAINT chk_assignment_target
    CHECK ((assignee_id IS NOT NULL AND assignee_label IS NULL)
        OR (assignee_id IS NULL AND assignee_label IS NOT NULL));

ALTER TABLE debts ADD CONSTRAINT chk_debt_debtor
    CHECK ((debtor_id IS NOT NULL AND debtor_label IS NULL)
        OR (debtor_id IS NULL AND debtor_label IS NOT NULL));
