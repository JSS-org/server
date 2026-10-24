#!/usr/bin/env python3
"""Validate current daily statistics tables, defaults and keys on disposable MySQL."""
import re
import secrets
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_statistics_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_statistics_verify_[0-9a-f]{12}', database)
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
            raise AssertionError('Duplicate active daily statistics accepted')

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V009__statistics_schema.sql').read_text(encoding='utf8'))
        models = mapped_models(ROOT / 'yudao-module-mall/yudao-module-statistics/src/main/java')
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        assert len(models) == 2 and models.keys() == columns.keys()
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0'
        assert execute("SELECT table_name,data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='time' ORDER BY table_name") == 'product_statistics\tdate\ntrade_statistics\tdatetime'
        checks.append('two current statistics models match with correct LocalDate/LocalDateTime storage and no metrics seeded')

        product = 'INSERT INTO product_statistics (tenant_id,spu_id,time) VALUES '
        execute(product + "(1,1,'2026-10-23');")
        duplicate(product + "(1,1,'2026-10-23');")
        execute(product + "(2,1,'2026-10-23'),(1,2,'2026-10-23'),(1,1,'2026-10-24');")
        execute("UPDATE product_statistics SET deleted=b'1' WHERE tenant_id=1 AND spu_id=1 AND time='2026-10-23';")
        execute(product + "(1,1,'2026-10-23');")
        checks.append('product snapshots are unique per tenant/product/day and can be recalculated after deletion')

        trade = 'INSERT INTO trade_statistics (tenant_id,time) VALUES '
        execute(trade + "(1,'2026-10-23 12:00:00');")
        duplicate(trade + "(1,'2026-10-23 18:00:00');")
        execute(trade + "(2,'2026-10-23 12:00:00'),(1,'2026-10-24 12:00:00');")
        execute("UPDATE trade_statistics SET deleted=b'1' WHERE tenant_id=1 AND DATE(time)='2026-10-23';")
        execute(trade + "(1,'2026-10-23 18:00:00');")
        checks.append('trade daily uniqueness follows calendar day, isolates tenants and supports reviewed recalculation')

        for table, fields in models.items():
            metric_fields = sorted(fields - {'id','time','spu_id','creator','create_time','updater','update_time','deleted','tenant_id'})
            assert execute('SELECT COUNT(*) FROM ' + table + ' WHERE ' + ' OR '.join(f'{field} IS NULL OR {field}<>0' for field in metric_fields)) == '0'
        execute("UPDATE trade_statistics SET order_pay_price=100,recharge_pay_price=30,wallet_pay_price=20,brokerage_settlement_price=-5,after_sale_refund_price=10 WHERE tenant_id=1 AND time='2026-10-23 18:00:00';")
        assert execute("SELECT SUM(order_pay_price+recharge_pay_price),SUM(wallet_pay_price+brokerage_settlement_price+after_sale_refund_price) FROM trade_statistics WHERE tenant_id=1 AND deleted=0 AND DATE(time)='2026-10-23'") == '130\t25'
        checks.append('non-null zero metrics support aggregate arithmetic and preserve signed settlement adjustments')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} statistics schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
