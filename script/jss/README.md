# JSS 隔离数据库验证

## 公告备注与V010升级验证

`verify_migrations.py` 现包含十一项真实Flyway检查：框架基座及全部JSS版本初始化、已有V009升级V010、旧公告正文与新备注保留、重复migrate/validate、历史校验和拒绝漂移及初始化前置保护。所有操作仅在工具确认归属的13316随机库，结束清理；V001–V009未改写。新增前置表system_notice，部署包含备注字段的新服务器前必须执行并验证V010，开发库不会自动升级。

`verify_notice_services.py` 在原始system_notice DDL上应用V010，再装配生产Notice Service/Mapper、租户/分页与Spring事务。五项实际MySQL覆盖备注新增/显式清空/旧客户端null保留、富文本原文、筛选分页、外租户读写/混合批次、缺失整批拒绝和重复ID/外层回滚；无Redis、HTTP或WebSocket发送。当前实际证据及H2/序列化专项见docs/testing.md，不代替授权或在线消息验收。

## 完整后端 HTTP 认证

先启动上述工具管理的 MySQL 13316 与 Redis 16379，再运行：

```powershell
python script/jss/verify_workspace_http.py
```

脚本在随机独立 schema 导入受控框架基线，通过实际 Flyway 执行 Gym/JSS 迁移，准备临时测试身份，启动 `jss-mall` 的完整 `YudaoServerApplication`。使用真实密码、OAuth、MySQL、Redisson 和 HTTP 安全过滤链，关闭 mock token；不会向现有开发库写入用户、角色或绑定。三项测试验证未绑定会员的工作台能力关闭、同手机号/ID/会员分组不自动升级、会员 Token 被管理接口拒绝、会员退出不影响独立管理会话；以及审核绑定、跨租户拒绝、五分钟会话上限/无刷新凭证、替换/撤销/重绑/父会话退出的实际管理请求、绑定审计版本与操作人。仅有入口权限的员工不能查询用户或审核绑定。没有替换控制器或安全组件。

另两项测试通过实际管理接口移除业务权限、工作台入口、用户角色，停用/恢复角色及员工；已签发会话在下一次请求中被拒绝，重新授权不会复活已拒绝的旧凭证，会员商城会话仍可使用。第六项通过真实菜单更新接口停用业务按钮、父菜单和工作台入口，核验工作台 403/401 与恢复后的重新授权，并保留独立原生后台会话作行为对照。权限变化使用实际管理接口，没有直接改表或清缓存来促使测试通过。

第七项使用另一个临时租户和其授权审核账号，预热普通框架的租户有效性缓存后，通过真实租户更新接口停用/过期，要求工作台立即拒绝、context 返回对应错误码，恢复后旧凭证不复活且新会话正常。还检查用户列表仅返回该租户的账号。该测试先复现旧实现继续成功，改为实时租户验证后通过。

第八项切换同一员工的两个角色状态，验证一个启用角色可授予入口、禁用角色不贡献入口或业务权限、恢复授权不复活已拒绝凭证。第九项比较同一管理员的普通后台会话与 JSS 会话：普通后台按既有权限使用 `visit-tenant-id` 访问另一租户，JSS 凭证被拒绝。普通会员场景还核验分组实际名为“员工”，伪造 context 查询参数或工作台申请载荷仍不授权。全部身份只在随机测试库中。

该命令会经运行 ID 核对后重启已运行的任务专用 Redis，以隔离框架缓存；若旧实例已退出，则核验进程状态后启动一次。请在其他隔离 Redis 测试结束后串行运行。2.4 MB 框架基线和 Quartz 批量初始化采用 30 秒等待窗口，观测超时后仍须核实 SQL/进程和清理状态，不能据此重启服务。凭证仅经子进程环境传递，脱敏日志位于忽略目录 `.runtime/jss-http/workspace-http-test.log`。结束后删除自己的随机库并停止本次 Redis；现有 3306、6379、48080 服务不受影响。默认单元测试不会运行这一环境门控的集成测试。商城完整业务仍待继续验收。

加上 `--browser` 可在同一完整后端中执行相邻 `JSS-uniapp` 的真实浏览器闭环：

