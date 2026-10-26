-- One immutable 24-hour quote per approved special-price application; no historical quote backfill.
CREATE TABLE gym_course_quote (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 product_id bigint NOT NULL,
 product_version int NOT NULL,
 member_id bigint DEFAULT NULL,
 approval_id bigint DEFAULT NULL,
 issuer_user_id bigint NOT NULL,
 amount decimal(12,2) NOT NULL,
 terms_snapshot text NOT NULL,
 issued_at datetime(6) NOT NULL,
 expires_at datetime(6) NOT NULL,
 used_order_id bigint DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_course_quote_approval (tenant_id,approval_id),
 UNIQUE KEY uk_course_quote_order (tenant_id,used_order_id),
 KEY idx_course_quote_member (tenant_id,member_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Permission definition for the mobile course-quote creation entry only. No role grants.
SET @gym_quote_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '私教报价-创建','gym:courses:quote',3,63,@gym_quote_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-mobile-quote',NOW(),'gym-mobile-quote',NOW(),b'0'
WHERE @gym_quote_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:courses:quote' AND m.deleted=b'0');
