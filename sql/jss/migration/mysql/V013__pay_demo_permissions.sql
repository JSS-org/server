-- Independent demo capabilities. No role, user, identity or tenant-package grants.
-- Include reviewed nodes in the tenant package, then assign only the required actions via RBAC.
-- Existing active nodes (including disabled ones) are never changed or re-enabled.
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '支付示例订单查询', 'pay:demo-order:query', 3, 1, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:demo-order:query' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '支付示例订单创建', 'pay:demo-order:create', 3, 2, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:demo-order:create' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '支付示例订单退款', 'pay:demo-order:refund', 3, 3, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:demo-order:refund' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '支付示例提现查询', 'pay:demo-withdraw:query', 3, 4, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:demo-withdraw:query' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '支付示例提现创建', 'pay:demo-withdraw:create', 3, 5, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:demo-withdraw:create' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '支付示例提现转账', 'pay:demo-withdraw:transfer', 3, 6, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:demo-withdraw:transfer' AND deleted=b'0');
