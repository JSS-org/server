-- 首批门店 / 员工：仅建新表，不清库，不自动创建业务档案。
-- 门店 ID = system_dept.id；所有业务表使用 tenant_id / deleted。
-- 自然键跨逻辑删除保留，当前不提供删除接口。任职/媒体整体替换时物理删除关系行。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS gym_store (
  id bigint NOT NULL COMMENT '等于 system_dept.id，不自动生成',
  code varchar(64) NOT NULL,
  name varchar(30) NOT NULL,
  description varchar(2000) NOT NULL DEFAULT '',
  area_sqm decimal(12,2) NOT NULL,
  features text NOT NULL COMMENT 'JSON 字符串数组',
  address varchar(500) NOT NULL,
  longitude decimal(10,7) DEFAULT NULL,
  latitude decimal(9,7) DEFAULT NULL,
  service_phone varchar(32) DEFAULT NULL,
  service_wechat varchar(64) DEFAULT NULL,
  opens varchar(5) NOT NULL,
  closes varchar(5) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'active',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_code (tenant_id, code),
  KEY idx_store_scope (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gym_employee (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint DEFAULT NULL COMMENT 'system_users.id，可暂不关联',
  employee_no varchar(64) NOT NULL,
  name varchar(80) NOT NULL,
  phone varchar(11) NOT NULL,
  job_title varchar(80) DEFAULT NULL,
  hired_on date NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'active',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_no (tenant_id, employee_no),
  UNIQUE KEY uk_employee_user (tenant_id, user_id),
  KEY idx_employee_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gym_employee_store (
  id bigint NOT NULL AUTO_INCREMENT,
  employee_id bigint NOT NULL,
  store_id bigint NOT NULL,
  is_primary bit(1) NOT NULL,
  is_manager bit(1) NOT NULL,
  joined_on date NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_store (tenant_id, employee_id, store_id),
  KEY idx_employee_scope (tenant_id, store_id, deleted, employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gym_store_media (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL,
  file_id bigint NOT NULL,
  caption varchar(200) DEFAULT NULL,
  is_cover bit(1) NOT NULL,
  sort_order int NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_media (tenant_id, store_id, file_id),
  KEY idx_media_order (tenant_id, store_id, sort_order, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
