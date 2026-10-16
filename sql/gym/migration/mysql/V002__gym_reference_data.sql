-- Required global menu/permission definitions only. Never grants roles or creates users.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @seed_user = 'gym-seed';

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



-- Source: 04-gym-first-batch-menus.sql

-- 基于当前 dashboard/apps/web-jss/integration/menus.json，仅注册已交付的两个页面。
-- 共享菜单；角色授权仅租户 1 的 gym_hq_admin / gym_store_manager，其他角色沿用 system 后台配置。
-- 先运行 01；本脚本幂等。仅修正 gym-seed 自建 read 按钮，不覆盖人工菜单。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
UPDATE system_menu SET path = 'gym', updater = 'gym-first-batch', update_time = NOW()
 WHERE id = @gym_root AND creator = 'gym-seed' AND path = '/gym';
UPDATE system_menu SET name = '门店资料', type = 2, path = 'stores', component = 'gym/stores/index',
 component_name = 'GymStores', parent_id = @gym_root, updater = 'gym-first-batch', update_time = NOW()
 WHERE permission = 'gym:stores:read' AND type = 3 AND creator = 'gym-seed' AND deleted = b'0';
UPDATE system_menu SET name = '员工资料', type = 2, path = 'employees', component = 'gym/employees/index',
 component_name = 'GymEmployees', parent_id = @gym_root, updater = 'gym-first-batch', update_time = NOW()
 WHERE permission = 'gym:employees:read' AND type = 3 AND creator = 'gym-seed' AND deleted = b'0';
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:office-building', t.component, t.component_name,
 0, b'1', b'1', b'1', 'gym-first-batch', NOW(), 'gym-first-batch', NOW(), b'0'
