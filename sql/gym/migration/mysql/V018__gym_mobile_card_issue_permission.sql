-- Permission definition for the mobile issue-application entry only. No role grants.
SET @gym_issue_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '移动发卡-申请','gym:cards:issue',3,62,@gym_issue_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-mobile-issue',NOW(),'gym-mobile-issue',NOW(),b'0'
WHERE @gym_issue_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:cards:issue' AND m.deleted=b'0');