```powershell
python script/jss/verify_workspace_http.py --browser
```

要求相邻工程已安装锁定的 pnpm 依赖，并有本机 Edge。测试另启 9102 H5 服务，实际从登录弹窗输入临时会员密码，验证普通会员四项导航、审核绑定员工五项导航、真实用户查询、撤销绑定后的旧请求拒绝及换号/深链保护；不拦截 API 或注入登录 Token。该模式额外生成四位临时密码以验证后端既有密码契约。不会使用开发库账号，也不会改动 9000 预览；H5 输出在相邻工程忽略目录 `test-results/authenticated-h5`。

每次完整 HTTP 运行的服务及浏览器日志位于 `.runtime/jss-http/<随机测试库名>/`，最近位置见 `last-run.json`；包装脚本还检查本次服务日志不含测试密码。默认运行检查九项，浏览器模式检查十项且均不得跳过。浏览器另篡改真实登录保存的能力/权限字段后整页加载管理深链，要求重新查询的 context 仍无工作台权限；不会注入登录 Token。另有六项 `JssPermissionBoundaryTest` 验证权限上限、逐项 OR 组合和原生后台对照；凭证日志修复有六项 `ApiAccessLogSanitizationTest`，捕获控制台、持久化和底层 JSON 解析日志，覆盖嵌套凭证、查询刷新凭证、自定义脱敏键、禁用参数日志及损坏 JSON；不改变请求传给业务控制器的参数。

## 隔离 Redis

`redis_sandbox.py` 管理本仓库 `.runtime/jss-redis` 下的独立 Redis，默认仅监听 `127.0.0.1:16379`，拒绝默认业务端口 6379。要求先安装可运行的 Redis；不在 PATH 时用 `--redis-server` 或 `JSS_REDIS_SERVER` 指定可执行文件。首次使用示例：

```powershell
python script/jss/redis_sandbox.py start --redis-server '本机已安装的 redis-server.exe 完整路径'
python script/jss/redis_sandbox.py status
python script/jss/verify_redis_sandbox.py
python script/jss/verify_workspace_redis.py
python script/jss/redis_sandbox.py stop
```

随机认证凭证只保存在被忽略的本地 `redis.conf`；不进入命令行或工具输出。关闭 RDB/AOF 持久化，以凭证、端口与运行 ID 核对实例归属后才允许停止；失联时还要确认原进程已结束，不能因观测超时自动重启。重复启动复用已确认存活的实例，后续启动可省略路径。Windows 后台启动隐藏窗口，MSYS 构建使用工作目录内的相对配置路径。

真实 Redis 8.10.2 的四项检查覆盖未认证拒绝、实际 TTL 过期、NX 加锁与缓存 Lua 脚本按拥有者释放、运行 ID/默认端口守卫及重复启动。只删除本次随机测试键，不执行 FLUSHDB，也不操作既有 Redis 服务。这些协议检查尚不代替 JSS Token DAO、RBAC 或完整应用 API 验收。

`verify_workspace_redis.py` 将本地凭证仅传入测试子进程环境，运行四项 `JssWorkspaceRedisIT`。使用实际 `JssWorkspaceSessionDAO`、项目现有 Redisson 连接工厂、Spring Data Redis 与生产 Lua，核验摘要键/共同 TTL、替换后旧会话清理不能删新指针、跨租户父指针独立、实际过期及已过期输入拒绝。只创建随机测试会话并逐个删除；最新报告必须无失败或跳过，日志位于 `.runtime/jss-redis/workspace-redis-test.log` 并屏蔽认证密码。测试只接受已核验运行 ID 和 16379 端口；不包含 OAuth/RBAC/绑定 API 的完整组合链路。

## 隔离 MySQL

`mysql_sandbox.py` 仅管理本仓库 `.runtime/jss-mysql` 下的临时实例，绑定 `127.0.0.1:13316`，不使用默认业务端口。首次启动生成随机本地 root 密码，保存在被 Git 忽略的 `client.cnf`，不会打印凭证。启动、状态及停止都核对实际端口和数据目录。

