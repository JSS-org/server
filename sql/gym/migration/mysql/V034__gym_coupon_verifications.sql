-- Durable single-voucher claims and exact-request provider proof. No historical/fabricated consumption.
CREATE TABLE gym_coupon_verification (
 id bigint NOT NULL AUTO_INCREMENT,
 user_id bigint NOT NULL,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 preview_id bigint NOT NULL,
 request_key varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_request_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 platform varchar(16) NOT NULL,
 coupon_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 claimed_coupon_hash char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
 scope_snapshot longtext NOT NULL,
 template_snapshot longtext NOT NULL,
 card_template_id bigint NOT NULL,
 deal_id bigint NOT NULL,
 deal_group_id bigint NOT NULL,
 status varchar(16) NOT NULL,
 stage varchar(16) NOT NULL,
 confirmation_snapshot longtext DEFAULT NULL,
 provider_flow_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
 member_card_id bigint DEFAULT NULL,
 verification_id bigint DEFAULT NULL,
 requested_at datetime(6) NOT NULL,
 consume_started_at datetime(6) DEFAULT NULL,
 confirmed_at datetime(6) DEFAULT NULL,
 completed_at datetime(6) DEFAULT NULL,
 next_check_at datetime(6) NOT NULL,
 query_count int NOT NULL DEFAULT 0,
 observation_snapshot longtext DEFAULT NULL,
 last_error_code int DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id),
 UNIQUE KEY uk_coupon_verification_request(tenant_id,user_id,request_key),
 UNIQUE KEY uk_coupon_verification_preview(tenant_id,preview_id),
 UNIQUE KEY uk_coupon_verification_claim(tenant_id,platform,claimed_coupon_hash),
 UNIQUE KEY uk_coupon_verification_provider(tenant_id,provider_request_id),
 UNIQUE KEY uk_coupon_verification_flow(tenant_id,platform,provider_flow_id),
 UNIQUE KEY uk_coupon_verification_card(tenant_id,member_card_id),
 UNIQUE KEY uk_coupon_verification_legacy(tenant_id,verification_id),
 KEY idx_coupon_verification_owner(tenant_id,user_id,member_id,deleted,id),
 KEY idx_coupon_verification_due(tenant_id,status,deleted,next_check_at,id),
 CONSTRAINT ck_coupon_verification_ids CHECK(user_id>0 AND member_id>0 AND store_id>0 AND preview_id>0 AND card_template_id>0 AND deal_id>0 AND deal_group_id>0 AND platform='meituan'),
 CONSTRAINT ck_coupon_verification_query CHECK(query_count BETWEEN 0 AND 6),
 CONSTRAINT ck_coupon_verification_time CHECK(
  (consume_started_at IS NULL OR consume_started_at>=requested_at)
  AND (confirmed_at IS NULL OR (consume_started_at IS NOT NULL AND confirmed_at>=consume_started_at))
  AND (completed_at IS NULL OR completed_at>=requested_at)
  AND (completed_at IS NULL OR confirmed_at IS NULL OR completed_at>=confirmed_at)),
 CONSTRAINT ck_coupon_verification_claim CHECK(
  (status='failed' AND claimed_coupon_hash IS NULL) OR
  (status IN ('processing','succeeded') AND claimed_coupon_hash IS NOT NULL AND claimed_coupon_hash=coupon_hash)),
 CONSTRAINT ck_coupon_verification_state CHECK(
  (status='processing' AND stage IN ('prepared','sending','unknown','confirmed') AND member_card_id IS NULL AND verification_id IS NULL AND completed_at IS NULL)
  OR (status='succeeded' AND stage='fulfilled' AND member_card_id IS NOT NULL AND member_card_id>0 AND verification_id IS NOT NULL AND verification_id>0 AND completed_at IS NOT NULL)
  OR (status='failed' AND stage='rejected' AND member_card_id IS NULL AND verification_id IS NULL AND completed_at IS NOT NULL AND last_error_code IS NOT NULL)),
 CONSTRAINT ck_coupon_verification_proof CHECK(
  (stage IN ('confirmed','fulfilled') AND confirmed_at IS NOT NULL AND confirmation_snapshot IS NOT NULL AND CHAR_LENGTH(TRIM(confirmation_snapshot))>2 AND provider_flow_id IS NOT NULL AND CHAR_LENGTH(TRIM(provider_flow_id))>0)
  OR (stage IN ('prepared','sending','unknown','rejected') AND confirmed_at IS NULL AND confirmation_snapshot IS NULL AND provider_flow_id IS NULL)),
 CONSTRAINT ck_coupon_verification_dispatch CHECK((stage='prepared' AND consume_started_at IS NULL) OR (stage IN ('sending','unknown','confirmed','fulfilled') AND consume_started_at IS NOT NULL) OR stage='rejected')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
