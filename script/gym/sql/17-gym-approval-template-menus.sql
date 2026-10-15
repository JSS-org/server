-- 只上线gym-approval-templates 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-approval-templates', NOW(), 'gym-approval-templates', NOW(), b'0'
FROM (
 SELECT '审批模板' name, 'gym:approvals:templates' permission, 17 sort, 'approval-templates' path, 'GymApprovalTemplates' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-approval-templates', NOW(), 'gym-approval-templates', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('approval-templates') AND m.type = 2 AND m.deleted = b'0'
WHERE r.tenant_id = 1 AND r.code IN ('gym_hq_admin') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');