```powershell
python script/jss/mysql_sandbox.py start --mysql-home "C:\Program Files\MySQL\MySQL Server 8.4"
python script/jss/mysql_sandbox.py status
python script/jss/verify_identity_schema.py
python script/jss/verify_member_schema.py
python script/jss/verify_product_schema.py
python script/jss/verify_product_mappers.py
python script/jss/verify_pay_schema.py
python script/jss/verify_pay_mappers.py
python script/jss/verify_promotion_content_schema.py
python script/jss/verify_promotion_content_mappers.py
python script/jss/verify_promotion_campaign_schema.py
python script/jss/verify_promotion_campaign_mappers.py
python script/jss/verify_trade_schema.py
python script/jss/verify_trade_mappers.py
python script/jss/verify_statistics_schema.py
python script/jss/verify_statistics_mappers.py
python script/jss/verify_migrations.py
python script/jss/mysql_sandbox.py stop
```

身份表验证创建随机命名的专用 schema，使用真实 MySQL 执行迁移并验证唯一性、租户隔离键、撤销后重绑、版本和审计快照，结束时删除自己创建的 schema。它不连接已有业务数据库，也不把这些约束测试作为完整登录/RBAC/Redis 验收。

会员表验证覆盖 `V001__member_schema.sql` 的 11 张表，并逐列对照当前 member DO；还验证空社交登录标识、同租户手机/邮箱唯一性、跨租户独立键、逻辑删除、IPv6 注册地址和签到经验字段。结构以本仓库历史 `5e8988b73bc76fcbc6e49b2b0a3e3c2b3b922b22` 的会员 DDL 为基线，再与当前模型/注册服务校对，不导入历史演示数据。

商品表验证覆盖 V003/V007 的九个当前模型，包括限定类型名 `List<ProductSkuDO.Property>` 的评价属性，排除内嵌属性对象本身的字段；验证 JSON/逗号分隔类型、并发条件扣库存 SQL、有效收藏唯一性与删除后重建、浏览可见性及评价字段。V007 前向补列保留已有评价，不修改已发布 V003。SQL 与模型匹配不代表已完成商品 HTTP/支付业务验收。

支付表验证覆盖 V004 的 14 个当前模型，核验商户单号的租户/应用幂等键、全局回调流水号、JSON 渠道配置与原始文本回调的区别、钱包归属唯一键、并发条件扣款 SQL、冻结/解冻、带符号流水金额、充值退款拆分字段及通知日志。迁移不初始化渠道、凭证、资金或成功状态；这些结构检查不代表已完成 Redis 锁、业务事务或第三方支付联调。

`verify_pay_mappers.py` 在独立随机库准备 V004，运行六项 `JssPayMySqlMapperIT` 并检查最新报告无失败或跳过。使用实际钱包/流水/渠道/支付扩展/转账 Mapper，覆盖并发消费、防止超额冻结退款、租户及逻辑删除、带符号流水、生产 JSON 处理器和转账重试清理旧结果。无 Spring/Redis 或外部支付调用；测试中的临时钱包和无需凭证的配置仅写入自己创建的随机库，完成后删除。日志位于 `.runtime/jss-mysql/pay-mapper-test.log`。

`verify_product_mappers.py` 在随机库准备 V001/V003/V007，显式运行 `JssProductMySqlMapperIT` 并核对五项测试确实执行、无跳过且无失败。测试使用生产 Mapper、JSON/CSV 类型处理器、租户拦截器和审计字段填充器，核验自定义查询/逻辑删除也保持租户隔离、并发库存修改，以及商品评价 SKU 属性往返。默认单元测试不连接 MySQL；该集成测试只接受独立命名的测试 URL，由包装脚本核验实例后提供凭证，结束删除随机 schema。测试日志位于忽略目录 `.runtime/jss-mysql/product-mapper-test.log`。

数据目录保留供后续同一任务重启，工具不会递归删除数据库文件。现有数据目录配置不同或初始化不完整时停止并要求检查，不自动覆盖。

`verify_trade_schema.py` 覆盖 V008 的 15 个交易模型，包括售后引用的限定嵌套属性类型。八项检查核验无业务种子、购物车有效归属键、每租户交易配置、配送 CSV/小数计费/营业 TIME、订单与售后 JSON 快照及空业务时间、佣金成员 ID/有符号冲正、审计文本。不会生成实际订单、物流、佣金或核销码，也不连接支付渠道；真实下单和退款跨表事务仍需应用联调。

