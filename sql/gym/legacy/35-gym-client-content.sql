-- 总部和门店独立配置；二维码仅记录真实渠道结果，不伪造生成图片。
CREATE TABLE IF NOT EXISTS gym_client_setting (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint DEFAULT NULL,
 scope_key bigint NOT NULL,
 platform varchar(16) NOT NULL,
 content mediumtext NOT NULL,
 revision bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_client_setting_scope (tenant_id,scope_key,platform)
);
CREATE TABLE IF NOT EXISTS gym_qr_code (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 platform varchar(16) NOT NULL,
 scene_type varchar(16) NOT NULL,
 scene_id bigint DEFAULT NULL,
 scene_key bigint NOT NULL,
 status varchar(16) NOT NULL,
 failure_reason varchar(500) DEFAULT NULL,
 provider_code_file_id bigint DEFAULT NULL,
 created_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_qr_code_scene (tenant_id,store_id,platform,scene_type,scene_key)
);
