#!/usr/bin/env python3
"""Check only the new feedback migration in a disposable schema on the owned MySQL instance."""
import json
import re
import secrets
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_feedback_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_feedback_verify_[0-9a-f]{12}', database)
    checks = []
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement)

    try:
        migration = (ROOT / 'sql/jss/migration/mysql/V029__member_feedback.sql').read_text(encoding='utf8')
        execute(migration)
        execute(migration)
        actual = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            actual.setdefault(table, set()).add(column)
        models = mapped_models(ROOT / 'yudao-module-member/src/main/java/cn/iocoder/yudao/module/member/dal/dataobject/feedback')
        assert actual.keys() == models.keys()
        for table, columns in models.items():
            assert columns == actual[table], (table, columns, actual[table])
        checks.append('both current models match all persisted and audit/tenant columns; repeated DDL is safe')
        assert execute('SELECT COUNT(*) FROM member_feedback') == '0'
        assert execute('SELECT COUNT(*) FROM member_feedback_attachment') == '0'
        checks.append('migration creates no member, feedback or attachment data')
        execute("INSERT INTO member_feedback(id,user_id,content,tenant_id) VALUES(9007199254740993,9007199254740995,'反馈迁移验证，不涉及真实用户',1)")
        execute("INSERT INTO member_feedback_attachment(id,user_id,content_type,content,tenant_id) VALUES(9007199254740997,9007199254740995,'image/png',UNHEX('89504E470D0A1A0A00010203'),1)")
        assert execute('SELECT HEX(content) FROM member_feedback_attachment WHERE id=9007199254740997') == '89504E470D0A1A0A00010203'
        assert execute('SELECT user_id FROM member_feedback WHERE id=9007199254740993') == '9007199254740995'
        checks.append('private binary payload and 64-bit member/feedback/attachment IDs round trip exactly')
        assert execute('SELECT COUNT(*) FROM member_feedback_attachment WHERE id=9007199254740997 AND user_id=9007199254740995 AND tenant_id=2') == '0'
        assert execute('SELECT COUNT(*) FROM member_feedback_attachment WHERE id=9007199254740997 AND user_id=9007199254740996 AND tenant_id=1') == '0'
        checks.append('explicit member and tenant predicates isolate the same attachment ID')
        claim = 'UPDATE member_feedback_attachment SET feedback_id=9007199254740993 WHERE id=9007199254740997 AND user_id=9007199254740995 AND tenant_id=1 AND feedback_id IS NULL; SELECT ROW_COUNT();'
        assert execute(claim) == '1'
        assert execute(claim) == '0'
        checks.append('an attachment can be claimed only once by the owner predicate')
        execute("START TRANSACTION; INSERT INTO member_feedback(id,user_id,content,tenant_id) VALUES(9007199254740999,9007199254740995,'用于事务回滚验证的反馈内容',1); UPDATE member_feedback_attachment SET feedback_id=9007199254740999 WHERE id=9007199254740997; ROLLBACK;")
        assert execute('SELECT COUNT(*) FROM member_feedback') == '1'
        assert execute('SELECT feedback_id FROM member_feedback_attachment WHERE id=9007199254740997') == '9007199254740993'
        checks.append('rollback preserves the previous feedback and attachment relationship')
        result = {'checks': checks, 'tables': len(models), 'columns': sum(map(len, models.values())), 'version': live['version'], 'port': live['port'], 'scope': 'Owned isolated MySQL DDL/column/binary/transaction evidence only, not live HTTP or full Flyway acceptance'}
    finally:
        sql(f'DROP DATABASE `{database}`;')
    result['remainingSchemas'] = int(sql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{database}'"))
    assert result['remainingSchemas'] == 0
    (ROOT / '.runtime/member-feedback-schema.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
