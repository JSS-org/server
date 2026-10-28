-- Complete native AI persisted outer DO fields.

-- Inner model and rule configuration classes remain inside their native JSON fields.

-- Original list/set handlers retain CSV LONGTEXT.

CREATE TABLE `ai_chat_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `title` LONGTEXT NULL,
    `pinned` BIT(1) NULL,
    `pinned_time` DATETIME NULL,
    `role_id` BIGINT NULL,
    `model_id` BIGINT NULL,
    `model` LONGTEXT NULL,
    `system_message` LONGTEXT NULL,
    `temperature` DOUBLE NULL,
    `max_tokens` INT NULL,
    `max_contexts` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT NULL,
    `reply_id` BIGINT NULL,
    `type` LONGTEXT NULL,
    `user_id` BIGINT NULL,
    `role_id` BIGINT NULL,
    `model` LONGTEXT NULL,
    `model_id` BIGINT NULL,
    `content` LONGTEXT NULL,
    `reasoning_content` LONGTEXT NULL,
    `use_context` BIT(1) NULL,
    `segment_ids` LONGTEXT NULL,
    `web_search_pages` JSON NULL,
    `attachment_urls` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `prompt` LONGTEXT NULL,
    `platform` LONGTEXT NULL,
    `model_id` BIGINT NULL,
    `model` LONGTEXT NULL,
    `width` INT NULL,
    `height` INT NULL,
    `status` INT NULL,
    `finish_time` DATETIME NULL,
    `error_message` LONGTEXT NULL,
    `pic_url` LONGTEXT NULL,
    `public_status` BIT(1) NULL,
    `options` JSON NULL,
    `buttons` JSON NULL,
    `task_id` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `embedding_model_id` BIGINT NULL,
    `embedding_model` LONGTEXT NULL,
    `top_k` INT NULL,
    `similarity_threshold` DOUBLE NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `knowledge_id` BIGINT NULL,
    `name` LONGTEXT NULL,
    `url` LONGTEXT NULL,
    `content` LONGTEXT NULL,
    `content_length` INT NULL,
    `tokens` INT NULL,
    `segment_max_tokens` INT NULL,
    `retrieval_count` INT NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_knowledge_segment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `knowledge_id` BIGINT NULL,
    `document_id` BIGINT NULL,
    `content` LONGTEXT NULL,
    `content_length` INT NULL,
    `vector_id` LONGTEXT NULL,
    `tokens` INT NULL,
    `retrieval_count` INT NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_mind_map` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `platform` LONGTEXT NULL,
    `model_id` BIGINT NULL,
    `model` LONGTEXT NULL,
    `prompt` LONGTEXT NULL,
    `generated_content` LONGTEXT NULL,
    `error_message` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_api_key` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `api_key` LONGTEXT NULL,
    `platform` LONGTEXT NULL,
    `url` LONGTEXT NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_chat_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `avatar` LONGTEXT NULL,
    `category` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `system_message` LONGTEXT NULL,
    `user_id` BIGINT NULL,
    `model_id` BIGINT NULL,
    `knowledge_ids` LONGTEXT NULL,
    `tool_ids` LONGTEXT NULL,
    `mcp_client_names` LONGTEXT NULL,
    `public_status` BIT(1) NULL,
    `sort` INT NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_model` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `key_id` BIGINT NULL,
    `name` LONGTEXT NULL,
    `model` LONGTEXT NULL,
    `platform` LONGTEXT NULL,
    `type` INT NULL,
    `sort` INT NULL,
    `status` INT NULL,
    `temperature` DOUBLE NULL,
    `max_tokens` INT NULL,
    `max_contexts` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_tool` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_music` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `title` LONGTEXT NULL,
    `lyric` LONGTEXT NULL,
    `image_url` LONGTEXT NULL,
    `audio_url` LONGTEXT NULL,
    `video_url` LONGTEXT NULL,
    `status` INT NULL,
    `generate_mode` INT NULL,
    `description` LONGTEXT NULL,
    `platform` LONGTEXT NULL,
    `model` LONGTEXT NULL,
    `tags` JSON NULL,
    `duration` DOUBLE NULL,
    `public_status` BIT(1) NULL,
    `task_id` LONGTEXT NULL,
    `error_message` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_workflow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `code` LONGTEXT NULL,
    `graph` LONGTEXT NULL,
    `remark` LONGTEXT NULL,
    `status` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_write` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `type` INT NULL,
    `platform` LONGTEXT NULL,
    `model_id` BIGINT NULL,
    `model` LONGTEXT NULL,
    `prompt` LONGTEXT NULL,
    `generated_content` LONGTEXT NULL,
    `original_content` LONGTEXT NULL,
    `length` INT NULL,
    `format` INT NULL,
    `tone` INT NULL,
    `language` INT NULL,
    `error_message` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
