-- 门店在线售卡、连续包月、自助核销业务条件；不表示厂商渠道已经配置。
CREATE TABLE IF NOT EXISTS gym_sales_setting (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 kind varchar(32) NOT NULL,
 content mediumtext NOT NULL,
 revision bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_sales_setting (tenant_id,store_id,kind)
);
