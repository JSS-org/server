-- Development only. @tenant_id is supplied by the explicit seed command.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @seed_user = 'gym-seed';
SET @seed_pwd = '$2a$10$DobR27ZollT9tVNGf2.FM.3A2S8Azi9IJbzYic6g660D6ta8LS8G2';
SET @gym_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=0 ORDER BY id LIMIT 1);
CREATE TEMPORARY TABLE gym_demo_guard (ok int NOT NULL CHECK(ok=1));
START TRANSACTION;
INSERT INTO gym_demo_guard SELECT IF(@tenant_id>0 AND @gym_root IS NOT NULL,1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@tenant_id AND code IN ('gym_hq_admin','gym_store_manager') AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND username IN ('gymadmin','gymstore') AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_dept WHERE tenant_id=@tenant_id AND name='健身总部' AND parent_id=0 AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
SET @gym_new_hq_role = NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@tenant_id AND code='gym_hq_admin');
SET @gym_new_store_role = NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@tenant_id AND code='gym_store_manager');
SET @gym_new_hq_user = NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND username='gymadmin');
SET @gym_new_store_user = NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND username='gymstore');
-- 3. 门店部门（门店 = system_dept，见 08-gym-api-contract.md §1）
--    「健身总部」为总部根部门，「示范门店」为门店级部门（演示数据范围用）
-- -----------------------------------------------------------------------------
INSERT INTO system_dept (name, parent_id, sort, leader_user_id, phone, email, status,
                         creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '健身总部', 0, 90, NULL, NULL, NULL, 0, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_dept WHERE name = '健身总部' AND parent_id = 0 AND deleted = b'0' AND tenant_id = @tenant_id);

SET @gym_hq_dept = (SELECT id FROM system_dept WHERE name = '健身总部' AND parent_id = 0 AND deleted = b'0' AND tenant_id = @tenant_id ORDER BY id LIMIT 1);

INSERT INTO system_dept (name, parent_id, sort, leader_user_id, phone, email, status,
                         creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '示范门店', @gym_hq_dept, 1, NULL, NULL, NULL, 0, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_dept WHERE name = '示范门店' AND parent_id = @gym_hq_dept AND deleted = b'0' AND tenant_id = @tenant_id);

SET @gym_store_dept = (SELECT id FROM system_dept WHERE name = '示范门店' AND parent_id = @gym_hq_dept AND deleted = b'0' AND tenant_id = @tenant_id ORDER BY id LIMIT 1);

-- -----------------------------------------------------------------------------
-- 4. 角色（09-permissions.md §4）
--    gym_hq_admin     总部管理员：全部数据（1）+ 全部 gym 权限
--    gym_store_manager 门店店长：本部门及以下（4）+ 门店侧 gym 权限（不含仅总部码）
-- -----------------------------------------------------------------------------
INSERT INTO system_role (name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
                         creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '健身总部管理员', 'gym_hq_admin', 90, 1, '', 0, 2, 'JSS 健身 · 总部管理员（全部门店，全部 gym 权限）',
       @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE code = 'gym_hq_admin' AND deleted = b'0' AND tenant_id = @tenant_id);

INSERT INTO system_role (name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
                         creator, create_time, updater, update_time, deleted, tenant_id)
SELECT '门店店长', 'gym_store_manager', 91, 4, '', 0, 2, 'JSS 健身 · 门店店长（本部门及以下，不含仅总部权限）',
       @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE code = 'gym_store_manager' AND deleted = b'0' AND tenant_id = @tenant_id);

SET @hq_role    = (SELECT id FROM system_role WHERE code = 'gym_hq_admin'      AND deleted = b'0' AND tenant_id = @tenant_id ORDER BY id LIMIT 1);
SET @store_role = (SELECT id FROM system_role WHERE code = 'gym_store_manager' AND deleted = b'0' AND tenant_id = @tenant_id ORDER BY id LIMIT 1);

-- -----------------------------------------------------------------------------
-- 5. 角色 × 菜单授权
--    总部角色：根目录 + 全部 gym 权限码
--    门店角色：根目录 + 门店侧权限码（排除 09-permissions.md §3 标注「仅总部」的 16 个码）
-- -----------------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @hq_role, m.id, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
FROM system_menu m
WHERE @gym_new_hq_role = 1 AND m.deleted = b'0' AND (m.id = @gym_root OR m.parent_id = @gym_root)
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = @hq_role AND rm.menu_id = m.id AND rm.deleted = b'0' AND rm.tenant_id = @tenant_id
  );

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @store_role, m.id, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
FROM system_menu m
WHERE @gym_new_store_role = 1 AND m.deleted = b'0' AND (m.id = @gym_root OR m.parent_id = @gym_root)
  AND m.permission NOT IN (
      'gym:accounts:write',
      'gym:roles:read', 'gym:roles:write',
      'gym:payroll:templates',
      'gym:blacklists:read', 'gym:blacklists:write',
      'gym:settings:manage',
      'gym:contracts:manage', 'gym:contracts:templates',
      'gym:approvals:templates',
      'gym:content:manage', 'gym:activities:manage', 'gym:franchise:manage',
      'gym:payment:read', 'gym:payment:write',
      'gym:settlement:read', 'gym:settlement:manage'
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = @store_role AND rm.menu_id = m.id AND rm.deleted = b'0' AND rm.tenant_id = @tenant_id
  );

