-- One store service conversation per member; no historical messages or role grants.
CREATE TABLE IF NOT EXISTS gym_mobile_conversation (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 creator_user_id bigint NOT NULL,
 initiated_at datetime(6) NOT NULL,
 last_message_id bigint DEFAULT NULL,
 member_read_message_id bigint NOT NULL DEFAULT 0,
 member_unread_count int NOT NULL DEFAULT 0,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_mobile_conversation_member_store (tenant_id,member_id,store_id),
 KEY idx_mobile_conversation_inbox (tenant_id,member_id,deleted,last_message_id,id),
 CONSTRAINT ck_mobile_conversation_read CHECK (member_unread_count>=0 AND member_read_message_id>=0 AND member_read_message_id<=COALESCE(last_message_id,0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gym_mobile_message (
 id bigint NOT NULL AUTO_INCREMENT,
 conversation_id bigint NOT NULL,
 sender_type int NOT NULL,
 sender_user_id bigint NOT NULL,
 content varchar(2000) NOT NULL,
 sent_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 KEY idx_mobile_message_history (tenant_id,conversation_id,deleted,id),
 KEY idx_mobile_message_unread (tenant_id,conversation_id,sender_type,deleted,id),
 CONSTRAINT ck_mobile_message_sender CHECK (sender_type IN (1,2)),
 CONSTRAINT ck_mobile_message_content CHECK (CHAR_LENGTH(TRIM(content))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @gym_message_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '会员私信-读取','gym:messages:read',3,64,@gym_message_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-mobile-message',NOW(),'gym-mobile-message',NOW(),b'0'
WHERE @gym_message_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:messages:read' AND m.deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '会员私信-发送','gym:messages:send',3,65,@gym_message_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-mobile-message',NOW(),'gym-mobile-message',NOW(),b'0'
WHERE @gym_message_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:messages:send' AND m.deleted=b'0');
