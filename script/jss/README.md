# JSS 隔离数据库验证

`mysql_sandbox.py` 仅管理本仓库 `.runtime/jss-mysql` 下的临时实例，绑定 `127.0.0.1:13316`，不使用默认业务端口。首次启动生成随机本地 root 密码，保存在被 Git 忽略的 `client.cnf`，不会打印凭证。启动、状态及停止都核对实际端口和数据目录。

```powershell
python script/jss/mysql_sandbox.py start --mysql-home "C:\Program Files\MySQL\MySQL Server 8.4"
python script/jss/mysql_sandbox.py status
python script/jss/verify_identity_schema.py
python script/jss/mysql_sandbox.py stop
```

身份表验证创建随机命名的专用 schema，使用真实 MySQL 执行迁移并验证唯一性、租户隔离键、撤销后重绑、版本和审计快照，结束时删除自己创建的 schema。它不连接已有业务数据库，也不把这些约束测试作为完整登录/RBAC/Redis 验收。

数据目录保留供后续同一任务重启，工具不会递归删除数据库文件。现有数据目录配置不同或初始化不完整时停止并要求检查，不自动覆盖。
