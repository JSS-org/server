# JSS 版本迁移

独立 Flyway 13.10.3 发布工具，历史表为 `flyway_jss_schema_history`，与现有 gym 迁移历史分开。Java 应用启动不运行迁移，也不执行开发 seed。

| 版本 | 内容 |
| --- | --- |
| V001 | 当前 11 张会员表；以仓库历史会员 DDL 为基础，补齐现有模型、社交注册空手机号及有效登录标识唯一约束 |
| V002 | 会员员工关联、绑定审计和三项 JSS 权限定义；不创建绑定、账号或角色授权 |

先准备已有 system/infra 框架基座，再显式指定迁移连接：

```powershell
$env:JSS_DB_HOST = '127.0.0.1'
$env:JSS_DB_PORT = '3306'
$env:JSS_DB_NAME = '明确的目标库名'
$env:JSS_DB_USER = '迁移账号'
$env:JSS_DB_PASSWORD = '通过安全环境配置提供的密码'
$env:JSS_MYSQL_HOME = 'C:\Program Files\MySQL\MySQL Server 8.4'
# Maven 不在 PATH 时，可用 JSS_MAVEN_COMMAND 指向本机 mvn/mvn.cmd。
python script/jss/database.py info
python script/jss/database.py init
python script/jss/database.py migrate
python script/jss/database.py validate
```

`init` 仅接管已存在框架基座、尚无会员/JSS/商城/支付表且没有 JSS 迁移历史的 schema，显式 baseline 到 0 后执行版本脚本。重复初始化、缺框架表或已有未托管业务表都会拒绝；已有会员/JSS 数据应先核对结构、历史和备份，不能靠自动 baseline、重放原初始化 SQL 或手工跳过版本接管。

`migrate`/`validate` 要求已有历史。自动 baseline、乱序迁移、clean 均禁用；CLI 不提供 repair、clean 或 seed。MySQL DDL 可能隐式提交，失败后应检查当前结构和 Flyway 历史，按审核后的前向迁移或备份恢复处理。后续追加新版本，不修改已应用的 V001/V002。

原 `sql/jss/001-identity.sql` 已移入 V002，Git 保留原文件历史；请统一通过本工具部署，避免业务库出现无版本记录的表。
