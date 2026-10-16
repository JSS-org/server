-- gym schema baseline. Requires existing framework system tables. No demo data.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `gym_file`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '文件编号',
    `store_id`    bigint                DEFAULT NULL COMMENT '归属门店编号（system_dept.id）',
    `category`    varchar(32)  NOT NULL COMMENT '文件类别：approval/body_report/contract/image/video',
    `name`        varchar(255) NOT NULL COMMENT '原始文件名',
    `url`         varchar(512) NOT NULL COMMENT 'infra 文件访问路径（私有，不返回前端）',
    `mime_type`   varchar(100) NOT NULL DEFAULT '' COMMENT '内容类型（魔数嗅探结果）',
    `size`        bigint       NOT NULL DEFAULT 0 COMMENT '文件大小，单位：字节',
    `uploader_id` bigint                DEFAULT NULL COMMENT '上传人编号（system_users.id）',
    `creator`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_gym_file_store_category` (`store_id`, `category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='gym 私有文件';

CREATE TABLE gym_store (
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

CREATE TABLE gym_employee (
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

CREATE TABLE gym_employee_store (
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

CREATE TABLE gym_store_media (
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

CREATE TABLE gym_salary_template (
  id bigint NOT NULL AUTO_INCREMENT,
  family_id bigint DEFAULT NULL,
  name varchar(100) NOT NULL,
  version int NOT NULL,
  base_salary decimal(12,2) NOT NULL,
  effective_from date NOT NULL,
  effective_to date DEFAULT NULL,
  status varchar(16) NOT NULL,
  rules text NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_salary_version (tenant_id, family_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_salary_assignment (
  id bigint NOT NULL AUTO_INCREMENT,
  employee_id bigint NOT NULL,
  store_id bigint NOT NULL,
  template_id bigint NOT NULL,
  effective_from date NOT NULL,
  effective_to date DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_salary_employee (tenant_id, employee_id, effective_from),
  KEY idx_salary_store (tenant_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_member (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL,
  member_no varchar(64) NOT NULL,
  name varchar(80) NOT NULL,
  phone varchar(11) NOT NULL,
  nickname varchar(80) DEFAULT NULL,
  gender varchar(16) NOT NULL,
  birthday date DEFAULT NULL,
  status varchar(16) NOT NULL,
  customer_stage varchar(16) NOT NULL,
  registered_at datetime(6) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_phone (tenant_id,phone),
  UNIQUE KEY uk_member_no (tenant_id,member_no),
  KEY idx_member_store (tenant_id,store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_blacklist (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_measurement (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_card_template (
  id bigint NOT NULL AUTO_INCREMENT,
  family_id bigint DEFAULT NULL,
  version int NOT NULL,
  name varchar(100) NOT NULL,
  kind varchar(16) NOT NULL,
  duration_days int DEFAULT NULL,
  included_uses int DEFAULT NULL,
  price decimal(12,2) NOT NULL,
  member_prefix varchar(100) DEFAULT NULL,
  access_scope varchar(16) NOT NULL,
  store_ids text NOT NULL,
  status varchar(16) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_card_template_version (tenant_id,family_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_member_store (
  id bigint NOT NULL AUTO_INCREMENT,
  member_id bigint NOT NULL,
  store_id bigint NOT NULL,
  joined_at datetime(6) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_member_store (tenant_id,member_id,store_id),
  KEY idx_member_store_scope (tenant_id,store_id,member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_member_card (
  id bigint NOT NULL AUTO_INCREMENT,
  member_id bigint NOT NULL,
  issued_store_id bigint NOT NULL,
  template_id bigint NOT NULL,
  card_no varchar(64) NOT NULL,
  display_prefix varchar(100) NOT NULL,
  name varchar(100) NOT NULL,
  kind varchar(16) NOT NULL,
  access_scope varchar(16) NOT NULL,
  store_ids text NOT NULL,
  starts_at datetime(6) NOT NULL,
  expires_at datetime(6) DEFAULT NULL,
  total_uses int DEFAULT NULL,
  remaining_uses int DEFAULT NULL,
  reserved_uses int NOT NULL DEFAULT 0,
  status varchar(16) NOT NULL,
  template_snapshot text NOT NULL,
  source_type varchar(32) NOT NULL,
  source_id varchar(100) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_member_card_source (tenant_id,source_type,source_id),
  UNIQUE KEY uk_member_card_no (tenant_id,card_no),
  KEY idx_member_card_member (tenant_id,member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_member_card_store (
  id bigint NOT NULL AUTO_INCREMENT,
  card_id bigint NOT NULL,
  store_id bigint NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_store (tenant_id,card_id,store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_course_product (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_course_session (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_course_pack (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_booking (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_checkin (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_lesson_record (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_business_mutex (
  tenant_id bigint NOT NULL,
  id bigint NOT NULL,
  PRIMARY KEY (tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_approval_template (
 id bigint NOT NULL AUTO_INCREMENT,
 family_id bigint DEFAULT NULL,
 version int NOT NULL,
 name varchar(100) NOT NULL,
 business_type varchar(32) NOT NULL,
 steps text NOT NULL,
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_approval_template_version (tenant_id,family_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_approval (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 template_id bigint NOT NULL,
 template_snapshot text NOT NULL,
 business_type varchar(32) NOT NULL,
 title varchar(255) NOT NULL,
 applicant_user_id bigint NOT NULL,
 applicant_name varchar(100) NOT NULL,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 request_hash varchar(64) NOT NULL,
 request_snapshot text NOT NULL,
 attachment_ids text NOT NULL,
 status varchar(16) NOT NULL,
 current_step int NOT NULL,
 submitted_at datetime(6) NOT NULL,
 completed_at datetime(6) DEFAULT NULL,
 business_result text DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_approval_request (tenant_id,applicant_user_id,request_key),
 KEY idx_approval_store (tenant_id,store_id,status,submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_approval_task (
 id bigint NOT NULL AUTO_INCREMENT,
 approval_id bigint NOT NULL,
 step_no int NOT NULL,
 approver_employee_id bigint NOT NULL,
 approver_user_id bigint NOT NULL,
 approver_name varchar(100) NOT NULL,
 status varchar(16) NOT NULL,
 comment varchar(2000) DEFAULT NULL,
 decided_at datetime(6) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_approval_step (tenant_id,approval_id,step_no),
 UNIQUE KEY uk_approval_user (tenant_id,approval_id,approver_user_id),
 KEY idx_approval_inbox (tenant_id,approver_user_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_contract_template (
 id bigint NOT NULL AUTO_INCREMENT,
 family_id bigint DEFAULT NULL,
 version int NOT NULL,
 name varchar(100) NOT NULL,
 applicable_type varchar(16) NOT NULL,
 reminder_days int NOT NULL,
 file_id bigint NOT NULL,
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_contract_template_version (tenant_id,family_id,version),
 KEY idx_contract_template_file (tenant_id,file_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_contract (
 id bigint NOT NULL AUTO_INCREMENT,
 contract_no varchar(64) NOT NULL,
 name varchar(160) NOT NULL,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 template_id bigint NOT NULL,
 template_snapshot text NOT NULL,
 business_snapshot mediumtext NOT NULL,
 source_type varchar(32) NOT NULL,
 source_id bigint NOT NULL,
 unsigned_file_id bigint NOT NULL,
 signed_file_id bigint DEFAULT NULL,
 status varchar(16) NOT NULL,
 approval_status varchar(16) NOT NULL,
 approved_approval_id bigint DEFAULT NULL,
 approved_at datetime(6) DEFAULT NULL,
 signing_status varchar(32) NOT NULL,
 due_at datetime(6) NOT NULL,
 signed_at datetime(6) DEFAULT NULL,
 sign_ip varchar(64) DEFAULT NULL,
 sign_device_uid varchar(128) DEFAULT NULL,
 sign_user_agent varchar(512) DEFAULT NULL,
 issued_at datetime(6) NOT NULL,
 cancelled_at datetime(6) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_contract_source (tenant_id,source_type,source_id),
 UNIQUE KEY uk_contract_number (tenant_id,contract_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_contract_event (
 id bigint NOT NULL AUTO_INCREMENT,
 contract_id bigint NOT NULL,
 type varchar(32) NOT NULL,
 occurred_at datetime(6) NOT NULL,
 actor_user_id bigint NOT NULL,
 ip varchar(64) DEFAULT NULL,
 detail varchar(2000) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_contract_reminder (
 id bigint NOT NULL AUTO_INCREMENT,
 contract_id bigint NOT NULL,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 channel varchar(16) NOT NULL,
 status varchar(16) NOT NULL,
 failure_reason varchar(2000) DEFAULT NULL,
 scheduled_at datetime(6) NOT NULL,
 completed_at datetime(6) DEFAULT NULL,
 requested_by bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_contract_reminder (tenant_id,contract_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_financial_event (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 employee_id bigint DEFAULT NULL,
 business_order_id bigint NOT NULL,
 event_type varchar(16) NOT NULL,
 product_type varchar(16) NOT NULL,
 amount decimal(20,2) NOT NULL,
 source_type varchar(32) NOT NULL,
 source_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 occurred_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 CHECK (amount>=0),
 CHECK (event_type IN ('payment','refund')),
 CHECK (product_type IN ('card','private')),
 UNIQUE KEY uk_financial_source (tenant_id,source_type,source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_payroll (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 store_name varchar(30) NOT NULL,
 payroll_month varchar(7) NOT NULL,
 employee_count int NOT NULL,
 net_amount decimal(20,2) NOT NULL,
 status varchar(16) NOT NULL,
 calculated_at datetime(6) NOT NULL,
 confirmed_at datetime(6) DEFAULT NULL,
 confirmed_by bigint DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_payroll_month (tenant_id,store_id,payroll_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_payroll_item (
 id bigint NOT NULL AUTO_INCREMENT,
 salary_assignment_id bigint NOT NULL,
 payroll_id bigint NOT NULL,
 employee_id bigint NOT NULL,
 employee_name varchar(80) NOT NULL,
 base_salary decimal(20,2) NOT NULL,
 card_commission decimal(20,2) NOT NULL,
 pt_commission decimal(20,2) NOT NULL,
 lesson_commission decimal(20,2) NOT NULL,
 allowances decimal(20,2) NOT NULL,
 deductions decimal(20,2) NOT NULL,
 gross_amount decimal(20,2) NOT NULL,
 net_amount decimal(20,2) NOT NULL,
 calculation_snapshot mediumtext NOT NULL,
 components mediumtext NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_sales_setting (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 kind varchar(32) NOT NULL,
 content mediumtext NOT NULL,
 revision bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_sales_setting (tenant_id,store_id,kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_payment_account (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint DEFAULT NULL,
 scope_key bigint NOT NULL,
 account_name varchar(100) NOT NULL,
 provider varchar(16) NOT NULL,
 merchant_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 last_four char(4) NOT NULL,
 credential_ref varchar(128) DEFAULT NULL,
 binding_status varchar(16) NOT NULL,
 binding_attempted_at datetime(6) DEFAULT NULL,
 binding_failure_reason varchar(300) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_payment_account (tenant_id,scope_key,provider,merchant_id,last_four)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_settlement_rule (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 version int NOT NULL,
 cycle_unit varchar(16) NOT NULL,
 cycle_value int NOT NULL,
 effective_from date NOT NULL,
 effective_to date DEFAULT NULL,
 status varchar(16) NOT NULL,
 items mediumtext NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_settlement_rule_version (tenant_id,store_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_settlement (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 store_name varchar(30) NOT NULL,
 financial_event_id bigint NOT NULL,
 rule_id bigint NOT NULL,
 rule_snapshot mediumtext NOT NULL,
 order_no varchar(64) NOT NULL,
 base_amount decimal(20,2) NOT NULL,
 due_at datetime(6) NOT NULL,
 status varchar(16) NOT NULL,
 items mediumtext NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 CHECK (base_amount>=0),
 UNIQUE KEY uk_settlement_event (tenant_id,financial_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_activity (
 id bigint NOT NULL AUTO_INCREMENT,
 name varchar(100) NOT NULL,
 description varchar(4000) NOT NULL DEFAULT '',
 registration_starts_at datetime(6) NOT NULL,
 registration_ends_at datetime(6) NOT NULL,
 starts_at datetime(6) NOT NULL,
 ends_at datetime(6) NOT NULL,
 capacity int DEFAULT NULL,
 cover_file_id bigint DEFAULT NULL,
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_activity_store (
 id bigint NOT NULL AUTO_INCREMENT,
 activity_id bigint NOT NULL,
 store_id bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_activity_store (tenant_id,activity_id,store_id),
 KEY idx_activity_store_scope (tenant_id,store_id,activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_activity_registration (
 id bigint NOT NULL AUTO_INCREMENT,
 activity_id bigint NOT NULL,
 store_id bigint NOT NULL,
 store_name varchar(30) NOT NULL,
 member_id bigint NOT NULL,
 member_name varchar(80) NOT NULL,
 status varchar(16) NOT NULL,
 registered_at datetime(6) NOT NULL,
 cancelled_at datetime(6) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_activity_member (tenant_id,activity_id,member_id),
 KEY idx_activity_registration_count (tenant_id,activity_id,status,store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_franchise_inquiry (
 id bigint NOT NULL AUTO_INCREMENT,
 contact_name varchar(80) NOT NULL,
 phone varchar(11) NOT NULL,
 preferred_city varchar(100) NOT NULL,
 message varchar(2000) NOT NULL DEFAULT '',
 status varchar(16) NOT NULL,
 received_at datetime(6) NOT NULL,
 contacted_at datetime(6) DEFAULT NULL,
 closed_at datetime(6) DEFAULT NULL,
 handled_by bigint DEFAULT NULL,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 request_hash varchar(64) NOT NULL,
 channel varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_franchise_request (tenant_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_device (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 name varchar(100) NOT NULL,
 type varchar(16) NOT NULL,
 provider varchar(64) NOT NULL,
 device_uid varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 location varchar(200) NOT NULL DEFAULT '',
 status varchar(16) NOT NULL,
 online boolean NOT NULL DEFAULT false,
 last_seen_at datetime(6) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_device_identity (tenant_id,provider,device_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_device_compartment (
 id bigint NOT NULL AUTO_INCREMENT,
 device_id bigint NOT NULL,
 number varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 status varchar(16) NOT NULL,
 configured boolean NOT NULL DEFAULT true,
 occupancy_state varchar(16) NOT NULL DEFAULT 'unknown',
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_device_compartment (tenant_id,device_id,number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_device_command (
 id bigint NOT NULL AUTO_INCREMENT,
 device_id bigint NOT NULL,
 compartment_id bigint DEFAULT NULL,
 command varchar(16) NOT NULL,
 reason varchar(500) NOT NULL,
 requester_id bigint NOT NULL,
 requester_name varchar(80) NOT NULL,
 requested_at datetime(6) NOT NULL,
 status varchar(16) NOT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 simulated boolean NOT NULL DEFAULT false,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 request_hash varchar(64) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_device_command_request (tenant_id,device_id,requester_id,request_key),
 KEY idx_device_command_date (tenant_id,device_id,requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_equipment (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 code varchar(64) NOT NULL,
 name varchar(100) NOT NULL,
 model varchar(100) NOT NULL DEFAULT '',
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_equipment_code (tenant_id,store_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_equipment_tutorial (
 id bigint NOT NULL AUTO_INCREMENT,
 equipment_id bigint NOT NULL,
 title varchar(100) NOT NULL,
 file_id bigint NOT NULL,
 instructions varchar(4000) NOT NULL DEFAULT '',
 sort_order int NOT NULL,
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_client_setting (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint DEFAULT NULL,
 scope_key bigint NOT NULL,
 platform varchar(16) NOT NULL,
 content mediumtext NOT NULL,
 revision bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_client_setting_scope (tenant_id,scope_key,platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_qr_code (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 platform varchar(16) NOT NULL,
 scene_type varchar(16) NOT NULL,
 scene_id bigint DEFAULT NULL,
 scene_key bigint NOT NULL,
 status varchar(16) NOT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 provider_code_file_id bigint DEFAULT NULL,
 created_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_qr_code_scene (tenant_id,store_id,platform,scene_type,scene_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_business_order (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 employee_id bigint DEFAULT NULL,
 order_no varchar(128) NOT NULL,
 member_name varchar(120) NOT NULL,
 business_type varchar(32) NOT NULL,
 channel varchar(32) NOT NULL,
 status varchar(32) NOT NULL,
 payable_amount decimal(18,2) NOT NULL,
 paid_amount decimal(18,2) NOT NULL,
 refunded_amount decimal(18,2) NOT NULL,
 ordered_at datetime(6) NOT NULL,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_business_order_no (tenant_id,order_no),
 UNIQUE KEY uk_business_order_request (tenant_id,store_id,channel,request_key),
 KEY idx_business_order_store_date (tenant_id,store_id,ordered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_order_item (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 product_type varchar(32) NOT NULL,
 product_id bigint NOT NULL,
 product_name_snapshot varchar(120) NOT NULL,
 product_version int NOT NULL,
 quantity int NOT NULL,
 unit_price decimal(18,2) NOT NULL,
 payable_amount decimal(18,2) NOT NULL,
 entitlement_ids text DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 KEY idx_order_item_order (tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_order_payment (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 provider varchar(128) NOT NULL,
 provider_payment_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 amount decimal(18,2) NOT NULL,
 status varchar(32) NOT NULL,
 paid_at datetime(6) DEFAULT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_payment_provider (tenant_id,provider,provider_payment_id),
 KEY idx_order_payment_order (tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_order_refund (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 payment_id bigint NOT NULL,
 provider varchar(128) NOT NULL,
 provider_refund_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 amount decimal(18,2) NOT NULL,
 status varchar(32) NOT NULL,
 refunded_at datetime(6) DEFAULT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_refund_provider (tenant_id,provider,provider_refund_id),
 KEY idx_order_refund_order (tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_subscription (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 member_name varchar(120) NOT NULL,
 card_template_id bigint NOT NULL,
 channel varchar(32) NOT NULL,
 status varchar(32) NOT NULL,
 amount decimal(18,2) NOT NULL,
 billing_day int NOT NULL,
 provider varchar(128) NOT NULL,
 provider_agreement_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
 requested_at datetime(6) NOT NULL,
 signed_at datetime(6) DEFAULT NULL,
 next_charge_at datetime(6) DEFAULT NULL,
 cancelled_at datetime(6) DEFAULT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_subscription_agreement (tenant_id,provider,provider_agreement_id),
 KEY idx_subscription_store_date (tenant_id,store_id,requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_subscription_cycle (
 id bigint NOT NULL AUTO_INCREMENT,
 subscription_id bigint NOT NULL,
 order_id bigint DEFAULT NULL,
 period_start date NOT NULL,
 period_end date NOT NULL,
 amount decimal(18,2) NOT NULL,
 paid_amount decimal(18,2) NOT NULL,
 status varchar(32) NOT NULL,
 charged_at datetime(6) DEFAULT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_subscription_period (tenant_id,subscription_id,period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_verification (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 member_name varchar(120) NOT NULL,
 type varchar(32) NOT NULL,
 channel varchar(32) NOT NULL,
 status varchar(32) NOT NULL,
 provider_reference varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 member_card_id bigint DEFAULT NULL,
 checkin_id bigint DEFAULT NULL,
 verified_at datetime(6) NOT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_verification_request (tenant_id,store_id,request_key),
 UNIQUE KEY uk_verification_provider (tenant_id,channel,provider_reference),
 KEY idx_verification_store_date (tenant_id,store_id,verified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_order_policy (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 request_hash varchar(64) NOT NULL,
 terms_snapshot mediumtext NOT NULL,
 approval_required boolean NOT NULL,
 contract_required boolean NOT NULL,
 approved_approval_id bigint DEFAULT NULL,
 fulfilled_at datetime(6) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_order_policy (tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_order_refund_review (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 refund_id bigint NOT NULL,
 status varchar(32) NOT NULL,
 decision varchar(32) DEFAULT NULL,
 member_card_ids text NOT NULL,
 note varchar(2000) DEFAULT NULL,
 reviewer_id bigint DEFAULT NULL,
 reviewed_at datetime(6) DEFAULT NULL,
 created_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_order_refund_review (tenant_id,refund_id),
 KEY idx_refund_review_order (tenant_id,order_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_fulfillment_task (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 principal_user_id bigint NOT NULL,
 status varchar(32) NOT NULL,
 attempt_count int NOT NULL,
 attempt_limit int NOT NULL,
 next_attempt_at datetime(6) DEFAULT NULL,
 last_attempt_at datetime(6) DEFAULT NULL,
 lease_until datetime(6) DEFAULT NULL,
 claim_token varchar(36) DEFAULT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_fulfillment_order (tenant_id,order_id),
 KEY idx_fulfillment_due (tenant_id,status,next_attempt_at),
 KEY idx_fulfillment_lease (tenant_id,status,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_order_item_financial_event (
 id bigint NOT NULL AUTO_INCREMENT,
 financial_event_id bigint NOT NULL,
 order_item_id bigint NOT NULL,
 amount decimal(20,2) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 CHECK (amount>=0),
 UNIQUE KEY uk_item_financial_event (tenant_id,financial_event_id,order_item_id),
 KEY idx_item_financial_order_item (tenant_id,order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_member_coach (
 id bigint NOT NULL AUTO_INCREMENT,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 employee_id bigint NOT NULL,
 active boolean NOT NULL DEFAULT true,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_member_coach (tenant_id,member_id,store_id,employee_id),
 KEY idx_member_coach_employee (tenant_id,employee_id,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE gym_training_plan (
 id bigint NOT NULL AUTO_INCREMENT,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 title varchar(120) NOT NULL,
 content mediumtext,
 file_id bigint,
 status varchar(16) NOT NULL DEFAULT 'active',
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 KEY idx_training_member (tenant_id,member_id,store_id,id),
 KEY idx_training_file (tenant_id,file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE gym_approval ADD COLUMN source_coach_id bigint DEFAULT NULL;
CREATE INDEX idx_approval_own_price ON gym_approval(tenant_id,applicant_user_id,business_type,source_coach_id,store_id);
