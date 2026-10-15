



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
-- 薪资模板版本与员工配置；通过员工行锁串行化同一员工的区间写入。
CREATE TABLE IF NOT EXISTS gym_salary_template (
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

);
CREATE TABLE IF NOT EXISTS gym_salary_assignment (
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

);
CREATE UNIQUE INDEX IF NOT EXISTS uk_salary_version ON gym_salary_template(tenant_id, family_id, version);
-- 会员档案；租户内手机号及会员编号唯一，建档门店绑定现有 gym_store。
CREATE TABLE IF NOT EXISTS gym_member (
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
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_phone ON gym_member(tenant_id,phone);
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_no ON gym_member(tenant_id,member_no);
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

);
-- 卡模板与真实权益；仅内部审批/支付成功链路可发卡，source防重复。
CREATE TABLE IF NOT EXISTS gym_card_template (
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
);
CREATE TABLE IF NOT EXISTS gym_member_store (
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
);
CREATE TABLE IF NOT EXISTS gym_member_card (
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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_card_template_version ON gym_card_template(tenant_id,family_id,version);
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_store ON gym_member_store(tenant_id,member_id,store_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_card_source ON gym_member_card(tenant_id,source_type,source_id);
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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_pack_source ON gym_course_pack(tenant_id,source_type,source_id);
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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_member_session ON gym_booking(tenant_id,session_id,member_id);
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
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lesson_booking ON gym_lesson_record(tenant_id,booking_id);
CREATE TABLE IF NOT EXISTS gym_member_card_store (
  id bigint NOT NULL AUTO_INCREMENT,
  card_id bigint NOT NULL,
  store_id bigint NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_card_store ON gym_member_card_store(tenant_id,card_id,store_id);
-- 每租户一个事务锁：协调全部门店权益发放与新店创建/启用，防止并发漏关联。
CREATE TABLE IF NOT EXISTS gym_business_mutex (
  tenant_id bigint NOT NULL,
  id bigint NOT NULL,
  PRIMARY KEY (tenant_id,id)
);
-- 审批模板保存有序员工步骤；发布版本内容不可变。
CREATE TABLE IF NOT EXISTS gym_approval_template (
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
 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_template_version ON gym_approval_template(tenant_id,family_id,version);
CREATE TABLE IF NOT EXISTS gym_approval (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 template_id bigint NOT NULL,
 template_snapshot text NOT NULL,
 source_coach_id bigint DEFAULT NULL,
 business_type varchar(32) NOT NULL,
 title varchar(255) NOT NULL,
 applicant_user_id bigint NOT NULL,
 applicant_name varchar(100) NOT NULL,
 request_key varchar(128) NOT NULL,
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
 PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS gym_approval_task (
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
 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_request ON gym_approval(tenant_id,applicant_user_id,request_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_step ON gym_approval_task(tenant_id,approval_id,step_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_user ON gym_approval_task(tenant_id,approval_id,approver_user_id);
-- 合同PDF模板；版本发布后文件ID与配置不可改。
CREATE TABLE IF NOT EXISTS gym_contract_template (
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
 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_contract_template_version ON gym_contract_template(tenant_id,family_id,version);
CREATE TABLE IF NOT EXISTS gym_contract (
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
 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_contract_source ON gym_contract(tenant_id,source_type,source_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_contract_number ON gym_contract(tenant_id,contract_no);
CREATE TABLE IF NOT EXISTS gym_contract_event (
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
);
CREATE TABLE IF NOT EXISTS gym_contract_reminder (
 id bigint NOT NULL AUTO_INCREMENT,
 contract_id bigint NOT NULL,
 request_key varchar(128) NOT NULL,
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
 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_contract_reminder ON gym_contract_reminder(tenant_id,contract_id,request_key);
CREATE TABLE IF NOT EXISTS gym_financial_event (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 employee_id bigint DEFAULT NULL,
 business_order_id bigint NOT NULL,
 event_type varchar(16) NOT NULL,
 product_type varchar(16) NOT NULL,
 amount decimal(20,2) NOT NULL,
 source_type varchar(32) NOT NULL,
 source_id varchar(128) NOT NULL,
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
 CHECK (product_type IN ('card','private'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_financial_source ON gym_financial_event(tenant_id,source_type,source_id);
CREATE TABLE IF NOT EXISTS gym_payroll (
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
 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payroll_month ON gym_payroll(tenant_id,store_id,payroll_month);
CREATE TABLE IF NOT EXISTS gym_payroll_item (
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
);

CREATE TABLE IF NOT EXISTS gym_sales_setting (
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
);

CREATE TABLE IF NOT EXISTS gym_payment_account (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint DEFAULT NULL,
 scope_key bigint NOT NULL,
 account_name varchar(100) NOT NULL,
 provider varchar(16) NOT NULL,
 merchant_id varchar(64) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_settlement_rule (
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
);
CREATE TABLE IF NOT EXISTS gym_settlement (
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
);

CREATE TABLE IF NOT EXISTS gym_activity (
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
);
CREATE TABLE IF NOT EXISTS gym_activity_store (
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
);
CREATE TABLE IF NOT EXISTS gym_activity_registration (
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
);
CREATE TABLE IF NOT EXISTS gym_franchise_inquiry (
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
 request_key varchar(128) NOT NULL,
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
);

CREATE TABLE IF NOT EXISTS gym_device (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 name varchar(100) NOT NULL,
 type varchar(16) NOT NULL,
 provider varchar(64) NOT NULL,
 device_uid varchar(128) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_device_compartment (
 id bigint NOT NULL AUTO_INCREMENT,
 device_id bigint NOT NULL,
 number varchar(32) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_device_command (
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
 request_key varchar(128) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_equipment (
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
);
CREATE TABLE IF NOT EXISTS gym_equipment_tutorial (
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
);

-- 总部和门店独立配置；二维码仅记录真实渠道结果，不伪造生成图片。
CREATE TABLE IF NOT EXISTS gym_client_setting (
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
);
CREATE TABLE IF NOT EXISTS gym_qr_code (
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
);

-- 持久化交易来源；仅可信支付/代扣/验券链路可写结果，禁止管理端伪造成功。
CREATE TABLE IF NOT EXISTS gym_business_order (
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
 request_key varchar(128) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_order_item (
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
);
CREATE TABLE IF NOT EXISTS gym_order_payment (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 provider varchar(128) NOT NULL,
 provider_payment_id varchar(128) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_order_refund (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 payment_id bigint NOT NULL,
 provider varchar(128) NOT NULL,
 provider_refund_id varchar(128) NOT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_subscription (
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
 provider_agreement_id varchar(128) DEFAULT NULL,
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
);
CREATE TABLE IF NOT EXISTS gym_subscription_cycle (
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
);
CREATE TABLE IF NOT EXISTS gym_verification (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 member_name varchar(120) NOT NULL,
 type varchar(32) NOT NULL,
 channel varchar(32) NOT NULL,
 status varchar(32) NOT NULL,
 provider_reference varchar(128) DEFAULT NULL,
 request_key varchar(128) NOT NULL,
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
);

-- 下单时固定价格和履约/合同规则，后续设置修改不得追溯影响既有订单。
CREATE TABLE IF NOT EXISTS gym_order_policy (
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
);

-- 全额退款停卡；每次部分退款保留人工处理任务，决定不可覆盖。
CREATE TABLE IF NOT EXISTS gym_order_refund_review (
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
);

-- 订单自动履约持久任务；数据库租约与订单幂等共同保护多实例执行。
CREATE TABLE IF NOT EXISTS gym_fulfillment_task (
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
);

-- 已核验财务事件按订单项累计分摊，分笔支付/退款以累计份额差额记账，避免分钱偏差。
CREATE TABLE IF NOT EXISTS gym_order_item_financial_event (
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
);

CREATE TABLE IF NOT EXISTS gym_member_coach (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id bigint NOT NULL, store_id bigint NOT NULL, employee_id bigint NOT NULL, active boolean NOT NULL DEFAULT true,
 creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false, tenant_id bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_coach ON gym_member_coach(tenant_id,member_id,store_id,employee_id);

-- Member training archive; uses the existing member archive read/write permissions.
CREATE TABLE IF NOT EXISTS gym_training_plan (
 id bigint NOT NULL AUTO_INCREMENT,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 title varchar(120) NOT NULL,
 content text,
 file_id bigint,
 status varchar(16) NOT NULL DEFAULT 'active',
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id)
);
