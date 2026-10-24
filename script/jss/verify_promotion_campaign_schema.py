#!/usr/bin/env python3
"""Validate current campaign storage formats/keys against disposable real MySQL."""
import re
import secrets
from concurrent.futures import ThreadPoolExecutor
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_campaign_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_campaign_verify_[0-9a-f]{12}', database)
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    checks = []

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement)

    def duplicate(statement):
        try:
            execute(statement)
        except RuntimeError as error:
            assert 'Duplicate entry' in str(error), str(error)
        else:
            raise AssertionError('Duplicate active campaign identity was accepted')

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V006__promotion_campaign_schema.sql').read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        source = ROOT / 'yudao-module-mall/yudao-module-promotion/src/main/java/cn/iocoder/yudao/module/promotion/dal/dataobject'
        models = {}
        for area in ['bargain', 'combination', 'coupon', 'discount', 'point', 'reward', 'seckill']:
            models.update(mapped_models(source / area))
        assert len(models) == 16 and models.keys() == columns.keys()
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0'
        assert 'give_coupon_template_counts' not in columns['promotion_reward_activity']
        checks.append('sixteen current campaign models match without campaign/coupon/stock seeds or nested rule columns')

        execute("INSERT INTO promotion_coupon_template (id,tenant_id,name,status,total_count,take_limit_count,product_scope_values) VALUES (1,1,'isolated',0,-1,-1,'11,12');")
        execute('UPDATE promotion_coupon_template SET take_count=take_count+1 WHERE id=1 AND (take_count<total_count OR total_count=-1);')
        assert execute('SELECT total_count,take_limit_count,take_count,use_count,product_scope_values FROM promotion_coupon_template') == '-1\t-1\t1\t0\t11,12'
        execute("INSERT INTO promotion_reward_activity (tenant_id,name,status,product_scope_values,rules) VALUES (1,'isolated',0,'11,12','[{\"limit\":100,\"discountPrice\":10,\"giveCouponTemplateCounts\":{\"1\":2}}]');")
        execute("INSERT INTO promotion_seckill_config (tenant_id,name,start_time,end_time,slider_pic_urls,status) VALUES (1,'isolated','08:00','10:00','[\"https://example.invalid/1.png\"]',0);")
        assert execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(rules,'$[0].giveCouponTemplateCounts.\"1\"')),product_scope_values FROM promotion_reward_activity") == '2\t11,12'
        assert execute('SELECT JSON_LENGTH(slider_pic_urls) FROM promotion_seckill_config') == '1'
        checks.append('signed unlimited coupon limits and JSON rules/images remain distinct from CSV scope/config lists')

        for area in ['seckill', 'point', 'bargain']:
            table = f'promotion_{area}_activity'
            name = ',name' if area != 'point' else ''
            value = ",'isolated'" if area != 'point' else ''
            execute(f'INSERT INTO {table} (id,tenant_id,status,stock,total_stock{name}) VALUES (1,1,0,3,3{value});')
            def consume(_):
                return execute(f'UPDATE {table} SET stock=stock-2 WHERE id=1 AND tenant_id=1 AND stock>=2; SELECT ROW_COUNT();')
            with ThreadPoolExecutor(max_workers=2) as pool:
                assert sorted(pool.map(consume, [1, 2])) == ['0', '1']
            assert execute(f'SELECT stock,total_stock FROM {table} WHERE id=1') == '1\t3'
        checks.append('seckill, point and bargain conditional stock SQL is atomic under competing decrements')

        for area in ['combination', 'discount', 'point', 'seckill']:
            table = f'promotion_{area}_product'
            statement = f'INSERT INTO {table} (tenant_id,activity_id,spu_id,sku_id) VALUES '
            execute(statement + '(1,1,1,1);')
            duplicate(statement + '(1,1,1,1);')
            execute(statement + '(2,1,1,1),(1,2,1,1);')
            execute(f"UPDATE {table} SET deleted=b'1' WHERE tenant_id=1 AND activity_id=1;")
            execute(statement + '(1,1,1,1);')
        execute("UPDATE promotion_seckill_product SET config_ids='1,2' WHERE tenant_id=1 AND activity_id=1 AND deleted=0;")
        assert execute('SELECT config_ids FROM promotion_seckill_product WHERE tenant_id=1 AND activity_id=1 AND deleted=0') == '1,2'
        checks.append('active activity/SKU keys permit other activities/tenants and replacement after deletion')

        help_insert = 'INSERT INTO promotion_bargain_help (tenant_id,activity_id,record_id,user_id,reduce_price) VALUES '
        execute(help_insert + '(1,1,1,10,1);')
        duplicate(help_insert + '(1,1,1,10,1);')
        execute(help_insert + '(2,1,1,10,1),(1,1,2,10,1);')
        coupon_insert = "INSERT INTO promotion_coupon (tenant_id,template_id,user_id,name,status,product_scope_values) VALUES "
        execute(coupon_insert + "(1,1,10,'first',1,'11,12'),(1,1,10,'second',1,'11,12');")
        assert execute('SELECT COUNT(*),COUNT(use_time),COUNT(use_order_id) FROM promotion_coupon WHERE tenant_id=1 AND user_id=10') == '2\t0\t0'
        checks.append('one help per record/user is enforced while valid multiple coupons per template/member remain possible')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} promotion campaign schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
