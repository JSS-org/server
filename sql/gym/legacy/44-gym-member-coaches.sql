-- Explicit store-specific responsibility. Revocation keeps the relation's audit identity.
CREATE TABLE IF NOT EXISTS gym_member_coach (
 id bigint NOT NULL AUTO_INCREMENT,
 member_id bigint NOT NULL,
 store_id bigint NOT NULL,
 employee_id bigint NOT NULL,
 active boolean NOT NULL DEFAULT true,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted boolean NOT NULL DEFAULT false,
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_member_coach (tenant_id,member_id,store_id,employee_id),
 KEY idx_member_coach_employee (tenant_id,employee_id,active)
);
