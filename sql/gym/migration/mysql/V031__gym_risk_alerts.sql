-- Internal alert records and immutable human dispositions; no fabricated gateway events or auto freezes.
CREATE TABLE IF NOT EXISTS gym_risk_alert (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 device_id bigint NOT NULL,
 member_id bigint DEFAULT NULL,
 type varchar(32) NOT NULL,
 source_event_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 source_snapshot longtext NOT NULL,
 occurred_at datetime(6) NOT NULL,
 evidence_file_ids text NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'open',
 version int NOT NULL DEFAULT 1,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id),
 UNIQUE KEY uk_risk_alert_source(tenant_id,device_id,source_event_key,type),
 KEY idx_risk_alert_store(tenant_id,store_id,deleted,status,occurred_at,id),
 CONSTRAINT ck_risk_alert_type CHECK(type IN ('multiple_entry','tailgating','blacklist','overnight','debt')),
 CONSTRAINT ck_risk_alert_state CHECK((status='open' AND version=1) OR (status='resolved' AND version=2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS gym_risk_resolution (
 id bigint NOT NULL AUTO_INCREMENT,
 alert_id bigint NOT NULL,
 operator_user_id bigint NOT NULL,
 result varchar(32) NOT NULL,
 note varchar(2000) NOT NULL,
 previous_version int NOT NULL,
 resolved_at datetime(6) NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id),
 UNIQUE KEY uk_risk_resolution_alert(tenant_id,alert_id),
 CONSTRAINT ck_risk_resolution_result CHECK(result IN ('confirmed','false_positive')),
 CONSTRAINT ck_risk_resolution_audit CHECK(operator_user_id>0 AND previous_version=1 AND CHAR_LENGTH(TRIM(note))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @gym_risk_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '风险告警-读取','gym:risk:read',3,67,@gym_risk_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-risk-alert',NOW(),'gym-risk-alert',NOW(),b'0'
WHERE @gym_risk_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:risk:read' AND m.deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '风险告警-处置','gym:risk:resolve',3,68,@gym_risk_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-risk-alert',NOW(),'gym-risk-alert',NOW(),b'0'
WHERE @gym_risk_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:risk:resolve' AND m.deleted=b'0');
