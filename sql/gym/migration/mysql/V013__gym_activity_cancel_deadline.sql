-- NULL preserves legacy member cancellation up to the activity start.
ALTER TABLE gym_activity ADD COLUMN cancel_deadline datetime(6) DEFAULT NULL AFTER ends_at;
