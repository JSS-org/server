-- Existing employees stay private until an authorized administrator explicitly publishes them.
ALTER TABLE gym_employee
    ADD COLUMN coach_published bit(1) NOT NULL DEFAULT b'0' COMMENT '是否公开为教练，旧员工默认不公开',
    ADD COLUMN coach_profile text DEFAULT NULL COMMENT '教练公开介绍、擅长项目和图片引用JSON';
