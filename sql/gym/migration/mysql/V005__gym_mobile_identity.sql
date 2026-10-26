-- Member authentication was previously an optional module. No demo identities or roles.
CREATE TABLE IF NOT EXISTS member_user (
  id bigint NOT NULL AUTO_INCREMENT,
  mobile varchar(11) DEFAULT NULL,
  email varchar(50) DEFAULT NULL,
  password varchar(100) NOT NULL DEFAULT '',
  status tinyint NOT NULL DEFAULT 0,
  register_ip varchar(64) DEFAULT NULL,
  register_terminal int DEFAULT NULL,
  login_ip varchar(64) DEFAULT NULL,
  login_date datetime DEFAULT NULL,
  nickname varchar(30) NOT NULL DEFAULT '',
  avatar varchar(512) DEFAULT NULL,
  name varchar(30) DEFAULT NULL,
  sex tinyint DEFAULT NULL,
  birthday datetime DEFAULT NULL,
  area_id int DEFAULT NULL,
  mark varchar(255) DEFAULT NULL,
  point int NOT NULL DEFAULT 0,
  tag_ids varchar(512) DEFAULT NULL,
  level_id bigint DEFAULT NULL,
  experience int NOT NULL DEFAULT 0,
  group_id bigint DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_mobile (tenant_id,mobile,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gym_mobile_identity (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL,
  member_id bigint NOT NULL,
  default_store_id bigint NOT NULL,
  source varchar(64) NOT NULL,
  invite_code varchar(128) DEFAULT NULL,
  avatar_file_id bigint DEFAULT NULL,
  face_status varchar(16) NOT NULL DEFAULT 'unregistered',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mobile_user (tenant_id,user_id),
  UNIQUE KEY uk_mobile_member (tenant_id,member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gym_mobile_request (
  id bigint NOT NULL AUTO_INCREMENT,
  user_type int NOT NULL,
  user_id bigint NOT NULL,
  operation varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_key varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  response_json longtext DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mobile_request (tenant_id,user_type,user_id,operation,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Separate member ownership from gym_file.uploader_id, which denotes a system administrator.
CREATE TABLE gym_mobile_file_owner (
  id bigint NOT NULL AUTO_INCREMENT,
  file_id bigint NOT NULL,
  member_id bigint NOT NULL,
  purpose varchar(32) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mobile_file (tenant_id,file_id),
  KEY idx_mobile_member_files (tenant_id,member_id,purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
