-- Complete native IoT relational tables. Device messages/properties retain original TDengine/IoTDB storage.

-- Inner model and rule configuration classes remain inside their native JSON fields.

-- Original list/set handlers retain CSV LONGTEXT.

CREATE TABLE `iot_alert_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `level` INT NULL,
    `status` INT NULL,
    `scene_rule_ids` LONGTEXT NULL,
    `receive_user_ids` LONGTEXT NULL,
    `receive_types` LONGTEXT NULL,
    `sms_template_code` LONGTEXT NULL,
    `mail_template_code` LONGTEXT NULL,
    `notify_template_code` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_alert_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `config_id` BIGINT NULL,
    `config_name` LONGTEXT NULL,
    `config_level` INT NULL,
    `scene_rule_id` BIGINT NULL,
    `product_id` BIGINT NULL,
    `device_id` BIGINT NULL,
    `device_message` JSON NULL,
    `process_status` BIT(1) NULL,
    `process_remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_device` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `device_name` LONGTEXT NULL,
    `nickname` LONGTEXT NULL,
    `serial_number` LONGTEXT NULL,
    `pic_url` LONGTEXT NULL,
    `group_ids` LONGTEXT NULL,
    `product_id` BIGINT NULL,
    `product_key` LONGTEXT NULL,
    `device_type` INT NULL,
    `gateway_id` BIGINT NULL,
    `state` INT NULL,
    `online_time` DATETIME NULL,
    `offline_time` DATETIME NULL,
    `active_time` DATETIME NULL,
    `firmware_id` BIGINT NULL,
    `device_secret` LONGTEXT NULL,
    `latitude` DECIMAL(38,12) NULL,
    `longitude` DECIMAL(38,12) NULL,
    `config` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_device_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `status` INT NULL,
    `description` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_device_modbus_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NULL,
    `device_id` BIGINT NULL,
    `ip` LONGTEXT NULL,
    `port` INT NULL,
    `slave_id` INT NULL,
    `timeout` INT NULL,
    `retry_interval` INT NULL,
    `mode` INT NULL,
    `frame_format` INT NULL,
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

CREATE TABLE `iot_device_modbus_point` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `device_id` BIGINT NULL,
    `thing_model_id` BIGINT NULL,
    `identifier` LONGTEXT NULL,
    `name` LONGTEXT NULL,
    `function_code` INT NULL,
    `register_address` INT NULL,
    `register_count` INT NULL,
    `byte_order` LONGTEXT NULL,
    `raw_data_type` LONGTEXT NULL,
    `scale` DECIMAL(38,12) NULL,
    `poll_interval` INT NULL,
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

CREATE TABLE `iot_ota_firmware` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `version` LONGTEXT NULL,
    `product_id` BIGINT NULL,
    `file_url` LONGTEXT NULL,
    `file_size` BIGINT NULL,
    `file_digest_algorithm` LONGTEXT NULL,
    `file_digest_value` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_ota_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `firmware_id` BIGINT NULL,
    `status` INT NULL,
    `device_scope` INT NULL,
    `device_total_count` INT NULL,
    `device_success_count` INT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_ota_task_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `firmware_id` BIGINT NULL,
    `task_id` BIGINT NULL,
    `device_id` BIGINT NULL,
    `from_firmware_id` BIGINT NULL,
    `status` INT NULL,
    `progress` INT NULL,
    `description` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_product_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `sort` INT NULL,
    `status` INT NULL,
    `description` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `product_key` LONGTEXT NULL,
    `product_secret` LONGTEXT NULL,
    `register_enabled` BIT(1) NULL,
    `category_id` BIGINT NULL,
    `icon` LONGTEXT NULL,
    `pic_url` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `device_type` INT NULL,
    `net_type` INT NULL,
    `protocol_type` LONGTEXT NULL,
    `serialize_type` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_data_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `source_configs` JSON NULL,
    `sink_ids` LONGTEXT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_data_sink` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `type` INT NULL,
    `config` JSON NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_scene_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `last_trigger_time` DATETIME NULL,
    `triggers` JSON NULL,
    `actions` JSON NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `iot_thing_model` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `identifier` LONGTEXT NULL,
    `name` LONGTEXT NULL,
    `description` LONGTEXT NULL,
    `product_id` BIGINT NULL,
    `product_key` LONGTEXT NULL,
    `type` INT NULL,
    `property` JSON NULL,
    `event` JSON NULL,
    `service` JSON NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
