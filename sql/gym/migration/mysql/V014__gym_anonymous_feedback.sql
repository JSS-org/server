-- Feedback ownership is restricted to server authorization; no staff response exposes it.
CREATE TABLE gym_feedback (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 member_id bigint NOT NULL,
 type varchar(16) NOT NULL,
 content varchar(2000) NOT NULL,
 rating int DEFAULT NULL,
 allow_card_context bit(1) NOT NULL DEFAULT b'0',
 card_context text DEFAULT NULL,
 status varchar(16) NOT NULL,
 submitted_at datetime(6) NOT NULL,
 reply varchar(2000) DEFAULT NULL,
 replied_by bigint DEFAULT NULL,
 replied_at datetime(6) DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 KEY idx_feedback_member (tenant_id,member_id,submitted_at,id),
 KEY idx_feedback_store (tenant_id,store_id,status,submitted_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE gym_feedback_attachment (
 id bigint NOT NULL AUTO_INCREMENT,
 feedback_id bigint NOT NULL,
 file_id bigint NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_feedback_attachment_file (tenant_id,file_id),
 KEY idx_feedback_attachment_feedback (tenant_id,feedback_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register permissions only; do not grant roles or add frontend routes.
SET @gym_feedback_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT t.name,t.permission,3,t.sort,@gym_feedback_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-feedback',NOW(),'gym-feedback',NOW(),b'0'
FROM (SELECT '匿名反馈-查询' name,'gym:feedbacks:read' permission,60 sort UNION ALL SELECT '匿名反馈-回复','gym:feedbacks:reply',61) t
WHERE @gym_feedback_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission=t.permission AND m.deleted=b'0');
