-- Development only: converted from database/seeds PostgreSQL fixtures, tenant 1.
-- Apply script/gym/sql/03 and 04 before this file; no old migrations are executed.
-- Natural keys retain existing records. Preflight rejects non-seed collisions.
-- Legacy scrypt was verified against the source development password and re-encoded as BCrypt.
-- Does not load Mock-only files or unsupported business modules.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @legacy_tenant = 1;
SET @legacy_creator = 'gym-legacy-seed';
CREATE TEMPORARY TABLE gym_legacy_import_guard (ok int NOT NULL CHECK (ok=1));
START TRANSACTION;
INSERT INTO gym_legacy_import_guard VALUES (1);
SET @legacy_hq = (SELECT id FROM system_dept WHERE tenant_id=@legacy_tenant AND name='健身总部' AND deleted=0 ORDER BY id LIMIT 1);
SET @legacy_root = (SELECT id FROM system_menu WHERE parent_id=0 AND path='gym' AND deleted=0 ORDER BY id LIMIT 1);
INSERT INTO gym_legacy_import_guard SELECT IF(@legacy_hq IS NOT NULL AND @legacy_root IS NOT NULL,1,0);
INSERT INTO gym_legacy_import_guard SELECT IF((SELECT COUNT(DISTINCT permission) FROM system_menu WHERE permission IN ('gym:stores:read','gym:stores:write','gym:employees:read','gym:employees:write','gym:files:read','gym:files:upload') AND status=0 AND deleted=0)=6,1,0);

