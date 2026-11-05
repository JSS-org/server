-- Fresh development schema only; bootstrap.py supplies tenant and random OAuth client secret.
-- No external service accounts, tokens, historical business rows or sample cloud credentials.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_tenant(id,name,contact_name,status,websites,package_id,expire_time,account_count,creator,updater)
VALUES(@tenant_id,'JSS 开发租户','开发管理员',0,'["localhost","127.0.0.1"]',0,'2099-12-31 23:59:59',1000,'gym-seed','gym-seed');
INSERT INTO system_oauth2_client(client_id,secret,name,logo,status,access_token_validity_seconds,refresh_token_validity_seconds,redirect_uris,authorized_grant_types,scopes,auto_approve_scopes)
VALUES('default',@dev_oauth_secret,'JSS 本地开发','',0,1800,604800,'[]','["password","refresh_token"]','[]','[]');
INSERT INTO infra_file_config(id,name,storage,remark,master,config,creator,updater)
VALUES(4,'JSS 本地数据库文件',1,'开发文件由业务 API 授权访问',1,
 '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"http://127.0.0.1:48080"}','gym-seed','gym-seed');
-- Development maintenance account; business acceptance also uses scoped gymadmin/gymstore.
INSERT INTO system_dept(name,parent_id,sort,status,creator,updater,tenant_id)
VALUES('健身总部',0,90,0,'gym-seed','gym-seed',@tenant_id);
SET @gym_bootstrap_hq=LAST_INSERT_ID();
INSERT INTO system_role(id,name,code,sort,data_scope,status,type,remark,tenant_id,creator,updater)
VALUES(1,'开发超级管理员','super_admin',0,1,0,1,'仅开发初始化使用',@tenant_id,'gym-seed','gym-seed');
INSERT INTO system_users(id,username,password,nickname,dept_id,status,tenant_id,creator,updater)
VALUES(1,'admin','$2a$10$DobR27ZollT9tVNGf2.FM.3A2S8Azi9IJbzYic6g660D6ta8LS8G2','开发管理员',@gym_bootstrap_hq,0,@tenant_id,'gym-seed','gym-seed');
INSERT INTO system_user_role(user_id,role_id,tenant_id,creator,updater)
VALUES(1,1,@tenant_id,'gym-seed','gym-seed');
COMMIT;
