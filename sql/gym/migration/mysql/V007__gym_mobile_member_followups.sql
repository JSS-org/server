-- Version is shared by mobile and existing admin assignment updates.
ALTER TABLE gym_member_store ADD COLUMN assignment_version int NOT NULL DEFAULT 0 COMMENT '本门店负责关系版本';

CREATE TABLE gym_member_followup (
  id bigint NOT NULL AUTO_INCREMENT,
  member_id bigint NOT NULL,
  store_id bigint NOT NULL,
  employee_id bigint NOT NULL,
  content varchar(2000) NOT NULL,
  next_contact_at datetime(6) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_member_followup (tenant_id,member_id,store_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
