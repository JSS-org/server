-- Development only. @tenant_id is supplied by the explicit seed command.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @seed_user = 'gym-seed';
SET @seed_pwd = '$2a$10$DobR27ZollT9tVNGf2.FM.3A2S8Azi9IJbzYic6g660D6ta8LS8G2';
SET @gym_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=0 ORDER BY id LIMIT 1);
CREATE TEMPORARY TABLE gym_demo_guard (ok int NOT NULL CHECK(ok=1));
CREATE TEMPORARY TABLE gym_demo_menus (id bigint PRIMARY KEY);
START TRANSACTION;
INSERT INTO gym_demo_guard SELECT IF(@tenant_id>0 AND @gym_root IS NOT NULL,1,0);
INSERT INTO gym_demo_menus
WITH RECURSIVE menus AS (
 SELECT id FROM system_menu WHERE id=@gym_root AND deleted=0
 UNION ALL SELECT m.id FROM system_menu m JOIN menus p ON m.parent_id=p.id WHERE m.deleted=0
) SELECT id FROM menus;
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@tenant_id AND code IN ('gym_hq_admin','gym_store_manager') AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND username IN ('gymadmin','gymstore') AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_dept WHERE tenant_id=@tenant_id AND name='健身总部' AND parent_id=0 AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
SET @gym_new_hq_role = COALESCE(@gym_sync_demo_access,0)=1 OR NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@tenant_id AND code='gym_hq_admin');
SET @gym_new_store_role = COALESCE(@gym_sync_demo_access,0)=1 OR NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@tenant_id AND code='gym_store_manager');
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
FROM system_menu m JOIN gym_demo_menus gm ON gm.id=m.id
WHERE @gym_new_hq_role = 1 AND m.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = @hq_role AND rm.menu_id = m.id AND rm.deleted = b'0' AND rm.tenant_id = @tenant_id
  );

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @store_role, m.id, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
FROM system_menu m JOIN gym_demo_menus gm ON gm.id=m.id
WHERE @gym_new_store_role = 1 AND m.deleted = b'0'
  AND m.permission NOT IN (
      'gym:accounts:write',
      'gym:roles:read', 'gym:roles:write',
      'gym:payroll:templates',
      'gym:blacklists:read', 'gym:blacklists:write', 'gym:blacklist:write',
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


-- BUSINESS_FIXTURES

COMMIT;
DROP TEMPORARY TABLE gym_demo_guard;
DROP TEMPORARY TABLE gym_demo_menus;
