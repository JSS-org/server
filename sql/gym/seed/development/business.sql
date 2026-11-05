-- Included inside demo.sql's transaction. No paid orders or third-party success fixtures.
-- Stable business keys belong exclusively to gym-seed. Existing business edits are retained.
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(
  SELECT 1 FROM system_dept WHERE tenant_id=@tenant_id AND parent_id=@gym_hq_dept
  AND name IN ('示范门店','示范二店') AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
INSERT INTO system_dept(name,parent_id,sort,status,creator,updater,tenant_id)
SELECT '示范二店',@gym_hq_dept,2,0,@seed_user,@seed_user,@tenant_id
WHERE NOT EXISTS(SELECT 1 FROM system_dept WHERE tenant_id=@tenant_id AND parent_id=@gym_hq_dept AND name='示范二店');
SET @gym_second_dept=(SELECT id FROM system_dept WHERE tenant_id=@tenant_id AND parent_id=@gym_hq_dept AND name='示范二店' AND deleted=0);

INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(
  SELECT 1 FROM gym_store WHERE tenant_id=@tenant_id AND code IN ('JSS-DEMO-01','JSS-DEMO-02')
  AND (NOT(creator <=> @seed_user) OR deleted<>0 OR id<>IF(code='JSS-DEMO-01',@gym_store_dept,@gym_second_dept))),1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(
  SELECT 1 FROM gym_store WHERE id IN (@gym_store_dept,@gym_second_dept)
  AND (tenant_id<>@tenant_id OR code<>IF(id=@gym_store_dept,'JSS-DEMO-01','JSS-DEMO-02'))),1,0);
INSERT INTO gym_store(id,code,name,description,area_sqm,features,address,opens,closes,status,creator,updater,tenant_id)
SELECT d.id,IF(d.id=@gym_store_dept,'JSS-DEMO-01','JSS-DEMO-02'),d.name,
       '仅供开发联调的示范门店',800,'["力量训练","私教"]','开发演示地址','06:00','23:00','active',@seed_user,@seed_user,@tenant_id
FROM system_dept d WHERE d.id IN (@gym_store_dept,@gym_second_dept) AND d.tenant_id=@tenant_id
AND NOT EXISTS(SELECT 1 FROM gym_store s WHERE s.id=d.id);

-- These accounts may already be bound by an operator; never silently rebind them.
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(
  SELECT 1 FROM gym_employee WHERE tenant_id=@tenant_id AND employee_no IN ('JSS-DEMO-HQ','JSS-DEMO-MGR','JSS-DEMO-COACH')
  AND (NOT(creator <=> @seed_user) OR deleted<>0)),1,0);
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(
  SELECT 1 FROM gym_employee WHERE tenant_id=@tenant_id AND user_id IN (@hq_user,@store_user)
  AND employee_no<>IF(user_id=@hq_user,'JSS-DEMO-HQ','JSS-DEMO-MGR')),1,0);
SET @gym_new_hq_employee=NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@tenant_id AND employee_no='JSS-DEMO-HQ');
SET @gym_new_store_employee=NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@tenant_id AND employee_no='JSS-DEMO-MGR');
SET @gym_new_coach_employee=NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@tenant_id AND employee_no='JSS-DEMO-COACH');
INSERT INTO gym_employee(user_id,employee_no,name,phone,job_title,hired_on,status,creator,updater,tenant_id)
SELECT @hq_user,'JSS-DEMO-HQ','示范总部员工','13800000001','运营','2026-01-01','active',@seed_user,@seed_user,@tenant_id
WHERE @gym_new_hq_employee=1;
INSERT INTO gym_employee(user_id,employee_no,name,phone,job_title,hired_on,status,creator,updater,tenant_id)
SELECT @store_user,'JSS-DEMO-MGR','示范门店店长','13800000002','店长兼教练','2026-01-01','active',@seed_user,@seed_user,@tenant_id
WHERE @gym_new_store_employee=1;
INSERT INTO gym_employee(employee_no,name,phone,job_title,hired_on,status,creator,updater,tenant_id)
SELECT 'JSS-DEMO-COACH','示范二店教练','13800000003','教练','2026-01-01','active',@seed_user,@seed_user,@tenant_id
WHERE @gym_new_coach_employee=1;
INSERT INTO gym_employee_store(employee_id,store_id,is_primary,is_manager,joined_on,creator,updater,tenant_id)
SELECT e.id,IF(e.employee_no='JSS-DEMO-COACH',@gym_second_dept,@gym_store_dept),1,
       e.employee_no='JSS-DEMO-MGR','2026-01-01',@seed_user,@seed_user,@tenant_id
FROM gym_employee e WHERE e.tenant_id=@tenant_id AND
 ((e.employee_no='JSS-DEMO-HQ' AND @gym_new_hq_employee=1)
 OR (e.employee_no='JSS-DEMO-MGR' AND @gym_new_store_employee=1)
 OR (e.employee_no='JSS-DEMO-COACH' AND @gym_new_coach_employee=1));

-- One prospect per store; memberships/cards are issued through audited business APIs.
INSERT INTO gym_demo_guard SELECT IF(NOT EXISTS(
  SELECT 1 FROM gym_member WHERE tenant_id=@tenant_id AND
  (member_no IN ('JSS-DEMO-M01','JSS-DEMO-M02') OR phone IN ('13900009001','13900009002'))
  AND (NOT(creator COLLATE utf8mb4_unicode_ci <=> @seed_user) OR deleted<>0
       OR member_no NOT IN ('JSS-DEMO-M01','JSS-DEMO-M02'))),1,0);
SET @gym_new_member1=NOT EXISTS(SELECT 1 FROM gym_member WHERE tenant_id=@tenant_id AND member_no='JSS-DEMO-M01');
SET @gym_new_member2=NOT EXISTS(SELECT 1 FROM gym_member WHERE tenant_id=@tenant_id AND member_no='JSS-DEMO-M02');
INSERT INTO gym_member(store_id,member_no,name,phone,gender,status,customer_stage,registered_at,creator,updater,tenant_id)
SELECT @gym_store_dept,'JSS-DEMO-M01','示范会员一','13900009001','unspecified','active','lead',NOW(6),@seed_user,@seed_user,@tenant_id
WHERE @gym_new_member1=1;
INSERT INTO gym_member(store_id,member_no,name,phone,gender,status,customer_stage,registered_at,creator,updater,tenant_id)
SELECT @gym_second_dept,'JSS-DEMO-M02','示范会员二','13900009002','unspecified','active','lead',NOW(6),@seed_user,@seed_user,@tenant_id
WHERE @gym_new_member2=1;
INSERT INTO gym_member_store(member_id,store_id,joined_at,creator,updater,tenant_id)
SELECT m.id,m.store_id,m.registered_at,@seed_user,@seed_user,@tenant_id FROM gym_member m
WHERE m.tenant_id=@tenant_id AND ((m.member_no='JSS-DEMO-M01' AND @gym_new_member1=1)
 OR (m.member_no='JSS-DEMO-M02' AND @gym_new_member2=1));
