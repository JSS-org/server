-- Requests and approvals never assert provider refund success. No historical applications are invented.
CREATE TABLE IF NOT EXISTS gym_refund_application (
 id bigint NOT NULL AUTO_INCREMENT,
 order_id bigint NOT NULL,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 requester_user_id bigint NOT NULL,
 active_order_id bigint DEFAULT NULL,
 status varchar(32) NOT NULL,
 requested_amount decimal(18,2) NOT NULL,
 refunded_amount decimal(18,2) NOT NULL DEFAULT 0,
 reason varchar(500) NOT NULL,
 version int NOT NULL DEFAULT 1,
 submitted_at datetime(6) NOT NULL,
 reviewer_user_id bigint DEFAULT NULL,
 reviewed_at datetime(6) DEFAULT NULL,
 review_note varchar(500) DEFAULT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_refund_application_active (tenant_id,active_order_id),
 KEY idx_refund_application_store (tenant_id,store_id,status,deleted,id),
 KEY idx_refund_application_member (tenant_id,member_id,requester_user_id,deleted,id),
 KEY idx_refund_application_order (tenant_id,order_id,deleted,id),
 CONSTRAINT ck_refund_application_active CHECK (
  (status IN ('pending_review','approved','partially_refunded') AND active_order_id IS NOT NULL AND active_order_id=order_id)
  OR (status IN ('refunded','rejected','closed') AND active_order_id IS NULL)),
 CONSTRAINT ck_refund_application_money CHECK (requested_amount>0 AND refunded_amount>=0 AND refunded_amount<=requested_amount AND version>=1)
);
ALTER TABLE gym_order_refund
 ADD COLUMN refund_application_id bigint DEFAULT NULL,
 ADD KEY idx_refund_application_receipt (tenant_id,refund_application_id,deleted,id);
