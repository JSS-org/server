-- Preserve unknown historical completion times; no invented device receipts.
ALTER TABLE gym_device_command
 ADD COLUMN completed_at datetime(6) DEFAULT NULL,
 ADD CONSTRAINT ck_device_command_completion CHECK (
  completed_at IS NULL OR (
   completed_at >= requested_at AND status IN ('failed','succeeded')
   AND (status <> 'succeeded' OR simulated = false)
  )
 );
