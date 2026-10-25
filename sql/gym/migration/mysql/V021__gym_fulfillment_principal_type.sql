-- All tasks created before V021 were queued by an authenticated administrator.
-- Preserve their principal IDs, leases and retry history; do not create member tasks here.
ALTER TABLE gym_fulfillment_task
 ADD COLUMN principal_user_type int NOT NULL DEFAULT 2 COMMENT 'UserTypeEnum: 1 member, 2 administrator';
