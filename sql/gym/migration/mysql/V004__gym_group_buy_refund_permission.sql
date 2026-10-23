-- 注册服务零售退款相关接口的独立权限；不授予任何角色，不新增前端页面。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu
                WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0'
                ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component,
                         component_name, status, visible, keep_alive, always_show,
                         creator, create_time, updater, update_time, deleted)
SELECT '团购退款-操作', 'gym:group-buy:refund', 3, 60, @gym_root, '', '#', NULL,
       NULL, 0, b'1', b'1', b'1', 'gym-group-buy', NOW(), 'gym-group-buy', NOW(), b'0'
WHERE @gym_root IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'gym:group-buy:refund' AND deleted = b'0'
);
