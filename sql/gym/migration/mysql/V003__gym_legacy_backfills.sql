-- Required legacy data transitions. Never restores deleted member-store links or grants roles.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO gym_member_store (member_id,store_id,joined_at,creator,updater,tenant_id)
SELECT m.id,m.store_id,m.registered_at,'gym-card-migration','gym-card-migration',m.tenant_id FROM gym_member m
WHERE m.deleted=0 AND NOT EXISTS (SELECT 1 FROM gym_member_store ms WHERE ms.member_id=m.id AND ms.store_id=m.store_id AND ms.tenant_id=m.tenant_id);

UPDATE gym_member SET customer_stage='lead' WHERE customer_stage='prospect';

UPDATE gym_approval a
JOIN gym_course_product p ON p.tenant_id=a.tenant_id AND p.store_id=a.store_id AND p.deleted=0
 AND p.id=CAST(JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(a.request_snapshot),a.request_snapshot,'{}'),'$.sourceId')) AS UNSIGNED)
SET a.source_coach_id=p.coach_id
WHERE a.business_type='price_change' AND a.source_coach_id IS NULL;

