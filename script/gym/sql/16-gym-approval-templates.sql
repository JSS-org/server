-- 审批模板保存有序员工步骤；发布版本内容不可变。
CREATE TABLE IF NOT EXISTS gym_approval_template (
 id bigint NOT NULL AUTO_INCREMENT,
 family_id bigint DEFAULT NULL,
 version int NOT NULL,
 name varchar(100) NOT NULL,
 business_type varchar(32) NOT NULL,
 steps text NOT NULL,
 status varchar(16) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_approval_template_version (tenant_id,family_id,version)
);
