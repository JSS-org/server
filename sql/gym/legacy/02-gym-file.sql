-- =============================================================================
-- JSS 健身 · gym 私有文件表
-- -----------------------------------------------------------------------------
-- 对应接口（admin/target/plan/10-backend-mock-api-inventory.md §4.1 后 3 行）：
--   POST /admin-api/gym/files            上传（魔数校验 + 类别限制 + 门店数据范围）
--   GET  /admin-api/gym/files/{id}       元数据
--   GET  /admin-api/gym/files/{id}/content  内容流（鉴权下载）
--
-- 表只存「业务归属」，文件内容交给 infra 文件服务存储（见 GymFileDO 注释）。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 用法：mysql -h127.0.0.1 -uyudao -p ruoyi-vue-pro < 02-gym-file.sql
--       或直接执行 script/gym/seed.sh（按文件名顺序执行本目录全部脚本）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `gym_file`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '文件编号',
    `store_id`    bigint                DEFAULT NULL COMMENT '归属门店编号（system_dept.id）',
    `category`    varchar(32)  NOT NULL COMMENT '文件类别：approval/body_report/contract/image/video',
    `name`        varchar(255) NOT NULL COMMENT '原始文件名',
    `url`         varchar(512) NOT NULL COMMENT 'infra 文件访问路径（私有，不返回前端）',
    `mime_type`   varchar(100) NOT NULL DEFAULT '' COMMENT '内容类型（魔数嗅探结果）',
    `size`        bigint       NOT NULL DEFAULT 0 COMMENT '文件大小，单位：字节',
    `uploader_id` bigint                DEFAULT NULL COMMENT '上传人编号（system_users.id）',
    `creator`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_gym_file_store_category` (`store_id`, `category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='gym 私有文件';
