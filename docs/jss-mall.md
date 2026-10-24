# JSS 商城运行组合

`jss-mall` Maven profile 将 pay、product、promotion、trade、statistics 加入已有 system/infra/gym/member/JSS 容器。它不启用无关 CRM/BPM 等模块，也不执行数据库迁移。

```sh
mvn -Pjss-mall -pl yudao-server -am package -DskipTests
```

本地已成功构建，并核验生成 jar 中包含上述五个模块及 trade-api。普通构建继续使用原有模块组合。

Windows 下运行中的 jar 可能被 Java 锁定，无法原地重新封装。需要保留现有进程时，可以显式使用另一个产物名：

```powershell
& ../.tools/apache-maven-3.9.11/bin/mvn.cmd -Pjss-mall -pl yudao-server -am package '-DskipTests' '-Djss.build.final-name=yudao-server-jss-preview'
```

新包为 `yudao-server/target/yudao-server-jss-preview.jar`。未设置该属性时仍生成原来的 `yudao-server.jar`；构建不会自动替换或重启运行中的服务。

2026-10-24 已验证上述独立产物名构建，核对包内包含五个商城模块及 trade-api，且包含秒杀场次结果映射修复。现有 48080 服务进程未重启，当次商品分类和装修使用中模板请求仍返回业务码 501，不代表最新包已部署或商城接口验收通过。

启动完整商城之前，必须完成数据库结构与必要配置。当前已接入模块依赖、会员/商品/支付/营销版本迁移及部分真实 Mapper 验证；交易/统计结构、运行配置和完整应用联调仍在推进，尚未证明真实商城 API 可用。不要将已有框架初始化 SQL 全量重放到业务库。

会员结构基线已补入 `sql/jss/migration/mysql/V001__member_schema.sql`，包含当前 11 个 member 模型所需表。已在隔离 MySQL 8.4.9 校验列覆盖、登录标识唯一性、社交注册空标识、租户独立性和签到经验。独立 Flyway 工具见 `sql/jss/README.md`；其他商城表仍在接入中，版本迁移不由 Java 启动流程自动执行。

商品九张表已纳入 V003，并通过真实 MySQL 列覆盖、JSON/配送类型、条件库存 SQL 和收藏约束验证。

支付 14 张表已纳入 V004，覆盖当前支付/退款/转账、通知、钱包、充值和演示接口模型；隔离 MySQL 的八项检查验证列覆盖、幂等键、回调格式、并发条件扣款 SQL 和充值字段。没有初始化渠道、凭证或资金，尚未验证真实支付、Redis 锁及完整业务事务。

生产 Mapper 的五项独立 MySQL 集成测试已通过，覆盖商品 JSON/CSV、SKU 内嵌属性和并发扣减/回补、标准与自定义查询的租户隔离及逻辑删除、会员登录标识和标签处理器、评价 SKU 属性往返。补查限定名类型后发现 V003 漏了评价属性，已通过 V007 追加 JSON 列并验证已有评价不丢失；七项商品结构检查通过，原 V003 不修改。此证据覆盖持久化链路，不作为完整 HTTP、RBAC、Redis 或支付验收。

支付另有六项实际 Mapper/MySQL 测试通过：并发消费不透支、钱包查询/修改的租户及逻辑删除隔离、冻结资金与累计充值双重退款条件、带符号流水及业务查询、渠道多态配置与额外参数处理器、转账状态条件更新及旧渠道结果清理。命令为 `python script/jss/verify_pay_mappers.py`；不连接外部支付渠道，尚未覆盖 Redis 锁及应用事务。

V005 补入装修模板/页面、文章/分类、轮播、客服会话/消息七张表。六项隔离 MySQL 结构检查通过，包含每租户单一启用模板的并发约束、独立页面命名空间、CSV 预览图、原始正文文本和客服会话归属/未读计数；没有发布装修、文章或轮播。内容 HTTP/客服连接仍待验证。

内容模块四项实际 Mapper/MySQL 测试已通过：装修图列表/配置文本往返、模板与独立页面查询、文章浏览更新、客服未读及批量已读操作。上述查询和更新均验证租户隔离，页面/模板/文章还验证逻辑删除查询；不代表完成内容 HTTP 或聊天业务授权。

V006 补入优惠券、满减、折扣、秒杀、拼团、砍价、积分活动的 16 张表，至此当前 promotion 的全部 23 个持久化模型都有迁移。活动结构的五项真实 MySQL 检查通过，V001–V006 的九项 Flyway 生命周期检查通过。

活动另有三项真实 Mapper 测试通过，验证商品范围/场次 CSV、嵌套满减规则及赠券映射、并发单张领券上限、秒杀商品库存扣减/回补与租户隔离。测试先复现秒杀商品场次列表写入后读回 null，修复为启用 `SeckillProductDO` 自动结果映射，复测通过。活动下单、批量发券和跨表事务仍未验收。
