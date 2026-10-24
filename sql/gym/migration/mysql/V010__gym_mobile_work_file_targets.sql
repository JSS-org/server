-- Preserve each new workbench upload's declared purpose and target; historical files are unchanged.
CREATE TABLE gym_mobile_work_file (
  id bigint NOT NULL AUTO_INCREMENT,
  file_id bigint NOT NULL,
  store_id bigint NOT NULL,
  purpose varchar(32) NOT NULL,
  member_id bigint DEFAULT NULL,
  employee_id bigint DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mobile_work_file (tenant_id,file_id),
  KEY idx_mobile_work_file_member (tenant_id,member_id,store_id),
  KEY idx_mobile_work_file_employee (tenant_id,employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
