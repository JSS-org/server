-- Member training archive; uses the existing member archive read/write permissions.
CREATE TABLE IF NOT EXISTS gym_training_plan (
 id bigint NOT NULL AUTO_INCREMENT,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 title varchar(120) NOT NULL,
 content mediumtext,
 file_id bigint,
 status varchar(16) NOT NULL DEFAULT 'active',
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 KEY idx_training_member (tenant_id,member_id,store_id,id),
 KEY idx_training_file (tenant_id,file_id)
);
