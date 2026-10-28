-- Complete original CRM/BPM business models, schema only.
-- No accounts, roles, owners, workflow definitions or business data are seeded.
-- List handlers keep comma-delimited TEXT; Jackson handlers keep JSON.
-- BigDecimal fields use DECIMAL(38,12); identifiers remain BIGINT or opaque VARCHAR.
-- Tenant columns are required by TenantDatabaseInterceptor even for these BaseDO models.

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/business/CrmBusinessDO.java
CREATE TABLE `crm_business` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `customer_id` BIGINT NULL,
    `follow_up_status` BIT(1) NOT NULL DEFAULT b'0',
    `contact_last_time` DATETIME(3) NULL,
    `contact_next_time` DATETIME(3) NULL,
    `owner_user_id` BIGINT NULL,
    `status_type_id` BIGINT NULL,
    `status_id` BIGINT NULL,
    `end_status` INT NULL,
    `end_remark` LONGTEXT NULL,
    `deal_time` DATETIME(3) NULL,
    `total_product_price` DECIMAL(38,12) NULL,
    `discount_percent` DECIMAL(38,12) NULL,
    `total_price` DECIMAL(38,12) NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`),
    KEY `idx_customer_id` (`tenant_id`,`customer_id`,`deleted`),
    KEY `idx_status_type_id` (`tenant_id`,`status_type_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/business/CrmBusinessProductDO.java
