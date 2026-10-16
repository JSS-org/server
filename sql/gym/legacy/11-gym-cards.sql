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
, UNIQUE KEY uk_card_template_version (tenant_id,family_id,version)
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
, UNIQUE KEY uk_member_store (tenant_id,member_id,store_id),
  KEY idx_member_store_scope (tenant_id,store_id,member_id)
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
, UNIQUE KEY uk_member_card_source (tenant_id,source_type,source_id),
  UNIQUE KEY uk_member_card_no (tenant_id,card_no),
  KEY idx_member_card_member (tenant_id,member_id)
);
-- 将此前已建档会员的初始门店关系补齐；按自然键幂等，不扩大其他门店范围。
INSERT INTO gym_member_store (member_id,store_id,joined_at,creator,updater,tenant_id)
SELECT m.id,m.store_id,m.registered_at,'gym-card-migration','gym-card-migration',m.tenant_id FROM gym_member m
WHERE m.deleted=0 AND NOT EXISTS (SELECT 1 FROM gym_member_store ms WHERE ms.member_id=m.id AND ms.store_id=m.store_id AND ms.tenant_id=m.tenant_id AND ms.deleted=0);
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
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_store (tenant_id,card_id,store_id)
);

-- 首批会员建档阶段对齐现行前端 lead/member 枚举。
UPDATE gym_member SET customer_stage='lead' WHERE customer_stage='prospect';
