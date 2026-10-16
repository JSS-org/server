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