CREATE TABLE `crm_business_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `business_id` BIGINT NULL,
    `product_id` BIGINT NULL,
    `product_price` DECIMAL(38,12) NULL,
    `business_price` DECIMAL(38,12) NULL,
    `count` DECIMAL(38,12) NULL,
    `total_price` DECIMAL(38,12) NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_business_id` (`tenant_id`,`business_id`,`deleted`),
    KEY `idx_product_id` (`tenant_id`,`product_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/business/CrmBusinessStatusDO.java
CREATE TABLE `crm_business_status` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `type_id` BIGINT NULL,
    `name` VARCHAR(255) NULL,
    `percent` INT NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_type_id` (`tenant_id`,`type_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/business/CrmBusinessStatusTypeDO.java
CREATE TABLE `crm_business_status_type` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `dept_ids` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/clue/CrmClueDO.java
CREATE TABLE `crm_clue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `follow_up_status` BIT(1) NOT NULL DEFAULT b'0',
    `contact_last_time` DATETIME(3) NULL,
    `contact_last_content` LONGTEXT NULL,
    `contact_next_time` DATETIME(3) NULL,
    `owner_user_id` BIGINT NULL,
    `transform_status` BIT(1) NOT NULL DEFAULT b'0',
    `customer_id` BIGINT NULL,
    `mobile` VARCHAR(255) NULL,
    `telephone` VARCHAR(255) NULL,
    `qq` VARCHAR(255) NULL,
    `wechat` VARCHAR(255) NULL,
    `email` VARCHAR(255) NULL,
    `area_id` INT NULL,
    `detail_address` VARCHAR(2048) NULL,
    `industry_id` INT NULL,
    `level` INT NULL,
    `source` INT NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`),
    KEY `idx_customer_id` (`tenant_id`,`customer_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/contact/CrmContactBusinessDO.java
CREATE TABLE `crm_contact_business` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `contact_id` BIGINT NULL,
    `business_id` BIGINT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_business_id` (`tenant_id`,`business_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/contact/CrmContactDO.java
CREATE TABLE `crm_contact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `customer_id` BIGINT NULL,
    `contact_last_time` DATETIME(3) NULL,
    `contact_last_content` LONGTEXT NULL,
    `contact_next_time` DATETIME(3) NULL,
    `owner_user_id` BIGINT NULL,
    `mobile` VARCHAR(255) NULL,
    `telephone` VARCHAR(255) NULL,
    `email` VARCHAR(255) NULL,
    `qq` BIGINT NULL,
    `wechat` VARCHAR(255) NULL,
    `area_id` INT NULL,
    `detail_address` VARCHAR(2048) NULL,
    `sex` INT NULL,
    `master` BIT(1) NOT NULL DEFAULT b'0',
    `post` VARCHAR(255) NULL,
    `parent_id` BIGINT NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`),
    KEY `idx_customer_id` (`tenant_id`,`customer_id`,`deleted`),
    KEY `idx_parent_id` (`tenant_id`,`parent_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/contract/CrmContractConfigDO.java
CREATE TABLE `crm_contract_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `notify_enabled` BIT(1) NOT NULL DEFAULT b'0',
    `notify_days` INT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/contract/CrmContractDO.java
CREATE TABLE `crm_contract` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `no` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `customer_id` BIGINT NULL,
    `business_id` BIGINT NULL,
    `contact_last_time` DATETIME(3) NULL,
    `owner_user_id` BIGINT NULL,
    `process_instance_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `audit_status` INT NULL,
    `order_date` DATETIME(3) NULL,
    `start_time` DATETIME(3) NULL,
    `end_time` DATETIME(3) NULL,
    `total_product_price` DECIMAL(38,12) NULL,
    `discount_percent` DECIMAL(38,12) NULL,
    `total_price` DECIMAL(38,12) NULL,
    `sign_contact_id` BIGINT NULL,
    `sign_user_id` BIGINT NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`),
    KEY `idx_customer_id` (`tenant_id`,`customer_id`,`deleted`),
    KEY `idx_business_id` (`tenant_id`,`business_id`,`deleted`),
    KEY `idx_process_instance_id` (`tenant_id`,`process_instance_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/contract/CrmContractProductDO.java
CREATE TABLE `crm_contract_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `contract_id` BIGINT NULL,
    `product_id` BIGINT NULL,
    `product_price` DECIMAL(38,12) NULL,
    `contract_price` DECIMAL(38,12) NULL,
    `count` DECIMAL(38,12) NULL,
    `total_price` DECIMAL(38,12) NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_contract_id` (`tenant_id`,`contract_id`,`deleted`),
    KEY `idx_product_id` (`tenant_id`,`product_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/customer/CrmCustomerDO.java
CREATE TABLE `crm_customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `follow_up_status` BIT(1) NOT NULL DEFAULT b'0',
    `contact_last_time` DATETIME(3) NULL,
    `contact_last_content` LONGTEXT NULL,
    `contact_next_time` DATETIME(3) NULL,
    `owner_user_id` BIGINT NULL,
    `owner_time` DATETIME(3) NULL,
    `lock_status` BIT(1) NOT NULL DEFAULT b'0',
    `deal_status` BIT(1) NOT NULL DEFAULT b'0',
    `mobile` VARCHAR(255) NULL,
    `telephone` VARCHAR(255) NULL,
    `qq` VARCHAR(255) NULL,
    `wechat` VARCHAR(255) NULL,
    `email` VARCHAR(255) NULL,
    `area_id` INT NULL,
    `detail_address` VARCHAR(2048) NULL,
    `industry_id` INT NULL,
    `level` INT NULL,
    `source` INT NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/customer/CrmCustomerLimitConfigDO.java
CREATE TABLE `crm_customer_limit_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `type` INT NULL,
    `user_ids` LONGTEXT NULL,
    `dept_ids` LONGTEXT NULL,
    `max_count` INT NULL,
    `deal_count_enabled` BIT(1) NOT NULL DEFAULT b'0',
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/customer/CrmCustomerPoolConfigDO.java
CREATE TABLE `crm_customer_pool_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `enabled` BIT(1) NOT NULL DEFAULT b'0',
    `contact_expire_days` INT NULL,
    `deal_expire_days` INT NULL,
    `notify_enabled` BIT(1) NOT NULL DEFAULT b'0',
    `notify_days` INT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/followup/CrmFollowUpRecordDO.java
CREATE TABLE `crm_follow_up_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `biz_type` INT NULL,
    `biz_id` BIGINT NULL,
    `type` INT NULL,
    `content` LONGTEXT NULL,
    `next_time` DATETIME(3) NULL,
    `pic_urls` LONGTEXT NULL,
    `file_urls` LONGTEXT NULL,
    `business_ids` LONGTEXT NULL,
    `contact_ids` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_business` (`tenant_id`,`biz_type`,`biz_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/performance/CrmPerformanceConfigDO.java
CREATE TABLE `crm_performance_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `biz_type` INT NULL,
    `object_id` BIGINT NULL,
    `object_type` INT NULL,
    `year` INT NULL,
    `year_target_price` DECIMAL(38,12) NULL,
    `january_target_price` DECIMAL(38,12) NULL,
    `february_target_price` DECIMAL(38,12) NULL,
    `march_target_price` DECIMAL(38,12) NULL,
    `april_target_price` DECIMAL(38,12) NULL,
    `may_target_price` DECIMAL(38,12) NULL,
    `june_target_price` DECIMAL(38,12) NULL,
    `july_target_price` DECIMAL(38,12) NULL,
    `august_target_price` DECIMAL(38,12) NULL,
    `september_target_price` DECIMAL(38,12) NULL,
    `october_target_price` DECIMAL(38,12) NULL,
    `november_target_price` DECIMAL(38,12) NULL,
    `december_target_price` DECIMAL(38,12) NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/permission/CrmOwnerRecordDO.java
CREATE TABLE `crm_owner_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `biz_type` INT NULL,
    `biz_id` BIGINT NULL,
    `pre_owner_user_id` BIGINT NULL,
    `post_owner_user_id` BIGINT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_business` (`tenant_id`,`biz_type`,`biz_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/permission/CrmPermissionDO.java
CREATE TABLE `crm_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `biz_type` INT NULL,
    `biz_id` BIGINT NULL,
    `user_id` BIGINT NULL,
    `level` INT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_user_id` (`tenant_id`,`user_id`,`deleted`),
    KEY `idx_business` (`tenant_id`,`biz_type`,`biz_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/product/CrmProductCategoryDO.java
CREATE TABLE `crm_product_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `parent_id` BIGINT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_parent_id` (`tenant_id`,`parent_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/product/CrmProductDO.java
CREATE TABLE `crm_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `no` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `unit` INT NULL,
    `price` DECIMAL(38,12) NULL,
    `status` INT NULL,
    `category_id` BIGINT NULL,
    `description` LONGTEXT NULL,
    `owner_user_id` BIGINT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/receivable/CrmReceivableDO.java
CREATE TABLE `crm_receivable` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `no` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `plan_id` BIGINT NULL,
    `customer_id` BIGINT NULL,
    `contract_id` BIGINT NULL,
    `owner_user_id` BIGINT NULL,
    `return_time` DATETIME(3) NULL,
    `return_type` INT NULL,
    `price` DECIMAL(38,12) NULL,
    `remark` LONGTEXT NULL,
    `process_instance_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `audit_status` INT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`),
    KEY `idx_customer_id` (`tenant_id`,`customer_id`,`deleted`),
    KEY `idx_contract_id` (`tenant_id`,`contract_id`,`deleted`),
    KEY `idx_process_instance_id` (`tenant_id`,`process_instance_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-crm/src/main/java/cn/iocoder/yudao/module/crm/dal/dataobject/receivable/CrmReceivablePlanDO.java
CREATE TABLE `crm_receivable_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `period` INT NULL,
    `customer_id` BIGINT NULL,
    `contract_id` BIGINT NULL,
    `owner_user_id` BIGINT NULL,
    `return_time` DATETIME(3) NULL,
    `return_type` INT NULL,
    `price` DECIMAL(38,12) NULL,
    `receivable_id` BIGINT NULL,
    `remind_days` INT NULL,
    `remind_time` DATETIME(3) NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_owner_user_id` (`tenant_id`,`owner_user_id`,`deleted`),
    KEY `idx_customer_id` (`tenant_id`,`customer_id`,`deleted`),
    KEY `idx_contract_id` (`tenant_id`,`contract_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmCategoryDO.java
CREATE TABLE `bpm_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `code` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `sort` INT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmFormDO.java
CREATE TABLE `bpm_form` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `status` INT NULL,
    `conf` LONGTEXT NULL,
    `fields` JSON NULL,
    `remark` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmProcessDefinitionInfoDO.java
CREATE TABLE `bpm_process_definition_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `process_definition_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `model_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `model_type` INT NULL,
    `category` VARCHAR(255) NULL,
    `icon` VARCHAR(2048) NULL,
    `description` LONGTEXT NULL,
    `form_type` INT NULL,
    `form_id` BIGINT NULL,
    `form_conf` LONGTEXT NULL,
    `form_fields` JSON NULL,
    `form_custom_create_path` VARCHAR(2048) NULL,
    `form_custom_view_path` VARCHAR(2048) NULL,
    `simple_model` LONGTEXT NULL,
    `visible` BIT(1) NOT NULL DEFAULT b'1',
    `sort` BIGINT NULL,
    `start_user_ids` LONGTEXT NULL,
    `start_dept_ids` LONGTEXT NULL,
    `manager_user_ids` LONGTEXT NULL,
    `allow_cancel_running_process` BIT(1) NOT NULL DEFAULT b'0',
    `allow_withdraw_task` BIT(1) NOT NULL DEFAULT b'0',
    `process_id_rule` JSON NULL,
    `auto_approval_type` INT NULL,
    `title_setting` JSON NULL,
    `summary_setting` JSON NULL,
    `process_before_trigger_setting` JSON NULL,
    `process_after_trigger_setting` JSON NULL,
    `task_before_trigger_setting` JSON NULL,
    `task_after_trigger_setting` JSON NULL,
    `print_template_setting` JSON NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_process_definition_id` (`tenant_id`,`process_definition_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmProcessExpressionDO.java
CREATE TABLE `bpm_process_expression` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `status` INT NULL,
    `expression` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmProcessListenerDO.java
CREATE TABLE `bpm_process_listener` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `status` INT NULL,
    `type` VARCHAR(255) NULL,
    `event` VARCHAR(255) NULL,
    `value_type` VARCHAR(255) NULL,
    `value` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmUserGroupDO.java
CREATE TABLE `bpm_user_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NULL,
    `description` LONGTEXT NULL,
    `status` INT NULL,
    `user_ids` JSON NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/oa/BpmOALeaveDO.java
CREATE TABLE `bpm_oa_leave` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `type` INT NULL,
    `reason` LONGTEXT NULL,
    `start_time` DATETIME(3) NULL,
    `end_time` DATETIME(3) NULL,
    `day` BIGINT NULL,
    `status` INT NULL,
    `process_instance_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_user_id` (`tenant_id`,`user_id`,`deleted`),
    KEY `idx_process_instance_id` (`tenant_id`,`process_instance_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/task/BpmProcessInstanceCopyDO.java
CREATE TABLE `bpm_process_instance_copy` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `start_user_id` BIGINT NULL,
    `process_instance_name` VARCHAR(255) NULL,
    `process_instance_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `process_definition_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `category` VARCHAR(255) NULL,
    `activity_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `activity_name` VARCHAR(255) NULL,
    `task_id` VARCHAR(255) COLLATE utf8mb4_bin NULL,
    `user_id` BIGINT NULL,
    `reason` LONGTEXT NULL,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_deleted` (`tenant_id`,`deleted`),
    KEY `idx_user_id` (`tenant_id`,`user_id`,`deleted`),
    KEY `idx_process_instance_id` (`tenant_id`,`process_instance_id`,`deleted`),
    KEY `idx_process_definition_id` (`tenant_id`,`process_definition_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
