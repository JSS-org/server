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