FROM (
 SELECT '门店资料' name, 'gym:stores:read' permission, 2 sort, 'stores' path, 'gym/stores/index' component, 'GymStores' component_name
 UNION ALL SELECT '员工资料', 'gym:employees:read', 5, 'employees', 'gym/employees/index', 'GymEmployees'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (
 SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0'
);


-- Source: 06-gym-salary-menus.sql

-- 只上线本批已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-salary', NOW(), 'gym-salary', NOW(), b'0'
FROM (
 SELECT '薪资模板' name, 'gym:payroll:templates' permission, 6 sort, 'salary-templates' path, 'Gym-salary-templates' component_name
 UNION ALL SELECT '员工薪资关联', 'gym:payroll:read', 7, 'employee-salary-assignments', 'Gym-employee-salary-assignments'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 08-gym-member-menus.sql

-- 只上线会员档案基础页面（权益/私教联调仍待完成）；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-member', NOW(), 'gym-member', NOW(), b'0'
FROM (
 SELECT '会员资料' name, 'gym:members:read' permission, 9 sort, 'members' path, 'GymMembers' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 10-gym-member-records-menus.sql

-- 只上线黑名单与体测页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-member-records', NOW(), 'gym-member-records', NOW(), b'0'
FROM (
 SELECT '体测记录' name, 'gym:measurements:read' permission, 10 sort, 'body-measurements' path, 'Gym-body-measurements' component_name
 UNION ALL SELECT '黑名单', 'gym:blacklist:read', 11, 'blacklists', 'Gym-blacklists'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 12-gym-card-menus.sql

-- 只上线gym-cards 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-cards', NOW(), 'gym-cards', NOW(), b'0'
FROM (
 SELECT '会员卡模板' name, 'gym:cards:read' permission, 12 sort, 'cards' path, 'GymCards' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 14-gym-private-course-menus.sql

-- 只上线gym-private-courses 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-private-courses', NOW(), 'gym-private-courses', NOW(), b'0'
FROM (
 SELECT '签到记录' name, 'gym:checkins:read' permission, 3 sort, 'checkins' path, 'Gym-checkins' component_name
 UNION ALL SELECT '课程产品', 'gym:courses:read', 17, 'course-products', 'Gym-course-products'
 UNION ALL SELECT '课程安排', 'gym:courses:read', 18, 'course-sessions', 'Gym-course-sessions'
 UNION ALL SELECT '预约管理', 'gym:bookings:read', 19, 'bookings', 'Gym-bookings'
 UNION ALL SELECT '上课记录', 'gym:lessons:read', 20, 'lesson-records', 'Gym-lesson-records'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 17-gym-approval-template-menus.sql

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


-- Source: 19-gym-approval-menus.sql

-- 只上线gym-approvals 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-approvals', NOW(), 'gym-approvals', NOW(), b'0'
FROM (
 SELECT '审批记录' name, 'gym:approvals:read' permission, 18 sort, 'approvals' path, 'GymApprovals' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 21-gym-contract-template-menus.sql

-- 只上线gym-contract-templates 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-contract-templates', NOW(), 'gym-contract-templates', NOW(), b'0'
FROM (
 SELECT '合同模板' name, 'gym:contracts:templates' permission, 19 sort, 'contract-templates' path, 'GymContractTemplates' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 23-gym-contract-menus.sql

-- 只上线gym-contracts 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-contracts', NOW(), 'gym-contracts', NOW(), b'0'
FROM (
 SELECT '合同记录' name, 'gym:contracts:read' permission, 20 sort, 'contracts' path, 'GymContracts' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 25-gym-payroll-menus.sql

-- 只上线gym-payrolls 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-payrolls', NOW(), 'gym-payrolls', NOW(), b'0'
FROM (
 SELECT '门店工资表' name, 'gym:payroll:read' permission, 21 sort, 'payrolls' path, 'GymPayrolls' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 26-gym-report-menus.sql

-- 只上线已实现的工作台、业绩和会员汇总页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-reports', NOW(), 'gym-reports', NOW(), b'0'
FROM (
 SELECT '门店工作台' name, 'gym:reports:read' permission, 1 sort, 'home' path, 'GymHome' component_name
 UNION ALL SELECT '门店与员工业绩', 'gym:reports:read', 33, 'reports/performance', 'Gym-reports/performance'
 UNION ALL SELECT '会员汇总', 'gym:reports:read', 34, 'reports/members', 'Gym-reports/members'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 28-gym-sales-settings-menus.sql

-- 只上线gym-sales-settings 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-sales-settings', NOW(), 'gym-sales-settings', NOW(), b'0'
FROM (
 SELECT '售卡与核销设置' name, 'gym:sales:settings' permission, 22 sort, 'settings' path, 'GymSettings' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 30-gym-settlement-menus.sql

-- 只上线gym-settlements 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-settlements', NOW(), 'gym-settlements', NOW(), b'0'
FROM (
 SELECT '收款账户' name, 'gym:settlement:manage' permission, 30 sort, 'payment-accounts' path, 'Gym-payment-accounts' component_name
 UNION ALL SELECT '分账规则','gym:settlement:manage',31,'settlement-rules','Gym-settlement-rules'
 UNION ALL SELECT '分账结算记录','gym:settlement:read',32,'settlements','Gym-settlements'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 32-gym-marketing-menus.sql

-- 只上线gym-marketing 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-marketing', NOW(), 'gym-marketing', NOW(), b'0'
FROM (
 SELECT '门店活动' name, 'gym:activities:manage' permission, 28 sort, 'activities' path, 'Gym-activities' component_name
 UNION ALL SELECT '加盟咨询','gym:franchise:manage',29,'franchise-inquiries','Gym-franchise-inquiries'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 34-gym-devices-equipment-menus.sql

-- 只上线gym-devices-equipment 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-devices-equipment', NOW(), 'gym-devices-equipment', NOW(), b'0'
FROM (
 SELECT '智能设备' name, 'gym:devices:read' permission, 25 sort, 'devices' path, 'Gym-devices' component_name
 UNION ALL SELECT '器械教学','gym:equipment:read',26,'equipment','Gym-equipment'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 36-gym-client-content-menus.sql

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


-- Source: 38-gym-sales-records-menus.sql

-- 只上线gym-sales-records 已实现页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-sales-records', NOW(), 'gym-sales-records', NOW(), b'0'
FROM (
 SELECT '自主办卡记录' name, 'gym:sales:read' permission, 29 sort, 'self-service-card-orders' path, 'Gym-self-service-card-orders' component_name
 UNION ALL SELECT '连续包月记录','gym:sales:read',30,'subscriptions','Gym-subscriptions'
 UNION ALL SELECT '自助核销记录','gym:sales:read',31,'verifications','Gym-verifications'
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 43-gym-card-report-menu.sql

-- 上线已实现的会员卡报表页面；不覆盖人工菜单，也不扩展旧开发角色权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @gym_root = (SELECT id FROM system_menu WHERE name = '健身管理' AND parent_id = 0 AND deleted = b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.name, t.permission, 2, t.sort, @gym_root, t.path, 'ep:money', CONCAT('gym/',t.path,'/index'), t.component_name,
 0, b'1', b'1', b'1', 'gym-card-report', NOW(), 'gym-card-report', NOW(), b'0'
FROM (
 SELECT '会员卡报表' name, 'gym:reports:read' permission, 35 sort, 'reports/member-cards' path, 'Gym-reports/member-cards' component_name
) t
WHERE @gym_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.parent_id = @gym_root AND m.path = t.path AND m.deleted = b'0');


-- Source: 47-gym-blacklist-write-permission.sql

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
