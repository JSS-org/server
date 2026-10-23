#!/usr/bin/env python3
"""Validate current product columns, storage formats and key SQL behavior on isolated MySQL."""
import re
import secrets
from concurrent.futures import ThreadPoolExecutor
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_product_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_product_verify_[0-9a-f]{12}', database)
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    checks = []

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement)

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V003__product_schema.sql').read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        models = mapped_models(ROOT / 'yudao-module-mall/yudao-module-product/src/main/java')
        assert len(models) == 9 and models.keys() == columns.keys()
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
        assert 'property_id' not in models['product_sku']  # Nested JSON members are not SQL columns.
        checks.append('all nine current product models and inherited audit/tenant columns are present')
        assert execute('SELECT SUM(table_rows) FROM information_schema.tables WHERE table_schema=DATABASE()') == '0'
        checks.append('migration inserts no goods, prices or stock')
        execute("INSERT INTO product_spu (id,tenant_id,name,description,category_id,slider_pic_urls,delivery_types) "
                "VALUES (1,1,'商品验证','<p>detail</p>',1,'[\"https://example.invalid/a.png\"]','1,2');")
        execute("INSERT INTO product_sku (id,tenant_id,spu_id,properties,stock) "
                "VALUES (1,1,1,'[{\"propertyId\":1,\"propertyName\":\"颜色\",\"valueId\":2,\"valueName\":\"蓝色\"}]',3);")
        assert execute('SELECT JSON_LENGTH(slider_pic_urls),delivery_types FROM product_spu WHERE id=1') == '1\t1,2'
        assert execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(properties,'$[0].valueName')) FROM product_sku WHERE id=1") == '蓝色'
        checks.append('JSON value objects and comma-separated delivery lists use their correct storage formats')
        def consume(_):
            return execute('UPDATE product_sku SET stock=stock-2,sales_count=sales_count+2 WHERE tenant_id=1 AND id=1 AND stock>=2; SELECT ROW_COUNT();')
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(consume, [1, 2]))
        assert sorted(results) == ['0', '1']
        assert execute('SELECT stock,sales_count FROM product_sku WHERE id=1') == '1\t2'
        checks.append('conditional stock SQL is atomic under concurrent decrements')
        execute('INSERT INTO product_favorite (tenant_id,user_id,spu_id) VALUES (1,10,1);')
        try:
            execute('INSERT INTO product_favorite (tenant_id,user_id,spu_id) VALUES (1,10,1);')
            raise AssertionError('Duplicate active favorite was accepted')
        except RuntimeError as error:
            assert 'Duplicate entry' in str(error)
        execute('INSERT INTO product_favorite (tenant_id,user_id,spu_id) VALUES (2,10,1);')
        execute("UPDATE product_favorite SET deleted=b'1' WHERE tenant_id=1; INSERT INTO product_favorite (tenant_id,user_id,spu_id) VALUES (1,10,1);")
        checks.append('favorites are unique per active member/tenant and can be recreated after deletion')
        execute('INSERT INTO product_browse_history (tenant_id,user_id,spu_id) VALUES (1,10,1);')
        assert execute('SELECT user_deleted+0 FROM product_browse_history') == '0'
        execute("INSERT INTO product_comment (tenant_id,spu_id,sku_id,sku_pic_url,pic_urls) VALUES (1,1,1,'https://example.invalid/sku.png','[]');")
        assert execute('SELECT visible+0,reply_status+0 FROM product_comment') == '1\t0'
        checks.append('browse visibility and current comment image/reply fields have usable defaults')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} product schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
