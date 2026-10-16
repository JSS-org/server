-- 每租户一个事务锁：协调全部门店权益发放与新店创建/启用，防止并发漏关联。
CREATE TABLE IF NOT EXISTS gym_business_mutex (
  tenant_id bigint NOT NULL,
  id bigint NOT NULL,
  PRIMARY KEY (tenant_id,id)
);
