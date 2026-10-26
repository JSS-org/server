-- Reserve inventory and numerical refund headroom before asynchronous card fulfillment.
ALTER TABLE gym_point_product
 ADD COLUMN reserved_stock int NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_point_product_reserved CHECK (reserved_stock>=0 AND (stock IS NULL OR CAST(stock AS SIGNED)+reserved_stock<=2147483647) AND revision+reserved_stock<=9007199254740991);
ALTER TABLE gym_point_account
 ADD COLUMN refund_reserve bigint NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_point_account_refund CHECK (refund_reserve>=0 AND total_earned+refund_reserve<=9007199254740991);
CREATE TABLE IF NOT EXISTS gym_point_redemption (
 id bigint NOT NULL AUTO_INCREMENT,
 user_id bigint NOT NULL,
 request_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 product_id bigint NOT NULL,
 product_revision bigint NOT NULL,
 required_points bigint NOT NULL,
 stock_reserved bit(1) NOT NULL,
 template_snapshot longtext NOT NULL,
 status varchar(16) NOT NULL,
 debit_record_id bigint DEFAULT NULL,
 refund_record_id bigint DEFAULT NULL,
 member_card_id bigint DEFAULT NULL,
 requested_at datetime(6) NOT NULL,
 completed_at datetime(6) DEFAULT NULL,
 failure_code int DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id),
 UNIQUE KEY uk_point_redemption_request(tenant_id,user_id,request_key),
 UNIQUE KEY uk_point_redemption_debit(tenant_id,debit_record_id),
 UNIQUE KEY uk_point_redemption_card(tenant_id,member_card_id),
 UNIQUE KEY uk_point_redemption_refund(tenant_id,refund_record_id),
 KEY idx_point_redemption_member(tenant_id,user_id,member_id,deleted,id),
 KEY idx_point_redemption_pending(tenant_id,status,deleted,requested_at,id),
 CONSTRAINT ck_point_redemption_cost CHECK(required_points BETWEEN 1 AND 9007199254740991 AND product_revision BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_point_redemption_state CHECK(
  (status='processing' AND member_card_id IS NULL AND refund_record_id IS NULL AND completed_at IS NULL AND failure_code IS NULL)
  OR (status='succeeded' AND debit_record_id IS NOT NULL AND member_card_id IS NOT NULL AND refund_record_id IS NULL AND completed_at IS NOT NULL AND failure_code IS NULL)
  OR (status='failed' AND debit_record_id IS NOT NULL AND member_card_id IS NULL AND refund_record_id IS NOT NULL AND completed_at IS NOT NULL AND failure_code IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
