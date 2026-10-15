-- 私教、权益、排期、预约、签到和签课；容量及预占/扣课由行锁事务保护。
CREATE TABLE IF NOT EXISTS gym_course_product (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL, coach_id bigint NOT NULL,
  name varchar(100) NOT NULL, description varchar(2000) DEFAULT NULL, kind varchar(16) NOT NULL,
  session_count int NOT NULL, duration_minutes int NOT NULL, price decimal(12,2) NOT NULL, status varchar(16) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_course_product_store (tenant_id,store_id)
);
CREATE TABLE IF NOT EXISTS gym_course_session (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL, course_product_id bigint NOT NULL, coach_id bigint NOT NULL,
  room varchar(100) DEFAULT NULL, starts_at datetime(6) NOT NULL, ends_at datetime(6) NOT NULL,
  capacity int NOT NULL, reserved_count int NOT NULL DEFAULT 0, status varchar(16) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_course_session_coach_time (tenant_id,coach_id,starts_at,ends_at)
);
CREATE TABLE IF NOT EXISTS gym_course_pack (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL, member_id bigint NOT NULL, course_product_id bigint NOT NULL, assigned_coach_id bigint NOT NULL,
  name varchar(100) NOT NULL, starts_at datetime(6) NOT NULL, expires_at datetime(6) DEFAULT NULL,
  total_sessions int NOT NULL, remaining_sessions int NOT NULL, reserved_sessions int NOT NULL,
  status varchar(16) NOT NULL, price_snapshot decimal(12,2) NOT NULL,
  source_type varchar(32) NOT NULL, source_id varchar(100) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_course_pack_source (tenant_id,source_type,source_id),
  KEY idx_course_pack_member (tenant_id,member_id,store_id)
);
CREATE TABLE IF NOT EXISTS gym_booking (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL, session_id bigint NOT NULL, member_id bigint NOT NULL,
  member_card_id bigint DEFAULT NULL, member_course_pack_id bigint DEFAULT NULL,
  status varchar(16) NOT NULL, booked_at datetime(6) NOT NULL, cancelled_at datetime(6) DEFAULT NULL,
  checkin_id bigint DEFAULT NULL, note varchar(2000) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_booking_member_session (tenant_id,session_id,member_id)
);
CREATE TABLE IF NOT EXISTS gym_checkin (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL, member_id bigint NOT NULL, booking_id bigint NOT NULL,
  entered_at datetime(6) NOT NULL, exited_at datetime(6) DEFAULT NULL, method varchar(16) NOT NULL,
  visitor_type varchar(16) NOT NULL, status varchar(16) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_checkin_scope_time (tenant_id,store_id,entered_at)
);
CREATE TABLE IF NOT EXISTS gym_lesson_record (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL, member_id bigint NOT NULL, coach_id bigint NOT NULL, booking_id bigint NOT NULL,
  checkin_id bigint NOT NULL, member_card_id bigint DEFAULT NULL, member_course_pack_id bigint DEFAULT NULL,
  session_units int NOT NULL, confirmed_at datetime(6) NOT NULL, method varchar(16) NOT NULL, status varchar(16) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_lesson_booking (tenant_id,booking_id)
);
