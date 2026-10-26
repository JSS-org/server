-- Product configuration only; no default catalog, rewards, stock consumption, or role grants.
CREATE TABLE IF NOT EXISTS gym_point_product (
 id bigint NOT NULL AUTO_INCREMENT,
 store_id bigint NOT NULL,
 card_product_id bigint NOT NULL,
 name varchar(100) NOT NULL,
 required_points bigint NOT NULL,
 stock int DEFAULT NULL,
 enabled bit(1) NOT NULL DEFAULT b'0',
 revision bigint NOT NULL DEFAULT 1,
 creator varchar(64) NOT NULL DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 KEY idx_point_product_store (tenant_id,store_id,deleted,enabled,id),
 CONSTRAINT ck_point_product_cost CHECK (required_points BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_point_product_stock CHECK (stock IS NULL OR stock>=0),
 CONSTRAINT ck_point_product_revision CHECK (revision BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_point_product_name CHECK (CHAR_LENGTH(TRIM(name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @gym_point_root = (SELECT id FROM system_menu WHERE name='健身管理' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '积分兑换商品-管理','gym:points:products',3,66,@gym_point_root,'','#',NULL,NULL,0,b'1',b'1',b'1','gym-point-product',NOW(),'gym-point-product',NOW(),b'0'
WHERE @gym_point_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='gym:points:products' AND m.deleted=b'0');
