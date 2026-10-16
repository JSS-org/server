-- =============================================================================
-- JSS 健身 · gym 认证切片种子（幂等，可重复执行）
-- -----------------------------------------------------------------------------
-- 对应规划：admin/target/plan/09-permissions.md §2 权限码注册表、§4 角色与数据范围；
--           admin/target/plan/10-backend-mock-api-inventory.md §4.1 前 5 个接口
--           （login / logout / refresh / codes / user info）
--
-- 作用：这 5 个接口由 yudao 原生提供（/admin-api/system/auth/*、/admin-api/system/user/profile/get），
--       本脚本负责把它们在「健身业务」下落地：注册 gym:<实体>:<动作> 权限码、建立总部/门店角色与演示账号，
--       使 GET /admin-api/system/auth/get-permission-info 能返回 gym:* 权限码，供前端 canGym(...) 使用。
--
-- 幂等策略：全部以自然键 + NOT EXISTS 判重，所有种子行 creator='gym-seed' 便于识别与清理。
-- 表特点：system_menu 为租户共享表（无 tenant_id）；system_role / system_users / system_dept /
--         system_role_menu / system_user_role 均带 tenant_id，默认写入 1 号租户。
--
-- 用法：mysql -h127.0.0.1 -P3306 -uyudao -p ruoyi-vue-pro < 01-gym-permission-seed.sql
--       或直接执行 script/gym/seed.sh
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci; -- yudao 表列为 utf8mb4_unicode_ci，统一连接排序规则，避免字面量比较报 1267

SET @tenant_id  = 1;                    -- 目标租户编号，可用 -e "SET @tenant_id=2;" 前置覆盖
SET @seed_user  = 'gym-seed';           -- 种子标记（creator/updater）
-- 演示账号统一密码：Gym@123456（BCrypt，强度 10）
SET @seed_pwd   = '$2a$10$DobR27ZollT9tVNGf2.FM.3A2S8Azi9IJbzYic6g660D6ta8LS8G2';

-- -----------------------------------------------------------------------------
-- 1. gym 权限码根目录（目录类型，后续按 09-permissions.md §3 挂载业务菜单）
-- -----------------------------------------------------------------------------
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '健身管理', '', 1, 90, 0, '/gym', 'ep:basketball', NULL, NULL,
       0, b'1', b'1', b'1', @seed_user, NOW(), @seed_user, NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0'
);

SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);

-- -----------------------------------------------------------------------------
-- 2. gym 权限码注册表（09-permissions.md §2，共 51 个码，按钮类型不产生路由）
--    按钮（type=3）只贡献 permission 字段：get-permission-info 会聚合返回，且不进菜单树。
-- -----------------------------------------------------------------------------
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 3, t.sort, @gym_root, '', '#', NULL, NULL,
       0, b'1', b'1', b'1', @seed_user, NOW(), @seed_user, NOW(), b'0'
FROM (
    -- 组织与账号
    SELECT  1 AS sort, '门店-查询'         AS name, 'gym:stores:read'          AS permission UNION ALL
    SELECT  2, '门店-编辑',                       'gym:stores:write'                  UNION ALL
    SELECT  3, '员工-查询',                       'gym:employees:read'                UNION ALL
    SELECT  4, '员工-编辑',                       'gym:employees:write'               UNION ALL
    SELECT  5, '员工账号-授权',                   'gym:accounts:write'                UNION ALL
    SELECT  6, '角色-查询',                       'gym:roles:read'                    UNION ALL
    SELECT  7, '角色-编辑',                       'gym:roles:write'                   UNION ALL
    -- 会员与卡
    SELECT  8, '会员-查询',                       'gym:members:read'                  UNION ALL
    SELECT  9, '会员-编辑',                       'gym:members:write'                 UNION ALL
    SELECT 10, '体测-查询',                       'gym:measurements:read'             UNION ALL
    SELECT 11, '体测-编辑',                       'gym:measurements:write'            UNION ALL
    SELECT 12, '卡种-查询',                       'gym:cards:read'                    UNION ALL
    SELECT 13, '卡种-编辑',                       'gym:cards:write'                   UNION ALL
    SELECT 14, '黑名单-查询',                     'gym:blacklists:read'               UNION ALL
    SELECT 15, '黑名单-编辑',                     'gym:blacklists:write'              UNION ALL
    -- 课程与到店
    SELECT 16, '课程产品-查询',                   'gym:courses:read'                  UNION ALL
    SELECT 17, '课程产品-编辑',                   'gym:courses:write'                 UNION ALL
    SELECT 18, '预约-查询',                       'gym:bookings:read'                 UNION ALL
    SELECT 19, '预约-编辑',                       'gym:bookings:write'                UNION ALL
    SELECT 20, '签到-查询',                       'gym:checkins:read'                 UNION ALL
    -- 合同与审批
    SELECT 21, '合同-查询',                       'gym:contracts:read'                UNION ALL
    SELECT 22, '合同模板-管理',                   'gym:contracts:manage'              UNION ALL
    SELECT 23, '合同-提醒',                       'gym:contracts:remind'              UNION ALL
    SELECT 24, '审批-查询',                       'gym:approvals:read'                UNION ALL
    SELECT 25, '审批-发起',                       'gym:approvals:create'              UNION ALL
    SELECT 26, '审批-决定',                       'gym:approvals:decide'              UNION ALL
    SELECT 27, '审批模板-管理',                   'gym:approvals:templates'           UNION ALL
    -- 设备与器械
    SELECT 28, '设备-查询',                       'gym:devices:read'                  UNION ALL
    SELECT 29, '设备-编辑',                       'gym:devices:write'                 UNION ALL
    SELECT 30, '设备-远程指令',                   'gym:devices:command'               UNION ALL
    SELECT 31, '器械教学-查询',                   'gym:equipment:read'                UNION ALL
    SELECT 32, '器械教学-编辑',                   'gym:equipment:write'               UNION ALL
    SELECT 33, '渠道码-查询',                     'gym:qr:read'                       UNION ALL
    -- 销售与财务
    SELECT 34, '办卡记录-查询',                   'gym:sales:read'                    UNION ALL
    SELECT 35, '连续包月-管理',                   'gym:subscriptions:manage'          UNION ALL
    SELECT 36, '自助核销-管理',                   'gym:verifications:manage'          UNION ALL
    SELECT 37, '收款账户-查询',                   'gym:payment:read'                  UNION ALL
    SELECT 38, '收款账户-编辑',                   'gym:payment:write'                 UNION ALL
    SELECT 39, '分账-查询',                       'gym:settlement:read'               UNION ALL
    SELECT 40, '分账-管理',                       'gym:settlement:manage'             UNION ALL
    -- 薪资
    SELECT 41, '工资表-查询',                     'gym:payroll:read'                  UNION ALL
    SELECT 42, '薪资模板-管理',                   'gym:payroll:templates'             UNION ALL
    SELECT 43, '工资表-计算',                     'gym:payroll:calculate'             UNION ALL
    SELECT 44, '工资表-确认',                     'gym:payroll:confirm'               UNION ALL
    -- 内容 / 活动 / 加盟 / 报表 / 文件 / 设置
    SELECT 45, '小程序展示配置-管理',             'gym:content:manage'                UNION ALL
    SELECT 46, '门店活动-管理',                   'gym:activities:manage'             UNION ALL
    SELECT 47, '加盟-管理',                       'gym:franchise:manage'              UNION ALL
    SELECT 48, '报表-查询',                       'gym:reports:read'                  UNION ALL
    SELECT 49, '文件-查询',                       'gym:files:read'                    UNION ALL
    SELECT 50, '文件-上传',                       'gym:files:upload'                  UNION ALL
    SELECT 51, '售卡与核销设置-管理',             'gym:settings:manage'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu m WHERE m.permission = t.permission AND m.deleted = b'0'
);

-- -----------------------------------------------------------------------------
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
WHERE m.deleted = b'0' AND (m.id = @gym_root OR m.parent_id = @gym_root)
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = @hq_role AND rm.menu_id = m.id AND rm.deleted = b'0' AND rm.tenant_id = @tenant_id
  );

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @store_role, m.id, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
FROM system_menu m
WHERE m.deleted = b'0' AND (m.id = @gym_root OR m.parent_id = @gym_root)
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
WHERE NOT EXISTS (SELECT 1 FROM system_user_role WHERE user_id = @hq_user AND role_id = @hq_role AND deleted = b'0' AND tenant_id = @tenant_id);

INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @store_user, @store_role, @seed_user, NOW(), @seed_user, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (SELECT 1 FROM system_user_role WHERE user_id = @store_user AND role_id = @store_role AND deleted = b'0' AND tenant_id = @tenant_id);

-- -----------------------------------------------------------------------------
-- 7. 结果自检（幂等：重复执行前后数值应保持一致）
-- -----------------------------------------------------------------------------
SELECT 'gym 权限码' AS item, COUNT(*) AS cnt FROM system_menu
 WHERE creator = @seed_user AND type = 3 AND deleted = b'0'
UNION ALL
SELECT '总部角色授权菜单', COUNT(*) FROM system_role_menu WHERE role_id = @hq_role AND deleted = b'0'
UNION ALL
SELECT '门店角色授权菜单', COUNT(*) FROM system_role_menu WHERE role_id = @store_role AND deleted = b'0'
UNION ALL
SELECT '演示账号', COUNT(*) FROM system_users
 WHERE username IN ('gymadmin', 'gymstore') AND deleted = b'0' AND tenant_id = @tenant_id;
