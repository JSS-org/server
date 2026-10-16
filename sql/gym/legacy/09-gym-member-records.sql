-- 全店黑名单与体测；实际权限在服务层检查，tenant_id 使用框架隔离。
CREATE TABLE IF NOT EXISTS gym_blacklist (
  id bigint NOT NULL AUTO_INCREMENT,
  member_id bigint NOT NULL,
  block_entry boolean NOT NULL,
  block_purchase boolean NOT NULL,
  reason varchar(2000) DEFAULT NULL,
  starts_at datetime(6) NOT NULL,
  ends_at datetime(6) DEFAULT NULL,
  revoked_at datetime(6) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_blacklist_member (tenant_id,member_id)
);
CREATE TABLE IF NOT EXISTS gym_measurement (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL,
  member_id bigint NOT NULL,
  measured_at datetime(6) NOT NULL,
  height_cm decimal(5,2) DEFAULT NULL,
  weight_kg decimal(5,2) DEFAULT NULL,
  body_fat_percent decimal(5,2) DEFAULT NULL,
  muscle_mass_kg decimal(5,2) DEFAULT NULL,
  note varchar(2000) DEFAULT NULL,
  report_file_id bigint DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_measurement_member (tenant_id,member_id),
  KEY idx_measurement_store_time (tenant_id,store_id,measured_at)
);
