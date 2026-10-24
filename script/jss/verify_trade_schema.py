#!/usr/bin/env python3
"""Verify current trade/delivery/after-sale/brokerage storage in a disposable MySQL schema."""
import re
import secrets
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_trade_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_trade_verify_[0-9a-f]{12}', database)
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
            raise AssertionError('Duplicate active trade identity was accepted')

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V008__trade_schema.sql').read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        models = mapped_models(ROOT / 'yudao-module-mall/yudao-module-trade/src/main/java')
        assert len(models) == 15 and models.keys() == columns.keys()
        assert 'properties' in models['trade_after_sale']
        assert 'property_id' not in models['trade_order_item']
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0'
        checks.append('fifteen trade models include qualified nested fields and audit/tenant columns, without seeds')

        cart = 'INSERT INTO trade_cart (tenant_id,user_id,spu_id,sku_id,count) VALUES '
        execute(cart + '(1,10,1,1,2);')
        duplicate(cart + '(1,10,1,1,2);')
        execute(cart + '(2,10,1,1,3),(1,11,1,1,1);')
        assert execute('SELECT selected+0,count FROM trade_cart WHERE tenant_id=1 AND user_id=10') == '1\t2'
        execute("UPDATE trade_cart SET deleted=b'1' WHERE tenant_id=1 AND user_id=10;")
        execute(cart + '(1,10,1,1,1);')
        checks.append('cart active owner/SKU keys isolate tenants/members and allow recreation after deletion')

        execute("INSERT INTO trade_config (tenant_id,after_sale_refund_reasons,brokerage_poster_urls,brokerage_withdraw_types) VALUES (1,'[\"isolated reason\"]','[\"https://example.invalid/poster.png\"]','1,2');")
        duplicate('INSERT INTO trade_config (tenant_id) VALUES (1);')
        execute('INSERT INTO trade_config (tenant_id) VALUES (2);')
        assert execute('SELECT delivery_express_free_enabled+0,delivery_pick_up_enabled+0,brokerage_enabled+0,brokerage_withdraw_types,JSON_LENGTH(after_sale_refund_reasons) FROM trade_config WHERE tenant_id=1') == '0\t0\t0\t1,2\t1'
        execute("UPDATE trade_config SET deleted=b'1' WHERE tenant_id=1; INSERT INTO trade_config (tenant_id) VALUES (1);")
        checks.append('one active configuration per tenant preserves JSON reasons/images, CSV withdrawal types and disabled defaults')

        execute("INSERT INTO trade_delivery_express_template (id,tenant_id,name) VALUES (1,1,'isolated');")
        execute("INSERT INTO trade_delivery_express_template_charge (tenant_id,template_id,area_ids,start_count,start_price,extra_count,extra_price) VALUES (1,1,'110000,120000',0.5,100,0.25,50);")
        assert execute('SELECT area_ids,start_count,extra_count FROM trade_delivery_express_template_charge') == '110000,120000\t0.5\t0.25'
        execute("INSERT INTO trade_delivery_pick_up_store (tenant_id,name,status,opening_time,closing_time,verify_user_ids,latitude,longitude) VALUES (1,'isolated',0,'08:30:00','21:00:00','10,11',31.2,121.5);")
        assert execute('SELECT opening_time,closing_time,verify_user_ids FROM trade_delivery_pick_up_store') == '08:30:00\t21:00:00\t10,11'
        checks.append('shipping regions/verifiers use CSV, fractional weights use numeric fields and store hours use TIME')

        execute("INSERT INTO trade_order (id,tenant_id,no,user_id,status,adjust_price,give_coupon_ids,give_coupon_template_counts) VALUES (1,1,'O-isolated',10,0,-10,'1,2','{\"7\":2}');")
        duplicate("INSERT INTO trade_order (tenant_id,no,user_id,status) VALUES (2,'O-isolated',10,0);")
        assert execute('SELECT pay_status+0,pay_time IS NULL,finish_time IS NULL,delivery_time IS NULL,receive_time IS NULL,adjust_price,give_coupon_ids FROM trade_order') == '0\t1\t1\t1\t1\t-10\t1,2'
        execute("INSERT INTO trade_order_item (id,tenant_id,user_id,order_id,spu_id,sku_id,count,properties) VALUES (1,1,10,1,1,1,2,'[{\"propertyId\":1,\"valueName\":\"蓝色\"}]');")
        assert execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(properties,'$[0].valueName')),after_sale_status FROM trade_order_item") == '蓝色\t0'
        checks.append('orders start unpaid/unfulfilled and keep signed adjustments, gift JSON/CSV and item property JSON')

        execute("INSERT INTO trade_after_sale (tenant_id,no,status,user_id,order_id,order_item_id,spu_id,sku_id,properties,apply_pic_urls) VALUES (1,'AS-isolated',10,10,1,1,1,1,'[{\"propertyId\":1,\"valueName\":\"蓝色\"}]','[\"https://example.invalid/proof.png\"]');")
        assert execute("SELECT JSON_LENGTH(apply_pic_urls),JSON_UNQUOTE(JSON_EXTRACT(properties,'$[0].valueName')),audit_time IS NULL,refund_time IS NULL,receive_time IS NULL FROM trade_after_sale") == '1\t蓝色\t1\t1\t1'
        checks.append('after-sale snapshots retain qualified SKU properties and evidence without invented audit/refund timestamps')

        execute('INSERT INTO trade_brokerage_user (id,tenant_id,brokerage_price,frozen_price) VALUES (77,1,100,50);')
        execute('UPDATE trade_brokerage_user SET brokerage_price=brokerage_price-120,frozen_price=frozen_price-60 WHERE id=77;')
        assert execute('SELECT id,brokerage_price,frozen_price,brokerage_enabled+0 FROM trade_brokerage_user') == '77\t-20\t-10\t0'
        assert execute("SELECT extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_brokerage_user' AND column_name='id'") == ''
        checks.append('brokerage IDs remain member IDs and signed reversal debts are supported without enabling accounts')

        execute("INSERT INTO trade_order_log (tenant_id,order_id,content) VALUES (1,1,'<text>isolated 😀</text>');")
        execute("INSERT INTO trade_after_sale_log (tenant_id,after_sale_id,content) VALUES (1,1,'<text>audit</text>');")
        assert execute('SELECT content FROM trade_order_log') == '<text>isolated 😀</text>'
        checks.append('order and after-sale audit logs retain text payloads independently of JSON snapshots')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} trade schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
