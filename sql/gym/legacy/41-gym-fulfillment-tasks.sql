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
