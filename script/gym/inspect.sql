-- =============================================================================
-- JSS 健身 · gym 数据排查 SQL（可在任意 MySQL 客户端直接打开执行）
--
-- 用途：核对「§4.1 认证切片 + 文件切片」落库的数据是否符合预期，
--       以及排查权限/门店越权/文件存储等问题。
--
-- 连接信息：127.0.0.1:3306 / ruoyi-vue-pro / yudao / 123456
--   （不要用 root：本机 root 为免密 socket 登录，GUI 客户端走 TCP 连不上）
--
-- 使用：Sequel Ace / DBeaver / PyCharm Database → File → Open 本文件 → 选中执行
--       命令行：mysql -uyudao -p123456 ruoyi-vue-pro < script/gym/inspect.sql
--
-- 对应实现见 doc/CHANGELOG.md「认证切片」「文件切片」两条记录。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① gym 权限码注册表（期望 51 行）
-- -----------------------------------------------------------------------------
SELECT id, sort, name, permission, parent_id
FROM system_menu
WHERE creator = 'gym-seed' AND type = 3 AND deleted = b'0'
ORDER BY sort;

-- -----------------------------------------------------------------------------
-- ② 角色与授权规模（期望：总部 52 = 根目录+51 码；门店 36 = 根目录+35 码）
-- -----------------------------------------------------------------------------
SELECT r.id, r.code, r.name, r.data_scope,
       CASE r.data_scope WHEN 1 THEN '全部' WHEN 4 THEN '本部门及以下' ELSE r.data_scope END AS scope_desc,
       COUNT(*) AS menu_count
FROM system_role r
JOIN system_role_menu rm ON rm.role_id = r.id AND rm.deleted = b'0'
JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = b'0'
WHERE r.code IN ('gym_hq_admin', 'gym_store_manager') AND r.deleted = b'0'
GROUP BY r.id, r.code, r.name, r.data_scope;

-- -----------------------------------------------------------------------------
-- ③ 门店（部门）与演示账号
-- -----------------------------------------------------------------------------
SELECT id, name, parent_id, sort FROM system_dept
WHERE creator = 'gym-seed' AND deleted = b'0' ORDER BY parent_id, sort;

SELECT u.id, u.username, u.nickname, u.status, u.tenant_id, d.name AS store
FROM system_users u
LEFT JOIN system_dept d ON d.id = u.dept_id
WHERE u.username IN ('gymadmin', 'gymstore') AND u.deleted = b'0';

-- -----------------------------------------------------------------------------
-- ④ 手工模拟 GET /system/auth/get-permission-info：某账号最终拿到的权限码
--    期望：gymadmin = 51；gymstore = 35（不含仅总部的 16 个码）
-- -----------------------------------------------------------------------------
SELECT u.username, COUNT(DISTINCT m.permission) AS permission_count
FROM system_users u
JOIN system_user_role ur ON ur.user_id = u.id AND ur.deleted = b'0'
JOIN system_role r ON r.id = ur.role_id AND r.deleted = b'0' AND r.status = 0
JOIN system_role_menu rm ON rm.role_id = r.id AND rm.deleted = b'0'
JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.status = 0
WHERE u.username IN ('gymadmin', 'gymstore') AND m.permission <> ''
GROUP BY u.username;

-- 明细（改 username 看不同角色）
SELECT DISTINCT m.permission
FROM system_users u
JOIN system_user_role ur ON ur.user_id = u.id AND ur.deleted = b'0'
JOIN system_role r ON r.id = ur.role_id AND r.deleted = b'0' AND r.status = 0
JOIN system_role_menu rm ON rm.role_id = r.id AND rm.deleted = b'0'
JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.status = 0
WHERE u.username = 'gymstore' AND m.permission <> ''
ORDER BY m.permission;

-- -----------------------------------------------------------------------------
-- ⑤ gym 私有文件（归属门店 + 上传人 + 类别 + 大小）
-- -----------------------------------------------------------------------------
SELECT f.id, f.category, f.name, f.mime_type, f.size,
       d.name AS store, u.username AS uploader, f.tenant_id, f.create_time
FROM gym_file f
LEFT JOIN system_dept d ON d.id = f.store_id
LEFT JOIN system_users u ON u.id = f.uploader_id
WHERE f.deleted = b'0'
ORDER BY f.id DESC
LIMIT 50;

-- 按类别/门店汇总
SELECT f.category, d.name AS store, COUNT(*) AS file_count, SUM(f.size) AS total_bytes
FROM gym_file f LEFT JOIN system_dept d ON d.id = f.store_id
WHERE f.deleted = b'0'
GROUP BY f.category, d.name ORDER BY f.category, d.name;

-- -----------------------------------------------------------------------------
-- ⑥ 私有文件的真实存储位置（url/path 属于存储凭证，接口刻意不返回给前端）
-- -----------------------------------------------------------------------------
SELECT f.id AS gym_file_id, f.name, i.id AS infra_file_id, i.config_id, i.path, i.url
FROM gym_file f
JOIN infra_file i ON i.url = f.url
WHERE f.deleted = b'0'
ORDER BY f.id DESC
LIMIT 20;

-- -----------------------------------------------------------------------------
-- ⑦ 缺陷排查：同名同天上传会共用同一存储路径（后一次覆盖前一次）
--    期望（修复前）：能看到 infra_rows > 1 的行
-- -----------------------------------------------------------------------------
SELECT i.path, COUNT(*) AS infra_rows, GROUP_CONCAT(i.id ORDER BY i.id) AS infra_file_ids
FROM infra_file i
WHERE i.path LIKE 'gym/%'
GROUP BY i.path
HAVING COUNT(*) > 1
ORDER BY infra_rows DESC;

-- -----------------------------------------------------------------------------
-- ⑧ 种子数据总览（一条 SQL 看全貌；重跑 seed.sh 后应保持不变）
-- -----------------------------------------------------------------------------
SELECT '① gym 权限码' AS item, COUNT(*) AS cnt FROM system_menu
 WHERE creator = 'gym-seed' AND type = 3 AND deleted = b'0'
UNION ALL SELECT '② 角色', COUNT(*) FROM system_role
 WHERE code IN ('gym_hq_admin','gym_store_manager') AND deleted = b'0'
UNION ALL SELECT '③ 演示账号', COUNT(*) FROM system_users
 WHERE username IN ('gymadmin','gymstore') AND deleted = b'0'
UNION ALL SELECT '④ 门店部门', COUNT(*) FROM system_dept
 WHERE creator = 'gym-seed' AND deleted = b'0'
UNION ALL SELECT '⑤ 角色授权行', COUNT(*) FROM system_role_menu
 WHERE creator = 'gym-seed' AND deleted = b'0'
UNION ALL SELECT '⑥ gym_file 行', COUNT(*) FROM gym_file WHERE deleted = b'0';
