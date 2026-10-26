-- Match the Long administrator identifiers used by error-log processing.
-- Preserve nullable legacy values and default 0; this widening does not rewrite log content.
ALTER TABLE `infra_api_error_log`
    MODIFY COLUMN `process_user_id` bigint NULL DEFAULT 0 COMMENT '处理用户编号';
