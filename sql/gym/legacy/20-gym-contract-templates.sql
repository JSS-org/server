-- 合同PDF模板；版本发布后文件ID与配置不可改。
CREATE TABLE IF NOT EXISTS gym_contract_template (
 id bigint NOT NULL AUTO_INCREMENT,
 family_id bigint DEFAULT NULL,
 version int NOT NULL,
 name varchar(100) NOT NULL,
 applicable_type varchar(16) NOT NULL,
 reminder_days int NOT NULL,
 file_id bigint NOT NULL,
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_contract_template_version (tenant_id,family_id,version),
 KEY idx_contract_template_file (tenant_id,file_id,status)
);