-- Source store 00000000-0000-4000-8000-000000000001 / SH001
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS (SELECT 1 FROM gym_store WHERE tenant_id=@legacy_tenant AND code='SH001' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_dept (name,parent_id,sort,status,creator,updater,tenant_id) SELECT '上海一店',@legacy_hq,1,0,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS (SELECT 1 FROM system_dept WHERE tenant_id=@legacy_tenant AND parent_id=@legacy_hq AND name='上海一店' AND deleted=0);
SET @legacy_store1=(SELECT id FROM system_dept WHERE tenant_id=@legacy_tenant AND parent_id=@legacy_hq AND name='上海一店' AND deleted=0 ORDER BY id LIMIT 1);
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS (SELECT 1 FROM gym_store WHERE id=@legacy_store1 AND (tenant_id<>@legacy_tenant OR code<>'SH001')),1,0);
INSERT INTO gym_store (id,code,name,description,area_sqm,features,address,longitude,latitude,service_phone,service_wechat,opens,closes,status,creator,updater,tenant_id) SELECT @legacy_store1,'SH001','上海一店','健身门店','800.00','["力量训练","操课"]','上海市示例路1号','121.4737000','31.2304000','02112345678','gym1','06:00','23:00','active',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM gym_store WHERE tenant_id=@legacy_tenant AND code='SH001');
-- Source store 00000000-0000-4000-8000-000000000002 / SH002
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS (SELECT 1 FROM gym_store WHERE tenant_id=@legacy_tenant AND code='SH002' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_dept (name,parent_id,sort,status,creator,updater,tenant_id) SELECT '上海二店',@legacy_hq,2,0,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS (SELECT 1 FROM system_dept WHERE tenant_id=@legacy_tenant AND parent_id=@legacy_hq AND name='上海二店' AND deleted=0);
SET @legacy_store2=(SELECT id FROM system_dept WHERE tenant_id=@legacy_tenant AND parent_id=@legacy_hq AND name='上海二店' AND deleted=0 ORDER BY id LIMIT 1);
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS (SELECT 1 FROM gym_store WHERE id=@legacy_store2 AND (tenant_id<>@legacy_tenant OR code<>'SH002')),1,0);
INSERT INTO gym_store (id,code,name,description,area_sqm,features,address,longitude,latitude,service_phone,service_wechat,opens,closes,status,creator,updater,tenant_id) SELECT @legacy_store2,'SH002','上海二店','健身门店','800.00','["力量训练","操课"]','上海市示例路2号','121.4737000','31.2304000','02112345678','gym2','06:00','23:00','active',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM gym_store WHERE tenant_id=@legacy_tenant AND code='SH002');
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@legacy_tenant AND code='gym_dev_hq' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_role (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,tenant_id) SELECT '总部管理员（首批开发）','gym_dev_hq',95,1,'',0,2,'旧种子首批数据角色；不包含未实现模块权限',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@legacy_tenant AND code='gym_dev_hq');
SET @legacy_role_hq=(SELECT id FROM system_role WHERE tenant_id=@legacy_tenant AND code='gym_dev_hq' AND deleted=0 LIMIT 1);
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,tenant_id) SELECT @legacy_role_hq,m.id,@legacy_creator,@legacy_creator,@legacy_tenant FROM system_menu m WHERE m.deleted=0 AND m.status=0 AND (m.id=@legacy_root OR m.permission IN ('gym:employees:read','gym:employees:write','gym:files:read','gym:files:upload','gym:stores:read','gym:stores:write')) AND NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@legacy_role_hq AND rm.menu_id=m.id AND rm.tenant_id=@legacy_tenant AND rm.deleted=0);
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@legacy_tenant AND code='gym_dev_store' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_role (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,tenant_id) SELECT '门店管理员（首批开发）','gym_dev_store',95,4,'',0,2,'旧种子首批数据角色；不包含未实现模块权限',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_role WHERE tenant_id=@legacy_tenant AND code='gym_dev_store');
SET @legacy_role_store=(SELECT id FROM system_role WHERE tenant_id=@legacy_tenant AND code='gym_dev_store' AND deleted=0 LIMIT 1);
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,tenant_id) SELECT @legacy_role_store,m.id,@legacy_creator,@legacy_creator,@legacy_tenant FROM system_menu m WHERE m.deleted=0 AND m.status=0 AND (m.id=@legacy_root OR m.permission IN ('gym:employees:read','gym:files:read','gym:files:upload','gym:stores:read')) AND NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@legacy_role_store AND rm.menu_id=m.id AND rm.tenant_id=@legacy_tenant AND rm.deleted=0);
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@legacy_tenant AND username='hq' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_users(username,password,nickname,remark,dept_id,mobile,email,status,creator,updater,tenant_id) SELECT 'hq','$2a$10$uOB7F9lsIB0frh5AyLN1qe9jLN95Yi1hpTdWByvxpGO2JXIwKE4hy','总部管理员','旧 PostgreSQL 开发种子账号',@legacy_hq,NULL,'',0,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@legacy_tenant AND username='hq');
SET @legacy_user0=(SELECT id FROM system_users WHERE tenant_id=@legacy_tenant AND username='hq' AND deleted=0 LIMIT 1);
INSERT INTO system_user_role(user_id,role_id,creator,updater,tenant_id) SELECT @legacy_user0,@legacy_role_hq,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@legacy_user0 AND role_id=@legacy_role_hq AND tenant_id=@legacy_tenant AND deleted=0);
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@legacy_tenant AND username='store1' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_users(username,password,nickname,remark,dept_id,mobile,email,status,creator,updater,tenant_id) SELECT 'store1','$2a$10$uOB7F9lsIB0frh5AyLN1qe9jLN95Yi1hpTdWByvxpGO2JXIwKE4hy','店长1','旧 PostgreSQL 开发种子账号',@legacy_store1,NULL,'',0,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@legacy_tenant AND username='store1');
SET @legacy_user1=(SELECT id FROM system_users WHERE tenant_id=@legacy_tenant AND username='store1' AND deleted=0 LIMIT 1);
INSERT INTO system_user_role(user_id,role_id,creator,updater,tenant_id) SELECT @legacy_user1,@legacy_role_store,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@legacy_user1 AND role_id=@legacy_role_store AND tenant_id=@legacy_tenant AND deleted=0);
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@legacy_tenant AND username='store2' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO system_users(username,password,nickname,remark,dept_id,mobile,email,status,creator,updater,tenant_id) SELECT 'store2','$2a$10$uOB7F9lsIB0frh5AyLN1qe9jLN95Yi1hpTdWByvxpGO2JXIwKE4hy','店长2','旧 PostgreSQL 开发种子账号',@legacy_store2,NULL,'',0,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=@legacy_tenant AND username='store2');
SET @legacy_user2=(SELECT id FROM system_users WHERE tenant_id=@legacy_tenant AND username='store2' AND deleted=0 LIMIT 1);
INSERT INTO system_user_role(user_id,role_id,creator,updater,tenant_id) SELECT @legacy_user2,@legacy_role_store,@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@legacy_user2 AND role_id=@legacy_role_store AND tenant_id=@legacy_tenant AND deleted=0);
-- Source employee 00000000-0000-4000-8000-000000000021 / E001
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@legacy_tenant AND employee_no='E001' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO gym_employee(user_id,employee_no,name,phone,job_title,hired_on,status,creator,updater,tenant_id) SELECT @legacy_user1,'E001','店长1','13800000001','店长','2026-01-01','active',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@legacy_tenant AND employee_no='E001');
SET @legacy_employee1=(SELECT id FROM gym_employee WHERE tenant_id=@legacy_tenant AND employee_no='E001' AND deleted=0 LIMIT 1);
INSERT INTO gym_employee_store(employee_id,store_id,is_primary,is_manager,joined_on,creator,updater,tenant_id) SELECT @legacy_employee1,@legacy_store1,1,1,'2026-01-01',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM gym_employee_store WHERE tenant_id=@legacy_tenant AND employee_id=@legacy_employee1 AND store_id=@legacy_store1);
-- Source employee 00000000-0000-4000-8000-000000000022 / E002
INSERT INTO gym_legacy_import_guard SELECT IF(NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@legacy_tenant AND employee_no='E002' AND (NOT (creator <=> @legacy_creator) OR deleted<>0)),1,0);
INSERT INTO gym_employee(user_id,employee_no,name,phone,job_title,hired_on,status,creator,updater,tenant_id) SELECT @legacy_user2,'E002','店长2','13800000002','店长','2026-01-01','active',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM gym_employee WHERE tenant_id=@legacy_tenant AND employee_no='E002');
SET @legacy_employee2=(SELECT id FROM gym_employee WHERE tenant_id=@legacy_tenant AND employee_no='E002' AND deleted=0 LIMIT 1);
INSERT INTO gym_employee_store(employee_id,store_id,is_primary,is_manager,joined_on,creator,updater,tenant_id) SELECT @legacy_employee2,@legacy_store2,1,1,'2026-01-01',@legacy_creator,@legacy_creator,@legacy_tenant WHERE NOT EXISTS(SELECT 1 FROM gym_employee_store WHERE tenant_id=@legacy_tenant AND employee_id=@legacy_employee2 AND store_id=@legacy_store2);
COMMIT;
DROP TEMPORARY TABLE gym_legacy_import_guard;
