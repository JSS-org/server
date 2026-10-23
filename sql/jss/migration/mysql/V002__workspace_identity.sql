-- MySQL 8 / InnoDB. Additive migration; run after the system and member schemas.
-- No users, passwords, bindings or role grants are created automatically.
CREATE TABLE IF NOT EXISTS jss_user_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    member_user_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 enabled, 1 revoked',
    binding_version BIGINT NOT NULL DEFAULT 1,
    bound_by BIGINT NOT NULL,
    bound_at DATETIME NOT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_jss_member (tenant_id, member_user_id),
    active_admin_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 0 AND deleted = b'0' THEN admin_user_id ELSE NULL END) STORED,
    UNIQUE KEY uk_jss_active_admin (tenant_id, active_admin_user_id),
    CONSTRAINT ck_jss_identity_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_jss_identity_version CHECK (binding_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS jss_identity_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    identity_id BIGINT NOT NULL,
    member_user_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    binding_version BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_jss_audit_identity (tenant_id, identity_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Permission nodes are hidden buttons. Assign to reviewed roles using the existing role UI.
-- Tenant packages must include these nodes before tenant roles can receive them.
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT 'JSS 工作台访问', 'jss:workspace:access', 3, 1, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jss:workspace:access' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT 'JSS 绑定查询', 'jss:identity:query', 3, 2, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jss:identity:query' AND deleted=b'0');
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, status, visible)
SELECT 'JSS 绑定审核', 'jss:identity:bind', 3, 3, 0, '', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='jss:identity:bind' AND deleted=b'0');
