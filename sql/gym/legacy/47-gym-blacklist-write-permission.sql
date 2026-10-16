-- Align the current singular permission without deleting/replacing any legacy menu or custom grants.
-- Only the tenant-1 demo HQ role that already holds the legacy edit capability receives the corrected alias.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_blacklist_page = (SELECT m.id FROM system_menu m JOIN system_menu root ON root.id=m.parent_id
 WHERE root.name='健身管理' AND root.parent_id=0 AND root.deleted=b'0'
 AND m.path='blacklists' AND m.type=2 AND m.deleted=b'0' ORDER BY m.id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '黑名单-编辑（现行契约）','gym:blacklist:write',3,1,@gym_blacklist_page,'','#',NULL,NULL,
 0,b'1',b'1',b'1','gym-permission-align',NOW(),'gym-permission-align',NOW(),b'0'
WHERE @gym_blacklist_page IS NOT NULL AND NOT EXISTS (
 SELECT 1 FROM system_menu WHERE permission='gym:blacklist:write' AND deleted=b'0');
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT r.id,m.id,'gym-permission-align',NOW(),'gym-permission-align',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.permission='gym:blacklist:write' AND m.deleted=b'0'
WHERE r.tenant_id=1 AND r.code='gym_hq_admin' AND r.data_scope=1 AND r.status=0 AND r.deleted=b'0'
 AND EXISTS (SELECT 1 FROM system_role_menu old_grant JOIN system_menu legacy ON legacy.id=old_grant.menu_id
  WHERE old_grant.role_id=r.id AND old_grant.tenant_id=r.tenant_id AND old_grant.deleted=b'0'
  AND legacy.permission='gym:blacklists:write' AND legacy.deleted=b'0')
 AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id=r.id AND existing.menu_id=m.id
  AND existing.tenant_id=r.tenant_id AND existing.deleted=b'0');
