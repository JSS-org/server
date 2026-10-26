-- No reward policy, producers, initial balances, or historical point backfill.
CREATE TABLE IF NOT EXISTS gym_point_account (
 id bigint NOT NULL AUTO_INCREMENT,
 member_id bigint NOT NULL,
 balance bigint NOT NULL DEFAULT 0,
 total_earned bigint NOT NULL DEFAULT 0,
 total_spent bigint NOT NULL DEFAULT 0,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_point_account_member (tenant_id,member_id),
 CONSTRAINT ck_point_account_totals CHECK (total_earned BETWEEN 0 AND 9007199254740991 AND total_spent BETWEEN 0 AND 9007199254740991 AND balance=total_earned-total_spent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gym_point_record (
 id bigint NOT NULL AUTO_INCREMENT,
 account_id bigint NOT NULL,
 member_id bigint NOT NULL,
 business_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 type varchar(16) NOT NULL,
 delta bigint NOT NULL,
 balance_after bigint NOT NULL,
 reason varchar(200) NOT NULL,
 original_record_id bigint DEFAULT NULL,
 posted_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_point_record_event (tenant_id,business_key),
 KEY idx_point_record_member (tenant_id,member_id,deleted,id),
 KEY idx_point_record_type (tenant_id,member_id,deleted,type,id),
 KEY idx_point_record_reversal (tenant_id,original_record_id,deleted,id),
 CONSTRAINT ck_point_record_delta CHECK (delta<>0 AND delta BETWEEN -9007199254740991 AND 9007199254740991 AND balance_after BETWEEN -9007199254740991 AND 9007199254740991),
 CONSTRAINT ck_point_record_type CHECK ((type='earn' AND delta>0 AND original_record_id IS NULL) OR (type='spend' AND delta<0 AND original_record_id IS NULL) OR (type='refund' AND original_record_id IS NOT NULL)),
 CONSTRAINT ck_point_record_reason CHECK (CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
