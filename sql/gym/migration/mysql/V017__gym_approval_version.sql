-- Mobile decisions compare this version while holding the existing approval row lock.
-- Existing approvals start at version 1; their status and immutable snapshots are preserved.
ALTER TABLE gym_approval ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER current_step;
