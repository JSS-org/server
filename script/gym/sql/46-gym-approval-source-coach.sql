-- Freeze the product's teaching employee for member-scoped SELF price-request reads.
SET @gym_has_source_coach = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_approval' AND column_name='source_coach_id');
SET @gym_source_coach_ddl = IF(@gym_has_source_coach=0,'ALTER TABLE gym_approval ADD COLUMN source_coach_id bigint DEFAULT NULL','SELECT 1');
PREPARE gym_source_coach_stmt FROM @gym_source_coach_ddl;
EXECUTE gym_source_coach_stmt;
DEALLOCATE PREPARE gym_source_coach_stmt;

-- Existing request snapshots were server-created; invalid historical JSON is never evaluated.
UPDATE gym_approval a
JOIN gym_course_product p ON p.tenant_id=a.tenant_id AND p.store_id=a.store_id AND p.deleted=0
 AND p.id=CAST(JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(a.request_snapshot),a.request_snapshot,'{}'),'$.sourceId')) AS UNSIGNED)
SET a.source_coach_id=p.coach_id
WHERE a.business_type='price_change' AND a.source_coach_id IS NULL;

SET @gym_has_source_coach_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_approval' AND index_name='idx_approval_own_price');
SET @gym_source_coach_index_ddl = IF(@gym_has_source_coach_index=0,'CREATE INDEX idx_approval_own_price ON gym_approval(tenant_id,applicant_user_id,business_type,source_coach_id,store_id)','SELECT 1');
PREPARE gym_source_coach_index_stmt FROM @gym_source_coach_index_ddl;
EXECUTE gym_source_coach_index_stmt;
DEALLOCATE PREPARE gym_source_coach_index_stmt;
