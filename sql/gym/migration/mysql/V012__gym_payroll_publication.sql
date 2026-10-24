-- Confirmation freezes calculation; explicit publication controls employee visibility.
-- Existing confirmed payrolls remain unpublished until an authorized administrator publishes.
ALTER TABLE gym_payroll
 ADD COLUMN published_at datetime(6) DEFAULT NULL AFTER confirmed_by,
 ADD COLUMN published_by bigint DEFAULT NULL AFTER published_at;
CREATE INDEX idx_payroll_item_employee ON gym_payroll_item (tenant_id,employee_id,payroll_id,deleted);
