-- Member cancellation reason and immutable post-consumption balance. Historical balances stay unknown.
ALTER TABLE gym_booking ADD COLUMN cancellation_reason varchar(500) DEFAULT NULL COMMENT '会员自助取消原因';
ALTER TABLE gym_lesson_record ADD COLUMN remaining_lessons_after int DEFAULT NULL COMMENT '本次课消后的余额；历史未知或不限次数时为空';