`verify_trade_mappers.py` 使用 V008 与生产 Mapper，在随机库运行六项测试：购物车归属/批量更新、订单赠券 JSON/CSV 与状态更新、订单明细属性/售后状态条件、售后快照及审核状态、交易配置/运费/营业时间类型处理、佣金负数冲正及并发受限解冻。验证对应查询和更新的会员/租户范围，不使用 Mapper 替身；最新报告必须无失败和跳过。日志位于 `.runtime/jss-mysql/trade-mapper-test.log`，结束删除随机 schema。不包含实际支付/物流或跨表事务。

`verify_statistics_schema.py` 覆盖 V009 的两个当前统计模型。四项检查验证商品 DATE/交易 DATETIME、无统计种子、每租户/商品/日有效快照唯一性、逻辑删除后重算、非空零值指标和有符号结算字段。不会运行定时统计任务或填入虚构报表；聚合 Mapper 和统计业务仍需独立验证。

`verify_statistics_mappers.py` 在随机库准备 V003/V007/V008/V009，运行三项实际统计 Mapper 测试，使用生产 XML、MPJ 注入器/拦截器、分页及租户插件。覆盖交易收入/支出聚合、商品日统计求和/平均/分组分页、商品原始数据 XML 的浏览/收藏/购物车/支付/售后子查询及订单关联；显式加入跨租户引用数据验证不会混入指标，并检查统计快照的逻辑删除。测试数据仅在自己的临时库创建，结束删除；日志为 `.runtime/jss-mysql/statistics-mapper-test.log`。尚未运行定时任务或报表 HTTP。

`verify_promotion_content_schema.py` 验证 V005 的七个内容/装修/客服模型：并发启用时每租户只有一个使用中的模板、独立页面命名与模板页面互不干扰、逻辑删除后名称释放、CSV 预览图及文本正文格式、客服会话唯一归属、隐藏会话保留身份、未读计数 SQL。不会发布装修内容或连接聊天 WebSocket；内容接口和完整客服权限仍需应用联调。

`verify_promotion_content_mappers.py` 使用 V005 和实际装修/文章/客服 Mapper 执行四项独立 MySQL 测试，核验 CSV 处理器与配置原文往返、使用中模板和独立页面查询、文章浏览计数、客服未读查询/批量已读更新，以及这些操作的租户/逻辑删除隔离。检查最新报告的执行数量与失败/跳过状态，结束清理测试 schema，日志为 `.runtime/jss-mysql/content-mapper-test.log`。不运行 Spring 安全链或 WebSocket，不代替应用级授权验收。

`verify_promotion_campaign_schema.py` 在随机库校验 V006 的 16 个活动模型，区分规则/图片 JSON 与商品范围/场次 ID 的 CSV；检查 -1 无限发券值、计数器默认值、秒杀/积分/砍价条件库存 SQL、活动/SKU 唯一键和删除后重建、单次助力约束，以及同一会员可合法持有同模板的多张券。不会创建实际活动或向业务账号发券，不作为完整活动下单/退款验收。

`verify_promotion_campaign_mappers.py` 在随机库执行三项真实 Mapper 测试：优惠券商品范围 CSV 与并发单张领取数量上限、满减嵌套规则及赠券映射、秒杀配置图片 JSON/场次 CSV 与商品并发库存扣减及回补。均验证租户隔离；运行后核对最新报告并删除测试库，日志为 `.runtime/jss-mysql/campaign-mapper-test.log`。测试曾复现秒杀商品读回场次 ID 为 null，现通过 `SeckillProductDO.autoResultMap` 修复。尚未覆盖活动下单的跨表事务或批量发券。

`verify_migrations.py` 在独立随机 schema 中执行真实 Flyway baseline/migrate/validate、校验和漂移拒绝及初始化守卫，连接密码仅从隔离实例的本地配置进入环境变量。正式部署使用 [JSS 迁移命令](../../sql/jss/README.md)，测试工具不会替用户初始化业务库。
