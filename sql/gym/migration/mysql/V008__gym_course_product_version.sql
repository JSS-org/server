-- Existing course products start at their first tracked revision; do not invent earlier history.
ALTER TABLE gym_course_product ADD COLUMN version int NOT NULL DEFAULT 1 COMMENT '商品版本，内容或审批价格变更时递增';
