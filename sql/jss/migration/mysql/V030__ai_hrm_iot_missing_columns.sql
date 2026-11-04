-- Forward-only schema repair for the persisted fields used by the integrated native modules.
-- Keep V019/V026/V027 unchanged: existing Flyway checksums remain valid.
-- Legacy model modalities and assessment snapshots stay NULL; IoT scripts start disabled.

ALTER TABLE ai_model
    ADD COLUMN input_modalities LONGTEXT NULL COMMENT '输入模态数组，原StringListTypeHandler CSV格式'
        AFTER max_contexts;

ALTER TABLE hrm_performance_plan
    ADD COLUMN assessment_config JSON NULL COMMENT '考核配置快照，旧计划不伪造快照'
        AFTER assessment_template_id;

ALTER TABLE iot_product
    ADD COLUMN script_content LONGTEXT NULL COMMENT '数据解析脚本'
        AFTER serialize_type,
    ADD COLUMN script_enabled BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否启用数据解析脚本，默认禁用'
        AFTER script_content;
