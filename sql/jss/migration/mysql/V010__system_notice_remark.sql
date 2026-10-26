-- Preserve notice remarks from the unified mobile management form.
-- Framework system_notice must already exist. No existing content is rewritten.
ALTER TABLE `system_notice`
    ADD COLUMN `remark` varchar(200) NOT NULL DEFAULT '' COMMENT '公告备注';
