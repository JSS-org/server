-- Product schema reconciled against the current product DOs and mapper queries.
-- JSON fields use JSON storage; IntegerListTypeHandler fields deliberately use comma-separated VARCHAR.
-- No sample goods, prices, inventory, accounts or grants are inserted.
CREATE TABLE product_brand (
    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(255) NOT NULL,
    pic_url VARCHAR(1024) NOT NULL DEFAULT '', sort INT NOT NULL DEFAULT 0,
    description VARCHAR(1024) NULL, status INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_brand_status (tenant_id, status, deleted, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_category (
    id BIGINT NOT NULL AUTO_INCREMENT, parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL, pic_url VARCHAR(1024) NOT NULL DEFAULT '',
    sort INT NOT NULL DEFAULT 0, status INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_category_parent (tenant_id, parent_id, deleted, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_property (
    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(64) NOT NULL, remark VARCHAR(255) NULL,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_property_tenant (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_property_value (
    id BIGINT NOT NULL AUTO_INCREMENT, property_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL, remark VARCHAR(255) NULL,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_property_value_property (tenant_id, property_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_spu (
    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(128) NOT NULL,
    keyword VARCHAR(256) NOT NULL DEFAULT '', introduction VARCHAR(256) NOT NULL DEFAULT '',
    description LONGTEXT NOT NULL, category_id BIGINT NOT NULL, brand_id BIGINT NULL,
    pic_url VARCHAR(1024) NOT NULL DEFAULT '', slider_pic_urls JSON NULL,
    sort INT NOT NULL DEFAULT 0, status INT NOT NULL DEFAULT 0, spec_type BIT(1) NOT NULL DEFAULT b'0',
    price INT NOT NULL DEFAULT 0, market_price INT NOT NULL DEFAULT 0, cost_price INT NOT NULL DEFAULT 0,
    stock INT NOT NULL DEFAULT 0, delivery_types VARCHAR(255) NULL,
    delivery_template_id BIGINT NULL, give_integral INT NOT NULL DEFAULT 0,
    sub_commission_type BIT(1) NOT NULL DEFAULT b'0', sales_count INT NOT NULL DEFAULT 0,
    virtual_sales_count INT NOT NULL DEFAULT 0, browse_count INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_spu_catalog (tenant_id, status, deleted, category_id, sort, id),
    KEY idx_product_spu_brand (tenant_id, brand_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_sku (
    id BIGINT NOT NULL AUTO_INCREMENT, spu_id BIGINT NOT NULL, properties JSON NULL,
    price INT NOT NULL DEFAULT 0, market_price INT NOT NULL DEFAULT 0, cost_price INT NOT NULL DEFAULT 0,
    bar_code VARCHAR(64) NULL, pic_url VARCHAR(1024) NOT NULL DEFAULT '',
    stock INT NOT NULL DEFAULT 0, weight DOUBLE NULL, volume DOUBLE NULL,
    first_brokerage_price INT NOT NULL DEFAULT 0, second_brokerage_price INT NOT NULL DEFAULT 0,
    sales_count INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_sku_spu (tenant_id, spu_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_comment (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NULL,
    user_nickname VARCHAR(255) NULL, user_avatar VARCHAR(1024) NULL,
    anonymous BIT(1) NOT NULL DEFAULT b'0', order_id BIGINT NULL, order_item_id BIGINT NULL,
    spu_id BIGINT NOT NULL, spu_name VARCHAR(128) NULL, sku_id BIGINT NULL, sku_pic_url VARCHAR(1024) NULL,
    visible BIT(1) NOT NULL DEFAULT b'1', scores INT NULL, description_scores INT NULL, benefit_scores INT NULL,
    content TEXT NULL, pic_urls JSON NULL, reply_status BIT(1) NOT NULL DEFAULT b'0',
    reply_user_id BIGINT NULL, reply_content TEXT NULL, reply_time DATETIME NULL,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_comment_spu (tenant_id, spu_id, visible, deleted, create_time),
    KEY idx_product_comment_order_item (tenant_id, order_item_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, spu_id BIGINT NOT NULL,
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    active_spu_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN spu_id ELSE NULL END) STORED,
    PRIMARY KEY (id), UNIQUE KEY uk_product_favorite_active (tenant_id, user_id, active_spu_id),
    KEY idx_product_favorite_user (tenant_id, user_id, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_browse_history (
    id BIGINT NOT NULL AUTO_INCREMENT, spu_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    user_deleted BIT(1) NOT NULL DEFAULT b'0',
    creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_product_history_user (tenant_id, user_id, deleted, create_time),
    KEY idx_product_history_spu (tenant_id, user_id, spu_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
