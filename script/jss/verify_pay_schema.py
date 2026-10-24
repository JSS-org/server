#!/usr/bin/env python3
"""Check current payment storage and constraints on the owned, disposable MySQL instance."""
import re
import secrets
from concurrent.futures import ThreadPoolExecutor
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_pay_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_pay_verify_[0-9a-f]{12}', database)
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
            raise AssertionError('Duplicate payment identity was accepted')

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V004__pay_schema.sql').read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        models = mapped_models(ROOT / 'yudao-module-pay/src/main/java')
        assert len(models) == 14 and models.keys() == columns.keys()
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
        checks.append('all fourteen current payment models include persisted audit/tenant fields')
        for table in models:
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0', table
        checks.append('migration creates no channels, credentials, funds or successful payments')

        execute("INSERT INTO pay_app (id,tenant_id,app_key,name,status) VALUES (1,1,'verify','isolated',0);")
        duplicate("INSERT INTO pay_app (tenant_id,app_key,name,status) VALUES (1,'verify','duplicate',0);")
        execute("INSERT INTO pay_app (tenant_id,app_key,name,status) VALUES (2,'verify','other tenant',0);")
        execute("UPDATE pay_app SET deleted=b'1' WHERE id=1; INSERT INTO pay_app (tenant_id,app_key,name,status) VALUES (1,'verify','replacement',0);")
        execute("INSERT INTO pay_channel (tenant_id,app_id,code,status,config) VALUES (1,1,'isolated',0,'{\"@class\":\"test-only\",\"sandbox\":true}');")
        duplicate("INSERT INTO pay_channel (tenant_id,app_id,code,status,config) VALUES (1,1,'isolated',0,'{}');")
        execute("INSERT INTO pay_channel (tenant_id,app_id,code,status,config) VALUES (1,2,'isolated',0,'{}'),(2,1,'isolated',0,'{}');")
        checks.append('active application/channel keys are tenant/application scoped')

        order = "INSERT INTO pay_order (tenant_id,app_id,merchant_order_id,subject,price,expire_time) VALUES "
        execute(order + "(1,1,'merchant-1','isolated',100,'2030-01-01');")
        duplicate(order + "(1,1,'merchant-1','duplicate',100,'2030-01-01');")
        execute(order + "(2,1,'merchant-1','other tenant',100,'2030-01-01'),(1,2,'merchant-1','other app',100,'2030-01-01');")
        refund = "INSERT INTO pay_refund (tenant_id,app_id,order_id,no,merchant_refund_id,pay_price,refund_price) VALUES "
        execute(refund + "(1,1,1,'R1','merchant-1',100,20);")
        duplicate(refund + "(1,1,1,'R2','merchant-1',100,20);")
        duplicate(refund + "(2,1,1,'R1','other',100,20);")
        execute(refund + "(2,1,1,'R3','merchant-1',100,20);")
        transfer = "INSERT INTO pay_transfer (tenant_id,app_id,no,merchant_transfer_id,subject,price) VALUES "
        execute(transfer + "(1,1,'T1','merchant-1','isolated',20);")
        duplicate(transfer + "(1,1,'T2','merchant-1','duplicate',20);")
        execute(transfer + "(2,1,'T3','merchant-1','other tenant',20);")
        assert execute('SELECT COUNT(*) FROM pay_order WHERE success_time IS NOT NULL OR no IS NOT NULL') == '0'
        assert execute('SELECT COUNT(*) FROM pay_refund WHERE success_time IS NOT NULL') == '0'
        assert execute('SELECT COUNT(*) FROM pay_transfer WHERE success_time IS NOT NULL') == '0'
        checks.append('merchant retries are unique within application/tenant; callback numbers are global; success times remain null')

        execute("INSERT INTO pay_order_extension (tenant_id,no,order_id,channel_id,channel_extras,channel_notify_data) VALUES "
                "(1,'P1',1,1,'{\"scene\":\"H5\"}','<xml>callback</xml>');")
        execute("UPDATE pay_transfer SET channel_extras='{\"scene\":\"app\"}',channel_notify_data='<xml>transfer</xml>',channel_package_info='opaque-package' WHERE no='T1';")
        assert execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(channel_extras,'$.scene')),channel_notify_data FROM pay_order_extension") == 'H5\t<xml>callback</xml>'
        assert execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(channel_extras,'$.scene')),channel_package_info FROM pay_transfer WHERE no='T1'") == 'app\topaque-package'
        checks.append('structured channel config/extras use JSON while raw callbacks/package payloads remain text')

        execute('INSERT INTO pay_wallet (id,tenant_id,user_id,user_type,balance) VALUES (1,1,10,1,100),(2,2,10,1,0),(3,1,10,2,0);')
        duplicate('INSERT INTO pay_wallet (tenant_id,user_id,user_type) VALUES (1,10,1);')
        def consume(_):
            return execute('UPDATE pay_wallet SET balance=balance-80,total_expense=total_expense+80 WHERE id=1 AND tenant_id=1 AND balance>=80; SELECT ROW_COUNT();')
        with ThreadPoolExecutor(max_workers=2) as pool:
            assert sorted(pool.map(consume, [1, 2])) == ['0', '1']
        assert execute('SELECT balance,total_expense FROM pay_wallet WHERE id=1') == '20\t80'
        execute('UPDATE pay_wallet SET balance=balance-20,freeze_price=freeze_price+20 WHERE id=1 AND balance>=20;')
        assert execute('UPDATE pay_wallet SET balance=balance+30,freeze_price=freeze_price-30 WHERE id=1 AND freeze_price>=30; SELECT ROW_COUNT();') == '0'
        execute('UPDATE pay_wallet SET balance=balance+20,freeze_price=freeze_price-20 WHERE id=1 AND freeze_price>=20;')
        assert execute('SELECT balance,freeze_price FROM pay_wallet WHERE id=1') == '20\t0'
        checks.append('wallet owner keys isolate tenant/type and conditional debit/freeze SQL avoids overdrawing')

        transaction = "INSERT INTO pay_wallet_transaction (tenant_id,no,wallet_id,biz_type,biz_id,title,price,balance) VALUES "
        execute(transaction + "(1,'W1',1,1,'B1','debit',-80,20);")
        duplicate(transaction + "(1,'W2',1,1,'B1','retry',-80,20);")
        execute(transaction + "(1,'W3',1,2,'B1','refund',80,100),(2,'W4',2,1,'B1','other tenant',-80,20);")
        assert execute("SELECT price FROM pay_wallet_transaction WHERE no='W1'") == '-80'
        execute('INSERT INTO pay_wallet_recharge (tenant_id,wallet_id,total_price,pay_price,bonus_price) VALUES (1,1,120,100,20);')
        assert execute('SELECT pay_status+0,pay_time IS NULL,refund_time IS NULL,refund_total_price,refund_pay_price,refund_bonus_price FROM pay_wallet_recharge') == '0\t1\t1\t0\t0\t0'
        checks.append('wallet transaction identities preserve signed debits and recharge/refund split fields start unpaid')

        execute("INSERT INTO pay_notify_task (id,tenant_id,app_id,type,data_id,next_notify_time,notify_times,max_notify_times) VALUES (1,1,1,1,1,'2026-01-01',0,8);")
        execute("INSERT INTO pay_notify_log (tenant_id,task_id,notify_times,response,status) VALUES (1,1,1,'<html>retry</html>',1);")
        assert execute('SELECT COUNT(*) FROM pay_notify_task WHERE tenant_id=1 AND status=0 AND next_notify_time<NOW() AND notify_times<max_notify_times') == '1'
        assert execute('SELECT response FROM pay_notify_log') == '<html>retry</html>'
        checks.append('notification scheduling and attempt logs preserve arbitrary response text')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} payment schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
