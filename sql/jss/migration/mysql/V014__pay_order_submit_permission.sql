-- Payment submission is an independent action; no role or package grants.
-- Preserve existing custom/disabled nodes and soft-deleted history.
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT '提交支付订单', 'pay:order:submit', 3, 7, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pay:order:submit' AND deleted=b'0');
