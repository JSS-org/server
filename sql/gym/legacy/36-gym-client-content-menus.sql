-- 只上线gym-client-content 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-client-content', NOW(), 'gym-client-content', NOW(), b'0'
FROM (
 SELECT '客户端展示' name, 'gym:content:manage' permission, 27 sort, 'client-settings' path, 'Gym-client-settings' component_name
 UNION ALL SELECT '场景二维码','gym:stores:read',28,'qr-codes','Gym-qr-codes'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-client-content', NOW(), 'gym-client-content', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('client-settings','qr-codes') AND m.type = 2 AND m.deleted = b'0'
WHERE r.tenant_id = 1 AND r.code IN ('gym_hq_admin','gym_store_manager') AND (r.code='gym_hq_admin' OR m.path='qr-codes') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');
