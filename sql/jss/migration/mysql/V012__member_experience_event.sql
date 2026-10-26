-- Immutable business-event receipts. Original experience history is preserved, including duplicates.
CREATE TABLE `member_experience_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `biz_type` tinyint NOT NULL,
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `requested_experience` int NULL COMMENT 'Signed request; NULL seals a legacy event with unknown original request',
  `tenant_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_experience_event` (`tenant_id`,`user_id`,`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Member experience event receipts';

-- ADMIN (0) uses an interactive operation, not a stable event ID. Do not collapse those changes.
INSERT INTO `member_experience_event` (`tenant_id`,`user_id`,`biz_type`,`biz_id`,`requested_experience`)
SELECT `tenant_id`,`user_id`,`biz_type`,`biz_id` COLLATE utf8mb4_bin,NULL
FROM `member_experience_record`
WHERE `biz_type`<>0 AND CHAR_LENGTH(TRIM(`biz_id`))>0
GROUP BY `tenant_id`,`user_id`,`biz_type`,`biz_id` COLLATE utf8mb4_bin;
