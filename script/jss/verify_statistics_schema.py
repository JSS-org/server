#!/usr/bin/env python3
"""Validate daily statistics storage on the owned, isolated MySQL instance."""
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
            raise AssertionError('Duplicate active daily aggregate was accepted')

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V009__statistics_schema.sql').read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        models = mapped_models(ROOT / 'yudao-module-mall/yudao-module-statistics/src/main/java')
        assert len(models) == 2 and models.keys() == columns.keys()
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0'
        checks.append('both statistics models have all persisted columns without fabricated aggregates')

        product = "INSERT INTO product_statistics (tenant_id,time,spu_id) VALUES "
        execute(product + "(1,'2026-10-23',10);")
        duplicate(product + "(1,'2026-10-23',10);")
        execute(product + "(2,'2026-10-23',10),(1,'2026-10-24',10),(1,'2026-10-23',11);")
        assert execute('SELECT order_pay_price,browse_convert_percent FROM product_statistics WHERE id=1') == '0\t0'
        execute("UPDATE product_statistics SET deleted=b'1' WHERE id=1;")
        execute(product + "(1,'2026-10-23',10);")
        checks.append('daily product uniqueness isolates tenants/products/dates and allows recomputation after deletion')

        trade = "INSERT INTO trade_statistics (tenant_id,time) VALUES "
        execute(trade + "(1,'2026-10-23 01:00:00');")
        duplicate(trade + "(1,'2026-10-23 23:59:59');")
        execute(trade + "(2,'2026-10-23 01:00:00'),(1,'2026-10-24 01:00:00');")
        execute("UPDATE trade_statistics SET deleted=b'1' WHERE id=1;")
        execute(trade + "(1,'2026-10-23 18:00:00');")
        checks.append('trade aggregates are unique by calendar day per tenant, not by exact timestamp')

        execute("UPDATE trade_statistics SET brokerage_settlement_price=-20 WHERE tenant_id=1 AND deleted=b'0';")
        assert execute("SELECT MIN(brokerage_settlement_price) FROM trade_statistics WHERE tenant_id=1 AND deleted=b'0'") == '-20'
        checks.append('signed commission reversals are retained without unsigned truncation')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} statistics schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
