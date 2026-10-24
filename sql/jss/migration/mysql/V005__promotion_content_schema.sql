-- Current storefront content, decoration and customer-service models.
-- Schema only: no published decoration, articles, banners or conversation history.
-- CSV preview images follow StringListTypeHandler; property/content remain raw text.

CREATE TABLE promotion_article_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    pic_url VARCHAR(2048) NULL,
    status INT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_promotion_article_category_status (tenant_id,status,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE promotion_article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    spu_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(128) NULL,
    pic_url VARCHAR(2048) NULL,
    introduction VARCHAR(1024) NULL,
    browse_count INT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 0,
    recommend_hot BIT(1) NOT NULL DEFAULT b'0',
    recommend_banner BIT(1) NOT NULL DEFAULT b'0',
    content LONGTEXT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_promotion_article_category (tenant_id,category_id,status,sort),
    KEY idx_promotion_article_hot (tenant_id,recommend_hot,status,sort),
    KEY idx_promotion_article_banner (tenant_id,recommend_banner,status,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE promotion_banner (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NULL,
    pic_url VARCHAR(2048) NULL,
    sort INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 0,
    position INT NOT NULL DEFAULT 0,
    memo VARCHAR(1024) NULL,
    browse_count INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_promotion_banner_position (tenant_id,position,status,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE promotion_diy_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    used BIT(1) NOT NULL DEFAULT b'0',
    used_time DATETIME NULL,
    remark VARCHAR(1024) NULL,
    preview_pic_urls TEXT NULL,
    property LONGTEXT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    active_name VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN NULLIF(TRIM(name),'') ELSE NULL END) STORED,
    used_slot TINYINT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' AND used=b'1' THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_promotion_diy_template_name (tenant_id,active_name),
    UNIQUE KEY uk_promotion_diy_template_used (tenant_id,used_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE promotion_diy_page (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    remark VARCHAR(1024) NULL,
    preview_pic_urls TEXT NULL,
    property LONGTEXT NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    standalone_name VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' AND template_id IS NULL THEN NULLIF(TRIM(name),'') ELSE NULL END) STORED,
    UNIQUE KEY uk_promotion_diy_page_standalone (tenant_id,standalone_name),
    KEY idx_promotion_diy_page_template (tenant_id,template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE promotion_kefu_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    last_message_time DATETIME NULL,
    last_message_content LONGTEXT NULL,
    last_message_content_type INT NOT NULL DEFAULT 0,
    admin_pinned BIT(1) NOT NULL DEFAULT b'0',
    user_deleted BIT(1) NOT NULL DEFAULT b'0',
    admin_deleted BIT(1) NOT NULL DEFAULT b'0',
    admin_unread_message_count INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN user_id ELSE NULL END) STORED,
    UNIQUE KEY uk_promotion_kefu_user (tenant_id,active_user_id),
    KEY idx_promotion_kefu_admin (tenant_id,admin_deleted,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE promotion_kefu_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_type INT NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_type INT NOT NULL,
    content_type INT NOT NULL,
    content LONGTEXT NULL,
    read_status BIT(1) NOT NULL DEFAULT b'0',
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_promotion_kefu_message_conversation (tenant_id,conversation_id,id),
    KEY idx_promotion_kefu_message_unread (tenant_id,conversation_id,read_status,receiver_type,receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