-- -----------------------------------------------------------------------------
-- 6. 演示账号（密码统一 Gym@123456）
--    gymadmin  总部管理员 -> gym_hq_admin，部门：健身总部
--    gymstore  门店店长   -> gym_store_manager，部门：示范门店
-- -----------------------------------------------------------------------------
INSERT INTO system_users (username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar,
                          status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'gymadmin', @seed_pwd, '健身总部管理员', 'JSS 健身演示账号 · 总部管理员', @gym_hq_dept, NULL,
       'gymadmin@jss.local', '13800000001', 1, '', 0, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_users WHERE username = 'gymadmin' AND deleted = b'0' AND tenant_id = @tenant_id);

INSERT INTO system_users (username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar,
                          status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'gymstore', @seed_pwd, '示范门店店长', 'JSS 健身演示账号 · 门店店长', @gym_store_dept, NULL,
       'gymstore@jss.local', '13800000002', 1, '', 0, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_users WHERE username = 'gymstore' AND deleted = b'0' AND tenant_id = @tenant_id);

SET @hq_user    = (SELECT id FROM system_users WHERE username = 'gymadmin' AND deleted = b'0' AND tenant_id = @tenant_id ORDER BY id LIMIT 1);
SET @store_user = (SELECT id FROM system_users WHERE username = 'gymstore' AND deleted = b'0' AND tenant_id = @tenant_id ORDER BY id LIMIT 1);

INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @hq_user, @hq_role, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE @gym_new_hq_user = 1 AND NOT EXISTS (SELECT 1 FROM system_user_role WHERE user_id = @hq_user AND role_id = @hq_role AND deleted = b'0' AND tenant_id = @tenant_id);

INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @store_user, @store_role, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE @gym_new_store_user = 1 AND NOT EXISTS (SELECT 1 FROM system_user_role WHERE user_id = @store_user AND role_id = @store_role AND deleted = b'0' AND tenant_id = @tenant_id);


-- Initial development grants: 04-gym-first-batch-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-first-batch', NOW(), 'gym-first-batch', NOW(), b'0', r.tenant_id
 FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('stores', 'employees') AND m.type = 2 AND m.deleted = b'0'
 WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin', 'gym_store_manager') AND r.deleted = b'0'
 AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 06-gym-salary-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-salary', NOW(), 'gym-salary', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('salary-templates','employee-salary-assignments') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND (m.path = 'employee-salary-assignments' OR r.code = 'gym_hq_admin')
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 08-gym-member-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-member', NOW(), 'gym-member', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('members') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 10-gym-member-records-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-member-records', NOW(), 'gym-member-records', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('body-measurements','blacklists') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 12-gym-card-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-cards', NOW(), 'gym-cards', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('cards') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 14-gym-private-course-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-private-courses', NOW(), 'gym-private-courses', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('checkins','course-products','course-sessions','bookings','lesson-records') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 17-gym-approval-template-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-approval-templates', NOW(), 'gym-approval-templates', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('approval-templates') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 19-gym-approval-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-approvals', NOW(), 'gym-approvals', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('approvals') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 21-gym-contract-template-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-contract-templates', NOW(), 'gym-contract-templates', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('contract-templates') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 23-gym-contract-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-contracts', NOW(), 'gym-contracts', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('contracts') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 25-gym-payroll-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-payrolls', NOW(), 'gym-payrolls', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('payrolls') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 26-gym-report-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-reports', NOW(), 'gym-reports', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('home','reports/performance','reports/members') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 28-gym-sales-settings-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-sales-settings', NOW(), 'gym-sales-settings', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('settings') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 30-gym-settlement-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-settlements', NOW(), 'gym-settlements', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('payment-accounts','settlement-rules','settlements') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 32-gym-marketing-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-marketing', NOW(), 'gym-marketing', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('activities','franchise-inquiries') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 34-gym-devices-equipment-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-devices-equipment', NOW(), 'gym-devices-equipment', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('devices','equipment') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 36-gym-client-content-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-client-content', NOW(), 'gym-client-content', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('client-settings','qr-codes') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND (r.code='gym_hq_admin' OR m.path='qr-codes') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 38-gym-sales-records-menus.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-sales-records', NOW(), 'gym-sales-records', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('self-service-card-orders','subscriptions','verifications') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 43-gym-card-report-menu.sql
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'gym-card-report', NOW(), 'gym-card-report', NOW(), b'0', r.tenant_id
FROM system_role r JOIN system_menu m ON m.parent_id = @gym_root AND m.path IN ('reports/member-cards') AND m.type = 2 AND m.deleted = b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code IN ('gym_hq_admin','gym_store_manager') AND r.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');

-- Initial development grants: 47-gym-blacklist-write-permission.sql
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT r.id,m.id,'gym-permission-align',NOW(),'gym-permission-align',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.permission='gym:blacklist:write' AND m.deleted=b'0'
WHERE ((r.code='gym_hq_admin' AND @gym_new_hq_role=1) OR (r.code='gym_store_manager' AND @gym_new_store_role=1)) AND r.tenant_id = @tenant_id AND r.code='gym_hq_admin' AND r.data_scope=1 AND r.status=0 AND r.deleted=b'0'
 AND EXISTS (SELECT 1 FROM system_role_menu old_grant JOIN system_menu legacy ON legacy.id=old_grant.menu_id
  WHERE old_grant.role_id=r.id AND old_grant.tenant_id=r.tenant_id AND old_grant.deleted=b'0'
  AND legacy.permission='gym:blacklists:write' AND legacy.deleted=b'0')
 AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id=r.id AND existing.menu_id=m.id
  AND existing.tenant_id=r.tenant_id AND existing.deleted=b'0');

COMMIT;
DROP TEMPORARY TABLE gym_demo_guard;
