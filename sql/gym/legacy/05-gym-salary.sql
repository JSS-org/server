-- 薪资模板版本与员工配置；通过员工行锁串行化同一员工的区间写入。
CREATE TABLE IF NOT EXISTS gym_salary_template (
  id bigint NOT NULL AUTO_INCREMENT,
  family_id bigint DEFAULT NULL,
  name varchar(100) NOT NULL,
  version int NOT NULL,
  base_salary decimal(12,2) NOT NULL,
  effective_from date NOT NULL,
  effective_to date DEFAULT NULL,
  status varchar(16) NOT NULL,
  rules text NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, UNIQUE KEY uk_salary_version (tenant_id, family_id, version)
);
CREATE TABLE IF NOT EXISTS gym_salary_assignment (
  id bigint NOT NULL AUTO_INCREMENT,
  employee_id bigint NOT NULL,
  store_id bigint NOT NULL,
  template_id bigint NOT NULL,
  effective_from date NOT NULL,
  effective_to date DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
, KEY idx_salary_employee (tenant_id, employee_id, effective_from),
  KEY idx_salary_store (tenant_id, store_id)
);
