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
