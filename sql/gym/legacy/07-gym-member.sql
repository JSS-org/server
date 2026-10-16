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
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_phone (tenant_id,phone),
  UNIQUE KEY uk_member_no (tenant_id,member_no),
  KEY idx_member_store (tenant_id,store_id)
);
