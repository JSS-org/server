



CREATE TABLE IF NOT EXISTS gym_store (
  id bigint NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(30) NOT NULL,
  description varchar(2000) NOT NULL DEFAULT '',
  area_sqm decimal(12,2) NOT NULL,
  features text NOT NULL,
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
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS gym_employee (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint DEFAULT NULL,
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
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS gym_employee_store (
  id bigint NOT NULL AUTO_INCREMENT,
  employee_id bigint NOT NULL,
  store_id bigint NOT NULL,
  is_primary boolean NOT NULL,
  is_manager boolean NOT NULL,
  joined_on date NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS gym_store_media (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL,
  file_id bigint NOT NULL,
  caption varchar(200) DEFAULT NULL,
  is_cover boolean NOT NULL,
  sort_order int NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS gym_file (id bigint AUTO_INCREMENT PRIMARY KEY, store_id bigint, category varchar(32), name varchar(255), url varchar(500), mime_type varchar(100), size bigint, uploader_id bigint, creator varchar(64) DEFAULT '', create_time timestamp DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '', update_time timestamp DEFAULT CURRENT_TIMESTAMP, deleted boolean DEFAULT false, tenant_id bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX IF NOT EXISTS uk_store_code ON gym_store(tenant_id, code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_employee_no ON gym_employee(tenant_id, employee_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_employee_user ON gym_employee(tenant_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_employee_store ON gym_employee_store(tenant_id, employee_id, store_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_store_media ON gym_store_media(tenant_id, store_id, file_id);
