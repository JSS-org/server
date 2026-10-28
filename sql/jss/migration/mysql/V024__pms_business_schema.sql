-- Complete PMS native persistent outer DO fields.
-- Jackson3 fields retain JSON; LongListTypeHandler retains native CSV LONGTEXT.

CREATE TABLE `pms_knowledge_content_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `open_status` BIT(1) NULL,
    `open_level` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_content_permission_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `permission_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `dept_id` BIGINT NULL,
    `level` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_permission_id` (`tenant_id`, `permission_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`),
    KEY `idx_dept_id` (`tenant_id`, `dept_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `permission_id` BIGINT NULL,
    `folder_id` BIGINT NULL,
    `parent_id` BIGINT NULL,
    `title` LONGTEXT NULL,
    `content` LONGTEXT NULL,
    `type` INT NULL,
    `file_type` LONGTEXT NULL,
    `file_size` BIGINT NULL,
    `status` INT NULL,
    `label_ids` LONGTEXT NULL,
    `delete_user_id` BIGINT NULL,
    `delete_time` DATETIME(3) NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`),
    KEY `idx_permission_id` (`tenant_id`, `permission_id`, `deleted`),
    KEY `idx_folder_id` (`tenant_id`, `folder_id`, `deleted`),
    KEY `idx_parent_id` (`tenant_id`, `parent_id`, `deleted`),
    KEY `idx_file_size` (`tenant_id`, `file_size`, `deleted`),
    KEY `idx_delete_user_id` (`tenant_id`, `delete_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_document_label` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `color` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `permission_id` BIGINT NULL,
    `parent_id` BIGINT NULL,
    `title` LONGTEXT NULL,
    `status` INT NULL,
    `delete_user_id` BIGINT NULL,
    `delete_time` DATETIME(3) NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`),
    KEY `idx_permission_id` (`tenant_id`, `permission_id`, `deleted`),
    KEY `idx_parent_id` (`tenant_id`, `parent_id`, `deleted`),
    KEY `idx_delete_user_id` (`tenant_id`, `delete_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_document_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `main_id` BIGINT NULL,
    `reply_user_id` BIGINT NULL,
    `content` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_document_id` (`tenant_id`, `document_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`),
    KEY `idx_main_id` (`tenant_id`, `main_id`, `deleted`),
    KEY `idx_reply_user_id` (`tenant_id`, `reply_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_document_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_document_id` (`tenant_id`, `document_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_document_share` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT NULL,
    `share_user_ids` JSON NULL,
    `token` LONGTEXT NULL,
    `status` INT NULL,
    `close_user_id` BIGINT NULL,
    `close_time` DATETIME(3) NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_document_id` (`tenant_id`, `document_id`, `deleted`),
    KEY `idx_close_user_id` (`tenant_id`, `close_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `type` INT NULL,
    `entity_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`),
    KEY `idx_entity_id` (`tenant_id`, `entity_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_view_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `type` INT NULL,
    `entity_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`),
    KEY `idx_entity_id` (`tenant_id`, `entity_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `name` VARCHAR(255) NULL,
    `sort` INT NULL,
    `type` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_group_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `group_id` BIGINT NULL,
    `library_id` BIGINT NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`),
    KEY `idx_group_id` (`tenant_id`, `group_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_library` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `description` LONGTEXT NULL,
    `open_status` BIT(1) NULL,
    `cover_url` LONGTEXT NULL,
    `status` INT NULL,
    `delete_user_id` BIGINT NULL,
    `delete_time` DATETIME(3) NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_delete_user_id` (`tenant_id`, `delete_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_library_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `dept_id` BIGINT NULL,
    `level` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`),
    KEY `idx_dept_id` (`tenant_id`, `dept_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_library_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `description` LONGTEXT NULL,
    `cover_url` LONGTEXT NULL,
    `status` INT NULL,
    `sort` INT NULL,
    `documents` JSON NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_knowledge_recycle_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `library_id` BIGINT NULL,
    `type` INT NULL,
    `entity_id` BIGINT NULL,
    `name` VARCHAR(255) NULL,
    `delete_user_id` BIGINT NULL,
    `delete_time` DATETIME(3) NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_library_id` (`tenant_id`, `library_id`, `deleted`),
    KEY `idx_entity_id` (`tenant_id`, `entity_id`, `deleted`),
    KEY `idx_delete_user_id` (`tenant_id`, `delete_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_iteration` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `name` VARCHAR(255) NULL,
    `owner_user_id` BIGINT NULL,
    `status` INT NULL,
    `start_time` DATETIME(3) NULL,
    `end_time` DATETIME(3) NULL,
    `finish_time` DATETIME(3) NULL,
    `target` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_owner_user_id` (`tenant_id`, `owner_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project_announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `content` LONGTEXT NULL,
    `file_urls` JSON NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `status` INT NULL,
    `type` INT NULL,
    `level` INT NULL,
    `description` LONGTEXT NULL,
    `open_status` BIT(1) NULL,
    `icon` LONGTEXT NULL,
    `sort` INT NULL,
    `start_time` DATETIME(3) NULL,
    `end_time` DATETIME(3) NULL,
    `archive_time` DATETIME(3) NULL,
    `recycle_time` DATETIME(3) NULL,
    `access_time` DATETIME(3) NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `name` VARCHAR(255) NULL,
    `sort` INT NULL,
    `type` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project_group_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `group_id` BIGINT NULL,
    `project_id` BIGINT NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`),
    KEY `idx_group_id` (`tenant_id`, `group_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `level` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_project_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `description` LONGTEXT NULL,
    `project_type` INT NULL,
    `status` INT NULL,
    `sort` INT NULL,
    `item_types` JSON NULL,
    `statuses` JSON NULL,
    `boards` JSON NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `work_item_id` BIGINT NULL,
    `operator_user_id` BIGINT NULL,
    `content` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_work_item_id` (`tenant_id`, `work_item_id`, `deleted`),
    KEY `idx_operator_user_id` (`tenant_id`, `operator_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_board` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `work_item_type` INT NULL,
    `name` VARCHAR(255) NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_item_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `main_id` BIGINT NULL,
    `reply_user_id` BIGINT NULL,
    `content` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_work_item_id` (`tenant_id`, `work_item_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`),
    KEY `idx_main_id` (`tenant_id`, `main_id`, `deleted`),
    KEY `idx_reply_user_id` (`tenant_id`, `reply_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `type` INT NULL,
    `serial_number` INT NULL,
    `name` VARCHAR(255) NULL,
    `description` LONGTEXT NULL,
    `priority` INT NULL,
    `assignee_user_id` BIGINT NULL,
    `status_id` BIGINT NULL,
    `status` INT NULL,
    `lifecycle_status` INT NULL,
    `archive_time` DATETIME(3) NULL,
    `recycle_time` DATETIME(3) NULL,
    `iteration_id` BIGINT NULL,
    `parent_id` BIGINT NULL,
    `related_requirement_id` BIGINT NULL,
    `defect_type` INT NULL,
    `start_time` DATETIME(3) NULL,
    `end_time` DATETIME(3) NULL,
    `estimated_hours` INT NULL,
    `progress` INT NULL,
    `file_urls` JSON NULL,
    `label_ids` JSON NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_assignee_user_id` (`tenant_id`, `assignee_user_id`, `deleted`),
    KEY `idx_status_id` (`tenant_id`, `status_id`, `deleted`),
    KEY `idx_iteration_id` (`tenant_id`, `iteration_id`, `deleted`),
    KEY `idx_parent_id` (`tenant_id`, `parent_id`, `deleted`),
    KEY `idx_related_requirement_id` (`tenant_id`, `related_requirement_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_label` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `color` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `work_item_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_work_item_id` (`tenant_id`, `work_item_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_status` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `work_item_type` INT NULL,
    `name` VARCHAR(255) NULL,
    `status_type` INT NULL,
    `description` LONGTEXT NULL,
    `board_name` LONGTEXT NULL,
    `system_code` VARCHAR(255) NULL,
    `default_status` BIT(1) NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_user_sort` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `work_item_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_work_item_id` (`tenant_id`, `work_item_id`, `deleted`),
    KEY `idx_user_id` (`tenant_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pms_work_item_work_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NULL,
    `work_item_id` BIGINT NULL,
    `actual_hours` INT NULL,
    `remaining_hours` INT NULL,
    `description` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`),
    KEY `idx_project_id` (`tenant_id`, `project_id`, `deleted`),
    KEY `idx_work_item_id` (`tenant_id`, `work_item_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
