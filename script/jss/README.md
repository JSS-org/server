# JSS 隔离数据库验证

`mysql_sandbox.py` 仅管理本仓库 `.runtime/jss-mysql` 下的临时实例，绑定 `127.0.0.1:13316`，不使用默认业务端口。首次启动生成随机本地 root 密码，保存在被 Git 忽略的 `client.cnf`，不会打印凭证。启动、状态及停止都核对实际端口和数据目录。

```powershell
python script/jss/mysql_sandbox.py start --mysql-home "C:\Program Files\MySQL\MySQL Server 8.4"
python script/jss/mysql_sandbox.py status
python script/jss/verify_identity_schema.py
python script/jss/verify_member_schema.py
python script/jss/verify_product_schema.py
python script/jss/verify_product_mappers.py
python script/jss/verify_pay_schema.py
python script/jss/verify_migrations.py
python script/jss/mysql_sandbox.py stop
```

身份表验证创建随机命名的专用 schema，使用真实 MySQL 执行迁移并验证唯一性、租户隔离键、撤销后重绑、版本和审计快照，结束时删除自己创建的 schema。它不连接已有业务数据库，也不把这些约束测试作为完整登录/RBAC/Redis 验收。

会员表验证覆盖 `V001__member_schema.sql` 的 11 张表，并逐列对照当前 member DO；还验证空社交登录标识、同租户手机/邮箱唯一性、跨租户独立键、逻辑删除、IPv6 注册地址和签到经验字段。结构以本仓库历史 `5e8988b73bc76fcbc6e49b2b0a3e3c2b3b922b22` 的会员 DDL 为基线，再与当前模型/注册服务校对，不导入历史演示数据。

商品表验证覆盖 V003 的九个当前模型，排除 SKU 内嵌属性对象的字段；验证 JSON/逗号分隔类型、并发条件扣库存 SQL、有效收藏唯一性与删除后重建、浏览可见性及评价字段。SQL 与模型匹配不代表已完成商品 HTTP/支付业务验收。

支付表验证覆盖 V004 的 14 个当前模型，核验商户单号的租户/应用幂等键、全局回调流水号、JSON 渠道配置与原始文本回调的区别、钱包归属唯一键、并发条件扣款 SQL、冻结/解冻、带符号流水金额、充值退款拆分字段及通知日志。迁移不初始化渠道、凭证、资金或成功状态；这些结构检查不代表已完成 Redis 锁、业务事务或第三方支付联调。

`verify_product_mappers.py` 在随机库准备 V001/V003，显式运行 `JssProductMySqlMapperIT` 并核对四项测试确实执行、无跳过且无失败。测试使用生产 Mapper、JSON/CSV 类型处理器、租户拦截器和审计字段填充器，核验自定义查询/逻辑删除也保持租户隔离，以及并发库存修改。默认单元测试不连接 MySQL；该集成测试只接受独立命名的测试 URL，由包装脚本核验实例后提供凭证，结束删除随机 schema。测试日志位于忽略目录 `.runtime/jss-mysql/product-mapper-test.log`。

数据目录保留供后续同一任务重启，工具不会递归删除数据库文件。现有数据目录配置不同或初始化不完整时停止并要求检查，不自动覆盖。

`verify_migrations.py` 在独立随机 schema 中执行真实 Flyway baseline/migrate/validate、校验和漂移拒绝及初始化守卫，连接密码仅从隔离实例的本地配置进入环境变量。正式部署使用 [JSS 迁移命令](../../sql/jss/README.md)，测试工具不会替用户初始化业务库。
