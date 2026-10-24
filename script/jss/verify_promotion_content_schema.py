#!/usr/bin/env python3
"""Check decoration/content/customer-service storage against current models in owned MySQL."""
import re
import secrets
from concurrent.futures import ThreadPoolExecutor
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_content_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_content_verify_[0-9a-f]{12}', database)
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
            raise AssertionError('Duplicate active content identity was accepted')

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V005__promotion_content_schema.sql').read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table, set()).add(column)
        models = {}
        source = ROOT / 'yudao-module-mall/yudao-module-promotion/src/main/java/cn/iocoder/yudao/module/promotion/dal/dataobject'
        for area in ['article', 'banner', 'diy', 'kefu']:
            models.update(mapped_models(source / area))
        assert len(models) == 7 and models.keys() == columns.keys()
        for table, expected in models.items():
            assert not expected - columns[table], (table, expected - columns[table])
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0'
        checks.append('seven content/decoration/customer-service models match, without seeded content')

        execute("INSERT INTO promotion_diy_template (id,tenant_id,name) VALUES (1,1,'one'),(2,1,'two'),(3,2,'one');")
        assert execute('SELECT SUM(used+0),COUNT(used_time) FROM promotion_diy_template') == '0\t0'
        def activate(identifier):
            try:
                return execute(f"UPDATE promotion_diy_template SET used=b'1' WHERE id={identifier}; SELECT ROW_COUNT();")
            except RuntimeError as error:
                assert 'Duplicate entry' in str(error), str(error)
                return 'conflict'
        with ThreadPoolExecutor(max_workers=2) as pool:
            assert sorted(pool.map(activate, [1, 2])) == ['1', 'conflict']
        assert activate(3) == '1'
        execute("UPDATE promotion_diy_template SET used=b'0' WHERE tenant_id=1; UPDATE promotion_diy_template SET used=b'1' WHERE id=2;")
        assert execute('SELECT id FROM promotion_diy_template WHERE tenant_id=1 AND used=1') == '2'
        duplicate("INSERT INTO promotion_diy_template (tenant_id,name) VALUES (1,'one');")
        execute("UPDATE promotion_diy_template SET deleted=b'1' WHERE id=1; INSERT INTO promotion_diy_template (tenant_id,name) VALUES (1,'one');")
        checks.append('concurrent activation permits one used template per tenant and preserves release/reuse')

        execute("INSERT INTO promotion_diy_page (id,tenant_id,name) VALUES (1,1,'standalone');")
        duplicate("INSERT INTO promotion_diy_page (tenant_id,name) VALUES (1,'standalone');")
        execute("INSERT INTO promotion_diy_page (tenant_id,template_id,name) VALUES (1,2,'standalone'),(1,3,'standalone'),(2,NULL,'standalone');")
        execute("UPDATE promotion_diy_page SET deleted=b'1' WHERE id=1; INSERT INTO promotion_diy_page (tenant_id,name) VALUES (1,'standalone');")
        checks.append('standalone page uniqueness leaves template pages and other tenants independent')

        execute("UPDATE promotion_diy_template SET preview_pic_urls='https://example.invalid/1.png,https://example.invalid/2.png',property='{\"tabBar\":{}}' WHERE id=2;")
        assert execute('SELECT preview_pic_urls,property FROM promotion_diy_template WHERE id=2') == 'https://example.invalid/1.png,https://example.invalid/2.png\t{"tabBar":{}}'
        execute("INSERT INTO promotion_article (tenant_id,category_id,title,content) VALUES (1,1,'isolated','<p>正文 😀</p>');")
        assert execute('SELECT content,browse_count,recommend_hot+0,recommend_banner+0 FROM promotion_article') == '<p>正文 😀</p>\t0\t0\t0'
        checks.append('preview images retain CSV and decoration/article text supports current payload formats')

        execute('INSERT INTO promotion_kefu_conversation (id,tenant_id,user_id) VALUES (1,1,10),(2,2,10);')
        duplicate('INSERT INTO promotion_kefu_conversation (tenant_id,user_id) VALUES (1,10);')
        assert execute('SELECT admin_pinned+0,user_deleted+0,admin_deleted+0,admin_unread_message_count FROM promotion_kefu_conversation WHERE id=1') == '0\t0\t0\t0'
        execute("UPDATE promotion_kefu_conversation SET admin_deleted=b'1' WHERE id=1;")
        duplicate('INSERT INTO promotion_kefu_conversation (tenant_id,user_id) VALUES (1,10);')
        execute("UPDATE promotion_kefu_conversation SET deleted=b'1' WHERE id=1; INSERT INTO promotion_kefu_conversation (tenant_id,user_id) VALUES (1,10);")
        checks.append('conversation ownership is tenant scoped; admin hiding preserves its identity')

        execute("INSERT INTO promotion_kefu_message (tenant_id,conversation_id,sender_id,sender_type,receiver_id,receiver_type,content_type,content) SELECT 1,id,10,1,20,2,1,'<text>isolated 😀</text>' FROM promotion_kefu_conversation WHERE tenant_id=1 AND deleted=0;")
        assert execute('SELECT read_status+0,content FROM promotion_kefu_message') == '0\t<text>isolated 😀</text>'
        def increment(_):
            execute('UPDATE promotion_kefu_conversation SET admin_unread_message_count=admin_unread_message_count+1 WHERE id=2 AND tenant_id=2;')
        with ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(increment, [1, 2]))
        assert execute('SELECT admin_unread_message_count FROM promotion_kefu_conversation WHERE id=2') == '2'
        checks.append('new messages start unread and concurrent SQL increments retain both notifications')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} promotion content schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
