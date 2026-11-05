# 验证手段与当前结论

> 规范：所有「已验证」结论必须附命令 + 输出数字；未接入/未覆盖的能力必须显式标注。
> 变更记录见 [`CHANGELOG.md`](CHANGELOG.md)，结构见 [`architecture.md`](architecture.md)。

## 1. 三层验证策略

| 层 | 手段 | 覆盖什么 | 不覆盖什么 |
| --- | --- | --- | --- |
| L1 单元测试 | JUnit 5 + Mockito（`yudao-module-gym/src/test`） | 纯逻辑：魔数嗅探、类别/大小规则、服务层分支与错误码、越权判断 | HTTP 层、序列化、真实 SQL |
| L2 端到端脚本 | `curl` + 断言（`script/gym/verify-*.sh`） | 真实链路：鉴权、权限码、数据范围、multipart、二进制流、错误码 | 并发、性能、前端渲染 |
| L3 手工/文档页 | Knife4j <http://127.0.0.1:48080/doc.html>、IDEA `.http` 文件 | 交互式核对字段与提示文案 | 不适合回归 |

写新接口时三层都要补：L1 照 `GymFileServiceImplTest`，L2 照 `verify-file-api.sh`，L3 在 `script/gym/*.http` 加请求。

## 2. 环境依赖

| 项 | 要求 | 备注 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306/ruoyi-vue-pro`，账号 `yudao/123456` | 本机 `root` 为免密 socket 登录，JDBC 连不上；可用 `GYM_DB_USERNAME`/`GYM_DB_PASSWORD` 覆盖 |
| Redis | `127.0.0.1:6379` | token、验证码、权限缓存 |
| JDK / Maven | JDK 25 / Maven 3.9 | 与 `pom.xml` 的 `java.version=25` 一致 |
| 服务端口 | 48080 | `application-local.yaml` |
| 租户请求头 | **所有接口必须带 `tenant-id: 1`** | 缺失返回 `code=400 请求的租户标识未传递` |

## 3. 命令清单与当前结论

### 3.1 编译

```bash
mvn -T 1C -DskipTests -pl yudao-server -am package
```
结论：`BUILD SUCCESS`，产物 `yudao-server/target/yudao-server.jar`。

### 3.2 单元测试（gym 模块，15 例）

```bash
mvn -T 1C -pl yudao-module-gym -am test -Dtest='GymFile*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

| 测试类 | 例数 | 覆盖 |
| --- | --- | --- |
| `GymFileTypeUtilsTest` | 5 | 7 种魔数识别、无法识别、**后缀与内容不一致**、类别白名单、类别大小上限 |
| `GymFileServiceImplTest` | 10 | 上传成功（默认门店）、非法类别、后缀伪装、类别白名单、超限、跨门店上传、文件不存在、越权读取、内容读取、内容缺失 |

结论：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。

> 注意：`-pl yudao-module-gym` 单独跑会因本地仓库缺依赖而失败，必须带 `-am`（在 reactor 内解析兄弟模块）。

### 3.3 数据库初始化（幂等）

```bash
bash script/gym/seed.sh
```
结论：按序执行 `script/gym/sql/01-gym-permission-seed.sql`（51 权限码 / 52 总部菜单 / 36 门店菜单 / 2 账号）
与 `02-gym-file.sql`（建 `gym_file`）；重复执行计数不变（已实测两连跑）。

### 3.4 认证接口端到端（26 断言）

```bash
bash script/gym/verify-auth-api.sh
```
结论：`通过 26 / 失败 0`。覆盖：未登录 401、登录成功/错误密码、角色与 51 个 `gym:*` 码、
`gym:` 前缀隔离、用户资料与部门角色、刷新令牌、登出失效、门店角色 35 码与 16 个仅总部码反向断言。

### 3.5 文件接口端到端（29 断言）

```bash
bash script/gym/verify-file-api.sh
```
结论：`通过 29 / 失败 0`。反例矩阵：

| 场景 | 期望码 | 结果 |
| --- | --- | --- |
| PDF 内容伪装 `.png`（image 类别） | `1070000003` | ✅ 通过 |
| 合同 PDF 传成 `image` 类别 | `1070000003` | ✅ |
| 11MB PNG（image 上限 10MB） | `1070000004` | ✅ |
| 未知类别 `coupon` | `1070000002` | ✅ |
| `gymstore` 跨门店读元数据 / 内容 | `1070000007` | ✅ 两次均拒绝 |
| 未登录访问 | `401` | ✅ |
| 读取不存在的文件 | `1070000001` | ✅ |
| 下载内容与上传文件 | 逐字节一致 | ✅（`cmp`）+ `Content-Type: image/png` |
| 响应体是否含 infra 存储地址 | 不含 | ✅ `url`/`path` 均无 |

## 4. 已知测试盲区（未覆盖，勿当成已通过）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 并发上传同一文件 / 幂等 | 未覆盖 | 旧 Mock 用 `Idempotency-Key`；本接口未实现幂等，保留原始行为 |
| 200MB 视频真实上传 | 未覆盖 | 脚本只验证了 `video/mp4` 小文件；大文件仅由配置上限 + 类别上限约束 |
| S3 / FTP 等存储后端 | 未覆盖 | 本机使用 infra 默认存储配置；换后端需回归 3.5 |
| 前端渲染与预览 | 不属本仓库 | `admin` 仓库的适配层仍指向 `/infra/file/upload`，联调前需前端改这一行 |
| 多租户隔离 | 未覆盖 | 演示库只有 `tenant_id=1`；`gym_file` 继承 `TenantBaseDO`，隔离能力来自框架 |
| 生产安全项 | 未覆盖 | 文件后缀/内容策略、`api-encrypt`、限流、审计日志（旧 Mock 对文件也不审计） |

## 5. 复现失败时的排查顺序

1. 服务是否在跑：`lsof -nP -iTCP:48080 -sTCP:LISTEN`
2. 是否带上租户头：缺 `tenant-id` → `code=400 请求的租户标识未传递`
3. 数据库是否初始化：`bash script/gym/seed.sh`（幂等，可随时重跑）
4. 权限码是否落库：`mysql -uroot -D ruoyi-vue-pro -e "select count(*) from system_menu where creator='gym-seed' and type=3"`
5. 账号是否被停用/改密：重新 `seed.sh` 不会覆盖已存在账号的密码，必要时按 `script/gym/README.md` §5 的清理 SQL 删掉重建
6. 日志：`~/logs/yudao-server.log`（沙箱内运行时用 `--logging.file.name=<仓库内路径>` 覆盖）

## 2026-10-13 门店 / 员工首批验证

```bash
mvn -q -pl yudao-module-gym -am test -Dtest='Gym*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -q -DskipTests -pl yudao-server -am package
python3 script/gym/verify-migrations.py
```

Mockito agent 路径按本机 Maven 缓存的实际版本调整；Java 25 环境无法动态自附加时使用启动 agent，不必修改生产 JVM 配置。

结果：34 测试、0 failures、0 errors（组织集成测试 17 + 既有文件测试 17）；主服务打包退出码 0。组织测试使用真实 H2、MyBatis、TenantDatabaseInterceptor、事务和方法权限代理，system 部门/用户/权限 API 为 mock。覆盖持久化、部分更新、唯一性、字段/任职校验、总部/门店范围、直接 ID 越权、多租户、无权限/只读角色拒绝、关联账号、媒体替换以及 DB 写入失败回滚。生产 MySQL 并发行为未验证。

迁移验证只创建独立随机 MySQL 测试库，取基座六张表的 DDL，执行全部 gym 脚本两次：四个业务表存在、两个页面路由正确、两角色共四条页面授权，重复执行记录数一致。验证后删除测试库；不读取或修改业务库。可通过 GYM_TEST_DB_HOST/PORT/USER/PASSWORD 指定专用测试实例。

48080 当前无法连接；此次未启动后端、未迁移业务库、未执行旧认证/文件 HTTP 脚本或前端浏览器联调。手工请求见 `script/gym/gym-organization.http`。历史 HTTP 证据仅代表历史切片，不自动覆盖本次版本。

## 2026-10-14 文件二进制下载错误状态回归

```bash
mvn -q -pl yudao-module-gym -am test -Dtest='Gym*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
bash -n script/gym/verify-file-api.sh
```

结果：**43 tests，0 failures，0 errors，0 skipped**（原有 34 + 新增 GymFileControllerTest 9）。新测试使用实际 Spring MVC 异常解析、GlobalExceptionHandler 和方法权限代理，文件 Service 为 mock，断言：正常下载二进制及附件头；记录不存在/内容缺失/包装后的内容缺失异常 HTTP 404；跨店及 @PreAuthorize 拒绝 HTTP 403；非法 ID HTTP 400；存储异常 HTTP 500；元数据错误继续 HTTP 200。错误 JSON 保留原业务 code，不生成附件响应头。

`verify-file-api.sh` 新增跨店下载 HTTP 403、不存在文件下载 HTTP 404 及错误码断言；本次仅通过 shell 语法检查，未对运行服务执行此脚本，未运行前端浏览器。安全 Filter 层的未登录/租户拒绝不在本次 MVC 测试覆盖范围。

## 2026-10-14 Mapper 跨模块注入启动回归

在 `GymOrganizationServiceTest` 中补充 infra 文件包的真实 Mapper 扫描，使 `fileMapper`（infra）和 `gymFileMapper`（gym）同时注册为 MyBatis JDK 代理。此前仅扫描 gym，未覆盖 `@Resource` 优先按字段名称查找 Bean 的碰撞。

```bash
# 修复前，仅新增真实 infra Mapper 扫描和用例，复现用户报告的注入错误：
mvn -q -pl yudao-module-gym -am test \
  -Dtest='GymOrganizationServiceTest#bothFileMappersCoexistAndStoreMediaUsesGymMapper' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
# 修复后完整回归：
mvn -q -pl yudao-module-gym -am test -Dtest='Gym*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -q -DskipTests -pl yudao-server -am package
```

修复前第一条命令退出 1，得到 `BeanNotOfRequiredTypeException: Bean named 'fileMapper' is expected to be of type GymFileMapper but was actually of type jdk.proxy2.$Proxy...`。将门店服务字段改为 `gymFileMapper` 后，Spring 测试上下文成功初始化；验证两个 Mapper Bean 的实际接口及通过 gym 表保存/读取门店媒体。完整回归 **44 tests，0 failures，0 errors，0 skipped**，主服务打包退出 0。

此证据覆盖真实 MyBatis Mapper 注册和注入，未启动连接业务数据库/Redis 的完整服务。无需启用 CGLib，也没有修改共享 Mapper 扫描配置。

## 2026-10-14 全量 API 任务：薪资与会员基础进度

```bash
mvn -q -pl yudao-module-gym -am test -Dtest='Gym*Test' -Dsurefire.failIfNoSpecifiedTests=false -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -q -DskipTests -pl yudao-server -am package
python3 script/gym/verify-migrations.py
# 运行服务后：从安全环境设置 GYM_TEST_PASSWORD，使用用户指定 admin
GYM_TEST_USERNAME=admin python3 script/gym/verify-salary-api.py
GYM_TEST_USERNAME=admin python3 script/gym/verify-member-api.py
```

已执行薪资 HTTP：37 断言通过，含8请求并发薪资关联只建一行、4请求并发版本号唯一连续、已发布不可改、员工和薪资失败原子回滚。单元/集成回归薪资阶段53测试通过；会员基础增加3测试后56测试通过（会员基础 HTTP 17断言通过，测试memberId=1保留）。构建退出0。隔离MySQL迁移：7业务表、5页面、9条示范角色页面授权；全部迁移二次执行幂等，临时库已删除。

真实浏览器先以示范账号新增薪资模板，再按用户指定切换admin；发布成功、刷新后状态仍为已发布。截图 `/tmp/gym-salary-admin-verified.png`。未修改dashboard源码，页面仅显示已上线批次；全部35业务页面未完成验收。

### 同日继续：体测与黑名单

同一Gym测试命令：62 tests，0 failures，0 errors。新增6个真实H2/MyBatis测试覆盖黑名单阻断入场/购卡、未生效/已过期边界、撤销幂等与不可修改、总部写权限、门店/租户读取隔离，以及体测字段/身份不可变、报告文件门店及类别校验、权限负例。

重新打包并启动后 `GYM_TEST_USERNAME=admin python3 script/gym/verify-member-api.py`（密码通过环境变量提供）：28断言通过，测试memberId=3；包含会员基础17断言及体测/黑名单11断言。`verify-migrations.py`：9业务表、7页面、13条示范角色页面授权，所有迁移两轮幂等。admin浏览器真实体测列表及详情通过，截图 `/tmp/gym-measurement-admin-verified.png`。未联调报告真实上传及跨店HTTP负例，已有真实Mapper集成负例覆盖；入场/购卡业务调用黑名单检查仍待对应业务模块。

## 卡权益、私教与审批模板验证

完整Gym回归84测试，0 failures/errors/skipped；包含新店与会员权益锁顺序并发测试、模板冻结/版本、预占取消重约、签到前置及单次扣课、租户和门店权限负例、SQL失败事务回滚，以及审批模板发布账号有效性和步骤合法性。

卡/课程真实HTTP46断言（verify-card-course-api.py）；会员28、薪资37断言回归；审批模板22断言（verify-approval-template-api.py），含4并发版本连续唯一。均通过环境提供测试凭证，保留带HTTP联调前缀开发数据。全量SQL隔离迁移21业务/支持表、14菜单、26示范角色页面授权，两轮执行一致。已构建启动最新版服务；审批记录及真实开卡来源尚未接入，因此有权益的真实HTTP预约/课消全链路和前端验收仍待完成，当前H2真实业务测试不能代替该项验收。

审批模板前端证据：admin在现有页面修改V5草稿名称为“浏览器联调开卡审批-20261014”，保存、发布并刷新后仍显示已发布；截图 `/tmp/gym-approval-template-admin-verified.png`。审批员工为现有店长2账号，未修改账号角色或权限。

## 2026-10-15 审批业务闭环

Gym完整89测试全部通过；新增5项审批业务集成测试覆盖两级审核、回滚重试、拒绝、改价冲突及附件/账号重绑定。调试修复返回VO自动将JSON字符串映射为Map的异常，改为显式JSON解析；模板发布增加权限校验并补充拒绝测试。

新版构建/启动后，verify-approval-flow-api.py使用admin与现有gymstore示范角色，真实HTTP52断言通过。申请与审批各8并发保证一申请一卡；签到前禁止签课、取消释放、重约清理、实际签到后8并发仅一课消，真实剩余2→1、产品200→150；有通卡的会员自动扩展新店。脚本为每轮创建独立测试教练，避免与已完成但仍占用教练时段的排期冲突，保留所有带HTTP标识资料。GYM_APPROVER_PASSWORD通过环境提供，无角色权限修改。verify-approval-template-api.py同构建回归22断言。

SQL01–19隔离两轮幂等验证23表/15页面/28示范角色页面授权。浏览器admin发起，gymstore实际审核，admin核对发卡并使用该卡预约，截图/tmp/gym-approval-approved-store.png与/tmp/gym-approval-card-rights-admin.png。仍待合同/订单审批来源、其他业务页面和最终全量review。

审批开卡后前端真实预约成功截图：`/tmp/gym-approved-card-booking-admin.png`，admin会话已恢复，未来排期sessionId=6，预约已确认。

## 2026-10-15 合同模板

完整Gym93测试通过，新增3项真实数据库模板测试及1项文件访问政策测试；模板PDF类别/类型/租户拒绝、草稿范围、发布冻结及替换、新版本、权限及共享撤回。verify-contract-template-api.py真实26断言通过，包含实际multipart上传和PDF下载内容逐字节一致、4并发版本唯一。隔离迁移24表/16页面/29授权两轮幂等。01种子重跑原先会扩展示范门店合同模板菜单，增加总部操作排除后恢复幂等。

浏览器真实上传失败（UPLOAD-JSON-001）：现有gym.ts uploadGymFile没有覆盖RequestClient默认JSON头，FormData被序列化后只剩file.uid，后端未接收到二进制。HTTP脚本multipart正常，不能把前端上传记为通过。仅记录前端修复需求，未改变dashboard代码；无法通过后端补造文件解决。合同模板其余配置与预览验证继续，合同记录、签约审批和提醒尚待实现。

admin浏览器使用已由真实HTTP上传的PDF，编辑V5合同模板并发布成功，截图`/tmp/gym-contract-template-admin-verified.png`。文件预览页加载文件名0.6KB及受控Blob下载链接，但IAB嵌入PDF区为空白，未确认PDF渲染；模板新增/替换的浏览器上传仍受UPLOAD-JSON-001阻塞。

## 合同实例及签约/提醒验证

Gym98测试通过，新增5个场景测试：权益实际生成独立原件/来源幂等、模板停用不损坏实例、缺原件回滚卡及最终审批、签约授权不伪造签名、提醒失败持久化/键冲突、私教对应模板，以及64字符ASCII/Unicode门店编码下真实会员号/卡号保存和UTF8合法性。

verify-contract-flow-api.py真实HTTP36断言：实际FileApi复制PDF并授权下载逐字节一致；8并发审批只建一卡一合同，8签约决定只有一授权事件，8提醒请求仅一failed记录。停用模板收回共享PDF访问，但已发实例仍可由本店读取；未配置服务商保持unsigned/unconfigured且无签名字段。主服务构建启动通过；SQL01–23隔离二轮27表/17页面/31授权幂等。真实渠道/自动提前提醒未验收，不能将未发送结果记为成功。


## 2026-10-15 工资联调

完整Gym测试105通过。运行 `mvn -q -pl yudao-module-gym -am test -Dtest='Gym*Test' -Dsurefire.failIfNoSpecifiedTests=false -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'`。隔离MySQL迁移30表/18页/33示范角色页面授权，两次幂等通过。

`GYM_TEST_PASSWORD='<开发密码>' python3 script/gym/verify-payroll-api.py`：真实HTTP24断言。保留停用开发门店及工资，可浏览器重算、详情来源与确认；确认后执行同命令追加 `--verify-confirmed`：10断言通过（重复确认时间不变、两种重算入口拒绝、确认期间不能缩短）。当前fixture /tmp/gym-payroll-flow-fixture.json，store131/payroll1，合计10100已确认。前端截图/tmp/gym-payroll-source-admin.png及/tmp/gym-payroll-confirmed-admin.png。此验证不包含实际支付工资或销售收款。

合同HTTP扩展42断言当前因对象存储S3 403在上传中断，未通过；此前36断言通过。前端上传请求内容缺文件字节的问题仍单列，不修改前端源码。


## 2026-10-15 经营报表验证

完整Gym测试110项通过。`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-report-api.py`只读真实HTTP51断言通过：看板日趋势和指标合计、业绩分组/分页汇总、会员统计交叉核对、无登录及跨门店拒绝、非法范围参数。服务数据库测试补充了实际支付减退款、订单部分付款去重及退款单独负净额，运行库没有伪造收入。隔离迁移30表/21页/39示范角色页面授权两次幂等通过。前端截图/tmp/gym-report-performance-admin.png、/tmp/gym-report-members-admin.png、/tmp/gym-dashboard-admin.png；7/30天切换、员工分组、真实会员人数、图表可见。会员卡报表与真实支付事件写入器尚未完成。


## 2026-10-15 售卡核销设置验证

完整Gym测试116通过；新增六项涵盖完整替换、并发幂等、草稿/停用/月卡/店归属、重复方案、签约审批依赖、时段、渠道、扣款日、金额精度和租户/权限边界。`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-sales-settings-api.py`真实HTTP38断言通过，保留停用store132/template27，无发卡或付款。隔离迁移31表/22页/41示范页面授权，两次幂等通过。前端admin在线价改89.88保存并刷新保持，原卡模板100不变；包月59.99和核销关闭配置可见，截图/tmp/gym-sales-settings-admin.png。厂商执行及真实订单支付仍未接入。


## 2026-10-15 收款与分账验证

123完整Gym测试通过，含7项新增数据库测试。真实渠道bound正向激活只在H2内部测试夹具模拟已验证回执状态，不是运行库实际绑定。`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-settlement-api.py`：真实HTTP46断言通过，保存脱敏账户、未配置绑定原因和草稿；6并发版本连续、精确比例/越权/伪造绑定拒绝。隔离迁移34表/25页面/44示范页面授权两轮幂等通过。

浏览器验证账户改名保存刷新、V7周期3保存刷新、未绑定启用后仍draft、结算记录真实空状态。截图/tmp/gym-payment-accounts-admin.png、/tmp/gym-settlement-rules-admin.png、/tmp/gym-settlements-admin.png。前端未显示启用业务失败消息且事件处理未捕获，记录FRONTEND-SETTLEMENT-ERROR-001，不改前端。真实划付/到账和生产回执写入未接入。


## 2026-10-15 活动与加盟验证

完整Gym测试132通过。`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-marketing-api.py`真实HTTP48断言通过，留下活动2和咨询4；限流测试会在开发来源IP消耗60秒窗口，重跑应待窗口恢复，不清空限流键。待冷却后使用 `python3 script/gym/verify-marketing-api.py --verify-rate` 只重放原提交，不新增咨询，核对伪造转发头不绕过限流。

前端活动名称/容量保存刷新、详情2/4及报名记录；加盟咨询测试状态处理保存。截图/tmp/gym-activity-admin.png和/tmp/gym-franchise-admin.png。活动封面真实上传仍受已记录阻塞，未验证通过。未发通知或联系真实联系人。隔离迁移38表/27页面/46示范角色页面授权两次幂等通过。

最新构建的 `verify-marketing-api.py --verify-rate` 已实测3断言通过：原提交仍可幂等返回，变更X-Forwarded-For的第6次请求返回429，未新增咨询。


## 2026-10-15 设备与器械验证

139完整Gym测试通过，新增7项数据库业务测试，含可信状态不能后台伪造、唯一设备UID、稳定柜格ID、占用保护、并发幂等失败日志、文件/器械归属和读写/命令权限分离。隔离迁移43表/29页/50示范页面授权两轮幂等，构建运行。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' GYM_TUTORIAL_FILE_ID=1 python3 script/gym/verify-device-api.py`最终38断言通过，明确输出tutorialPositiveVerified=False与existingMediaReadable=False。设备4/5、器械1。第一次在旧媒体所属124无业务门店时中断；脚本现有分支正确验证跨店文件拒绝，没有把失败变成正向教学通过。H2正向关联和启停通过，但真实媒体上传/读取仍被已记录前端上传和S3 403阻塞。

前端新增柜格A3、同键重复提交保持一条失败日志；器械名称/型号修改保存刷新，教学列表真实为空。截图/tmp/gym-device-command-admin.png和/tmp/gym-equipment-admin.png。没有发送硬件指令、伪造在线或填充假教学。


## 2026-10-15 独立存储补验

新增可选存储后140项Gym+37项infra FileServiceImplTest全部通过。当前后端使用 `bash script/gym/run-local.sh --logging.file.name=/tmp/gym-server.log`，默认仅gym选DB存储4；全局master仍22。后续调试重启使用此脚本，或显式传--gym.file.storage-config-id=4，否则会回到原S3示例默认。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-gym-storage-api.py`：真实18断言，通过file19/store125及tutorial1，PNG有效且字节完整一致。合同verify-contract-flow-api.py本次完整42断言通过，contract3/member17；替代此前S3阻塞的未验证状态，旧历史记录不删。前端真实教学图片预览已见，截图/tmp/gym-real-tutorial-preview.png。前端按钮上传仍受UPLOAD-JSON-001影响，不能将此测试当浏览器上传通过。


## 2026-10-15 展示配置与场景二维码

完整Gym测试150项全部通过，新增10项内容/二维码测试：真实H2租户、权限、完整PUT/清空、跨店隐藏媒体拒绝、HTML清洗、下载URL校验、六并发配置及场景幂等、真实排课归属。隔离MySQL迁移两轮执行：45业务/支持表、31业务菜单、53示范页面授权；SQL35/36已应用，新jar构建并通过run-local.sh运行。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-content-api.py`首次43断言通过，真实排课引用=True，store125/file19/qrCode1，fixture /tmp/gym-content-fixture.json无凭证。脚本增加配置六并发断言后，完整新版44断言已重跑通过；另独立真实MySQL补验6断言通过，六并发PUT保持浏览器已保存版本和正文不变。

浏览器admin：选择HTTP审批示范门店，名称改“浏览器展示验证-20261015”，正文“浏览器正文保存验证-20261015”保存并刷新后保留，封面真实file19加载；截图/tmp/gym-client-settings-admin.png。窄面板截图有封面预览覆盖表单现象，不作为布局通过证据。二维码重复提交仍4条，原createdAt与failed不变，无预览文件；截图/tmp/gym-qr-codes-admin.png。页面只显示失败状态而无failureReason列，API返回明确原因。

FRONTEND-CONTENT-IMAGE-001：含gym-file:19图片的正文，后端PUT/GET清洗结果一致，但首次进入编辑器后正文为空且未操作就dirty；纯文本保存刷新可用。疑似RichContent/VbenTiptap异步图片hydrate与初始更新交互，尚未修复或确认具体根因。用户禁止改前端，源码保持干净。不得把封面预览或纯文本通过当作富文本图片回填通过。


## 2026-10-15 订单、包月及核销记录查询

完整157项Gym测试通过，新增7项真实H2业务测试：限定自主办卡来源/商品类型、支付退款与商品快照、渠道标识遮蔽、跨租户和门店保护、协议子记录保护、日期过滤、包月未扣款空时间。周期Instant范围用北京时间零点比较，覆盖零点后1秒的双边界。发现并修正chargedAt错误非空约束，真实MySQL SQL37保持nullable。

`python3 script/gym/verify-migrations.py`两轮52业务/支持表、34页面、59示范角色页面授权一致，SQL37/38应用运行库，mvn package通过，新jar经run-local.sh启动。H2不模拟MySQL二进制字符排序语法，真实迁移单独验证该语法。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-sales-records-api.py`：38断言通过，验证认证、空库仍跨店拒绝、页大小/日期校验、父实体不存在错误及只读API边界。运行库三个资源记录数均0，未插入任何已支付、已签约、已扣款或已核销夹具；HTTP未验证有数据的详情正向，相关模型/隔离正向仅H2测试证明。

现有前端三个列表均显示“暂无记录”，无新增/编辑入口；截图/tmp/gym-sales-orders-admin.png、/tmp/gym-subscriptions-admin.png、/tmp/gym-verifications-admin.png。这只证明加载与空态可用，不能代替交易闭环、周期详情或审批入口验证。前端源码未改。


## 2026-10-15 内部交易与财务事务验证

完整Gym测试169项通过（137数据库业务、14文件服务、9文件controller、5类型、2限流键、2支付配置）。本轮新增10数据库测试+2配置测试；验证服务端报价与重放快照、禁售/免费/范围、部分支付与分笔退款、跨租户/订单/币种/精度、超额支付/退款、渠道ID冲突、六并发支付幂等、支付及退款财务写失败事务回滚。报表读取新财务事件净额79.99，全部分笔退款后0，仅H2夹具。

ApplicationContextRunner验证无适配器注册默认失败实现，注入真实适配器接口bean时默认实现退让，不产生双bean。两轮MySQL迁移53表/34菜单/59授权一致，SQL39应用，mvn package构建并经run-local.sh启动。真实verify-sales-records-api.py回归38断言通过。SQL核对运行库订单、支付、退款、财务事件、订单规则五项均0，未导入支付成功测试夹具。

本轮没有真实厂商核验成功或HTTP购买/付款/退款入口，不宣称真实支付联调通过；管理端已有三个记录页面仍为空。订单审批/权益及合同衔接、退款权益处理等未实现范围见进度表。


## 2026-10-15 订单审批履约验证

完整Gym测试176项通过，新增7数据库业务测试：订单身份由服务端解析，未付款/错门店/替换参数拒绝，最后一步仅一次发卡，合同缺失回滚审批与权益但保留支付，模板停用后按已购版本发放，冻结审批规则不可通过改设置绕过，审批期间退款阻止发卡，六并发三张卡订单恰好三张权益。订单详情关联ID/履约时间/合同规则亦检查。

构建成功并以run-local.sh启动，无新增迁移或菜单（仍53表/34业务菜单/59示范授权）。verify-sales-records-api.py补充不存在orders来源和替换memberId拒绝，真实42断言通过，未产生成功交易。原verify-contract-flow-api.py真实42断言回归通过，contract4/member18，新原件file23，资料保留；不调用真实电子签/提醒。

订单支付及新履约正向仅H2独立凭证mock验证，未把paid订单夹具写入开发库，因此新orders审批按钮尚无真实有数据浏览器正向证据。原手动开卡/合同链路HTTP回归通过，不能替代新订单来源的正向界面验证。前端未改。退款后权益规则待用户回答，自动履约触发/重试仍未完成。


## 2026-10-15 全额停卡与部分退款人工处理

完整181项Gym测试通过（149数据库业务及既有32项测试）。新增5数据库测试证明：部分退款默认权益保留，按笔任务、人工retain/stop_selected及不可覆盖；全额退款只停本订单卡、保留次数并关闭待办；停卡数据库失败使退款/财务/汇总一起回滚；六并发全额退款不重复入账；其他卡、角色权限、门店/租户及关闭后处理被拒绝。

SQL40及新构建已运行。verify-migrations.py两轮54表/34菜单/59示范授权一致。verify-sales-records-api.py当前45真实HTTP断言通过，新增退款任务登录/父订单不存在和无效订单决定检查；运行库交易仍0，没有制造支付退款成功夹具。新增退款正向动作仅H2验证，不当作真实渠道退款或浏览器操作通过。

现有前端未提供人工退款权益按钮；按用户限制没有修改前端。后端详情可读refundReviews，手动决定接口供后续接入。既有预约没有被自动取消：卡已inactive不能继续使用，历史课消/签到/预留保留；门店按已有取消流程释放预约。退款规则用户已答复，不再待确认；自动履约触发和其他剩余模块继续。


## 2026-10-15 自动履约任务及独立调度验证

197项完整Gym测试通过（163数据库业务、14文件服务、9文件controller、5类型、2限流键、2支付配置、2私有定时器装配）。本轮增加14业务测试及2定时器测试，覆盖支付/任务激活原子回滚、零价无假支付、失败/退避/配置修复后成功、耗尽/人工重试保留累计次数、账号停用/租户/角色/门店撤权、租约接管和旧token拒绝、六执行器只一次领取发卡、多租户上下文恢复、不可用租户跳过、取消订单拒绝重试、退款取消任务、人工审批暂停与创建失败回滚。

ApplicationContextRunner实际启动私有定时器，证明回调线程gym-fulfillment-*且不替换/调用应用TaskScheduler；显式enabled=false无后台回调。第一次测试因mock具体Scheduler类的@Resource字段被测试容器注入失败，已改为构造器依赖并完成全量重跑，不遗留失败。

SQL41、构建及run-local.sh新后端运行。两轮MySQL迁移55表/34菜单/59示范授权一致。verify-sales-records-api.py真实48断言回归通过，含履约任务未登录、未知父订单及人工重试拒绝。没有真实成功交易或履约任务夹具，运行库列表仍0，正向任务处理/重试仅数据库和组件测试，不算真实厂商付款联调。

本地实测日志2026-10-15 12:29:51，/tmp/gym-server-console.log：gym-fulfillment-1正常扫描3租户、候选0，121/122为TENANT_EXPIRE(1002015002)正常跳过。只读SQL确认121有效期2026-07-10、122为2023-04-29，未修改租户；admin账号1有效且tenant1。之前基于共用scheduling-1的版本发现重复WARN，已替换私有线程及正常过期跳过策略。当前启动附定向DEBUG日志，其他调度线程保持原配置。前端源码仍干净。

人工审批拒绝后任务保留waiting_approval，需人工后续决定，不自动绕过拒绝发卡。现有前端没有任务重试按钮，后端详情fulfillmentTask与两个新接口可查询/控制；未改前端、未宣称新按钮浏览器验证通过。


## 2026-10-15 会员卡报表验证

完整Gym测试209通过（171数据库业务、14文件服务、9文件controller、5类型、2限流键、2支付配置、2调度器、4整数分摊）。新增8数据库用例验证支付/消课/部分及全额退款统计、零价与分笔、门店租户隔离、历史快照、未归属现金、分页汇总及分摊写失败回滚；4分摊用例含100组随机权重逐分与独立分配序列比较。构建成功、SQL42/43已应用，verify-migrations.py独立MySQL两轮56表/35菜单/61示范授权。

设置GYM_TEST_PASSWORD及GYM_APPROVER_PASSWORD为既有开发测试账号密码后运行python3 script/gym/verify-card-report-api.py：51真实HTTP断言通过（14分组、净收0、持卡分组人数8、课消2），对照现有权益/课消和门店资金汇总，无新增业务数据。verify-sales-records-api.py回归48断言通过。最初遗漏两个必需环境变量导致脚本退出，补齐后通过。

浏览器会员卡报表加载与125门店筛选通过，14变13行，持卡/课消可见，截图/tmp/gym-card-report-admin.png。运行库真实交易仍0，支付/退款正向只在H2，不冒充真实厂商联调。前端源码未改。


## 2026-10-15 包月签约来源验证

完整Gym测试215项通过，新增6业务用例：服务端协议→独立合同→审批且无代扣/发卡；六并发同key仅一合同一审批；金额变化/解约及普通合同路径绕过拒绝；客户端换会员、跨店、已授权/带签约时间/带解约时间拒绝；无模板/审批人不可用回滚；门店租户隔离。构建及新后端启动成功。

verify-sales-records-api.py真实53断言通过，新增包月未知来源与换会员/价格拒绝；verify-contract-flow-api.py原有完整42项通过，contract5/member19保留，未请求真实电子签发送。前端包月页加载正常但无真实协议，只验证空态，截图/tmp/gym-subscription-signing-page.png。正向包月审批仅H2，不能当实际平台授权或有数据UI通过。源码未改前端。


## 2026-10-15 自动未签提醒验证

完整223项Gym测试通过：185数据库业务，既有文件/类型/限流/支付/履约定时器等34项，整数分摊4项，新增合同定时器2项（其他总计38项）。本轮新增6业务及2装配测试，含北京时间00:00窗口与次日、冻结模板、签署/取消时间保护、停用会员门店、6并发、审计失败事务回滚重试、无登录审计0、跨租户及过期跳过、102合同游标扫描、独立调度线程和enabled=false。

初次编译测试有断言引用未公开DTO字段及重复声明字段问题，修正后执行；回滚夹具原请求未注册JdbcTemplate，改用既有DataSource构造。真实无登录定时线程又发现creator=NULL，与H2登录上下文不同；修复显式系统审计0并补测试后完整223重新通过，构建成功。

真实日志15:57:15首次处理5份未签合同，之后5秒扫描不重复；原合同完整链新增contract6/member20后再1条。MySQL只读统计自动6条，requested_by/creator/updater均0且均failed；无推送/短信发送。verify-automatic-reminders-api.py15项HTTP验证通过，原verify-contract-flow-api.py42项回归通过。后者按人工提醒ID验证幂等，允许同合同存在独立自动记录。

浏览器合同5详情展示自动失败事件、App渠道未配置未发送，截图/tmp/gym-automatic-reminder-admin.png。前端git干净。当前本地QA调度间隔5秒/初始1秒，产品默认5分钟；真实渠道仍未配置，不能将失败记录当送达。

测试分类勘误：本条上文的185数据库业务/38其他项应为183数据库业务/40其他项，以本次Surefire XML为准；完整总计223项不变。其他项为文件服务14、文件controller9、类型5、限流2、支付配置2、履约定时器2、金额分摊4、合同提醒定时器2。


## 2026-10-15 负责会员关系与SELF验证

完整233项Gym测试通过（GymOrganizationServiceTest193，其他40）。新增10项业务覆盖负责会员列表/详情与真实权益、同店他人拒绝、共享会员另一店合同/体测不可见、本人负责体测写入与其他上传者报告拒绝、用户停用/换租户/非后台身份/员工停用/撤出任职、责任撤销、无SELF角色不能仅凭关系访问、跨租户、接口操作权限和自赋关系拒绝、六并发整体替换一行。还覆盖本人负责会员预约取消重约、签到、计次卡及私教包签课和撤销后记录拒绝。

SQL44两轮MySQL迁移57表/35页面/61示范授权通过，已应用并完成构建运行。最初新测试引用不存在的scope字段导致编译失败，改用既有Bean后全量重跑通过；无遗留测试失败。

独立SELF真实测试账号创建最初被自动审批拒绝，未执行任何账号/角色变更；用户随后明确答复允许创建并联调，才执行。verify-member-coach-api.py在最新构建47断言通过，创建/复用仅本脚本标识的user257/role175/employee9，负责member20/store125，contract6与measurement4。撤销后同token立即拒绝会员/体测/合同，恢复可读，专用资料保留。未修改既有账号或角色；脚本需GYM_TEST_PASSWORD、GYM_COACH_PASSWORD环境变量，不打印凭证。

既有verify-member-api.py28项通过（member21），verify-card-course-api.py最终46项通过（template38/product8/session8）。前端gymselfqa登录、只见1条会员、卡权益和体测记录可见，截图/tmp/gym-self-member-list.png、/tmp/gym-self-member-detail.png、/tmp/gym-self-measurement.png。过程中旧进程Jar被新构建覆盖导致类加载异常，新进程运行后恢复；浏览器已重新admin登录，截图/tmp/gym-admin-restored.png，前端git干净。

SELF真实预约写入、附属文件及通用排期选择未宣称通过；预约业务正向在H2，专用QA账号未额外授予预约写权限。文件及黑名单列表的一致范围继续审计。后台自动提醒当前恢复默认5分钟扫描。


## 2026-10-15 SELF合同及体测附件读取

完整Gym测试240项通过（197数据库业务+43其他），构建成功。新增7项覆盖实际绑定/业务权限/撤销、其他会员报告不可重绑、管理员已绑定报告编辑保留、共享附件、租户、黑名单列表、缺失权限响应和NULL门店历史附件。先停运行Java再package，随后通过run-local.sh运行新包。

用户明确同意专用角色gym_self_qa增加gym:files:read后，设置GYM_TEST_PASSWORD及GYM_COACH_PASSWORD运行verify-member-coach-api.py，47断言通过；再运行verify-member-file-access-api.py，24断言通过：member20合同6和measurement4关联报告真实字节与管理员下载一致，非负责会员原件、撤销后同token元数据/内容拒绝，恢复原负责员工集合后可读。测试资料保留，fixture为/tmp/gym-member-coach-fixture.json，无凭证。上传测试PDF由管理员完成，未授SELF上传权限。

此轮verify-gym-storage-api.py亦18项通过（file28/tutorial2，134字节PNG），未改前端。附件验证是HTTP原件字节证据，不宣称浏览器PDF渲染成功。


后续上传修复完整Gym测试242项通过（198数据库+44其他），新增2项验证有效负责门店/体测写权限、其他类别/门店/撤销、真实字节存储、未绑定仍不可读及存储写入次数。构建成功，新后端运行，附件HTTP24项再次通过。SELF上传正向仅H2/Mockito证据，专用QA角色未授files:upload，未宣称真实SELF上传已联调。管理员浏览器从measurement4“查看报告”打开file29的文件名、0.6KB和Blob下载入口，截图/tmp/gym-member-report-preview-admin.png，嵌入PDF依旧空白。

只读路由审计：`python3 script/gym/verify-api-coverage.py`，运行时OpenAPI147个管理端操作，实际调用/关联source/ResourcePage及手动解析通用编辑器共127个不同方法+路径要求，无缺失。通用root占位GET/PATCH在输出单列，并由脚本中的人工资源表展开；可配置--dashboard-root和--openapi-url。脚本不写业务数据、不读凭证、不改前端；只证明路由覆盖，不证明字段、权限或业务语义，不能替代各模块测试。


## 2026-10-15 训练档案、任职生效与SELF选课

完整Gym测试248项通过（204数据库业务+44其他），新增6项覆盖训练档案CRUD/归档/清空附件、会员门店不可变、空内容和错类别/跨店附件、跨租户、SELF负责关系/撤销、他人报告不可重绑、共享附件拒绝及controller权限；另覆盖未来入职/任职在北京时间生效前不授会员范围，及本人课程/排期/教练列表详情、其他教练拒绝、预约不能猜ID绕过、不能借选择权限写整店排期。原文件类别错误用例更新training_plan枚举，初始controller权限mock预期已修正后完整通过。

verify-migrations.py隔离库全量两轮通过：58表/35业务页面/61示范页面授权，临时库已删除。SQL45已应用本地运行库，248项后的构建成功，停旧Java后run-local.sh启动新版本，未覆盖运行Jar。

设置GYM_TEST_PASSWORD及GYM_APPROVER_PASSWORD运行verify-training-plan-api.py：30断言通过，管理员真实PDF、持久化、门店账号读取、归档/显式清附件、非法内容/错误类别/身份变更、跨店直接读取和修改拒绝。资料/tmp/gym-training-plan-fixture.json，当前plan1/归档2/file30/其他店plan3，无凭证；仅开发测试占位内容。

设置GYM_TEST_PASSWORD及GYM_COACH_PASSWORD运行verify-self-course-selection-api.py：29断言通过。管理员为QA员工9及同店另一教练各创建产品/未来排期，QA只读本人列表详情和教练选项；未增加角色权限、预约写或上传权限。撤销原负责关系中的员工9后原token列表空、详情拒绝，恢复完整原集合后恢复。fixture /tmp/gym-self-course-selection-fixture.json。浏览器QA课程安排共1条本人排期，截图/tmp/gym-self-course-selection.png；随后恢复admin登录工作台，前端git干净。SELF创建预约正向及越权仍为H2证据，不声称真实QA写入预约。

新OpenAPI151个管理端操作（另有公开加盟APP操作），verify-api-coverage.py127项既有前端路由无缺失。训练计划没有现有前端操作界面，不能宣称训练页面验收；未改前端。


SELF选课脚本重复运行改为校验并复用同一批标记产品/未来排期，避免同教练同时间重复创建冲突；实际再次运行31断言通过，仍product9/session9与另一教练product10/session10，无角色授权变化，负责关系原集合恢复。首次29断言作为首次创建证据保留。


## 2026-10-15 review修复：原始存储URL绕过

修复前只读查询file30存储地址并在未登录状态请求，真实HTTP200、614字节；未向日志/回答输出原始URL。修复后运行verify-private-file-url-api.py18断言通过：合同27、体测29、训练30的已知原始URL对匿名/管理员均403；编码路径拒绝，业务内容入口仍200且PDF长度与元数据一致。私有目录预签名和外部URL登记明确1001003006，未产生业务写入。

命令`mvn -q -pl yudao-module-gym -am test -Dtest='Gym*Test,FileServiceImplTest,FilePrivatePathPolicyTest,FileControllerPrivatePathTest' -Dsurefire.failIfNoSpecifiedTests=false`（沿用本机Mockito javaagent）通过：Gym248项+infra文件44项=292项，0失败/错误/跳过。infra为FileServiceImplTest39、策略2、controller3；本轮新增7项，覆盖解码/目录穿越/私有403不读存储、公开目录兼容、私有存储类型限制、禁止直传预签名及登记。构建通过，停止旧Java后run-local.sh新版本运行。

SELF原件HTTP24回归通过，撤销原token立即403并精确恢复原关系。真实数据库上传/教学HTTP18回归通过，file31/tutorial3，134字节PNG。浏览器admin打开file31图片正常显示及Blob下载入口，截图/tmp/gym-private-storage-frontend.png；前端git干净。

只读最新infra元数据去重盘点：修复前12条DB、18条S3，新增file31后DB共13。S3存量未执行迁移或对象权限变更，不把本地入口封堵当作外部桶撤权完成。


后续通用admin/app上传私有目录封堵增加1项测试，最新Gym248+infra45=293项全部通过；infra为FileServiceImpl39/策略2/controller4。新构建运行，原始URL脚本加入通用admin multipart上传负例后19断言通过；App上传负例仅单测，不宣称APP会员HTTP联调。


### SELF 改价与字段审计最新证据

完整Gym及infra文件命令同上，最新298项（253+45）通过，0失败/错误/跳过；含部门过滤模拟、跨租户账号拒绝和上下文恢复。SQL46双轮隔离迁移58表/35菜单/61授权通过，六类回填及三次执行幂等通过。

用户已明确授权后，设置GYM_TEST_PASSWORD运行authorize-self-price-qa.py，只合并专用标记角色的审批read/create和目录祖先。再设置GYM_APPROVER_PASSWORD、GYM_COACH_PASSWORD运行verify-self-price-approval-api.py：39断言通过，8并发同键单申请、当前范围、其他人不可读、撤销后拒绝、既有管理员审批改专用product9；测试资料保留，不改真实业务产品。第一次账号过滤失败轮次不计通过，遗留测试模板及管理员负例保留。

python3 script/gym/verify-frontend-fields.py只读实时OpenAPI与dashboard：31静态页面309字段名+4自定义页67嵌套路径，总376无缺失。修复嵌套Schema名称后工资明细正确。静态匹配不证明业务含义、所有动态字段或UI完整通过。浏览器SELF本人审批已通过详情截图/tmp/gym-self-price-approval-frontend.png；发起表单store下拉空，FRONTEND-SELF-APPROVAL-STORE-001，截图/tmp/gym-self-price-store-options-empty.png。前端不改，恢复admin。


### 2026-10-15 最终回归与浏览器补验

同一完整回归命令最新301项通过：Gym256（212数据库+44其他）+infra文件45，0失败/错误/跳过；mvn package通过，运行新包。新增3个教练生命周期测试先在旧版实跑1失败/2错误，再验证修复。verify-coach-lifecycle-api.py需GYM_TEST_PASSWORD/GYM_APPROVER_PASSWORD，当前27断言通过，使用独立HTTP-COACH-LIFECYCLE资料，店长6并发取消一次业务效果、预约取消/预占归零、余额未扣。verify-approval-flow-api.py最终运行包52断言通过。

verify-migrations.py完整脚本双轮通过58表/35页/61页面授权；包含SQL47现行权限完整性及撤旧权不补别名。verify-controller-permissions.py（GYM_TEST_PASSWORD）只读当前Java方法/类守卫和启用菜单：22控制器150方法45权限码均齐；不是形式化授权证明，不替代租户/门店/SELF HTTP负例。verify-api-coverage.py最新151/127无缺失，verify-frontend-fields.py376字段/路径无缺失。

SQL47在用户明确批准之后应用实库，读查gym_hq_admin=1条单数编辑授权、gym_store_manager=0、gym_self_qa=0。浏览器admin刷新权限后，member24专用未来生效黑名单新增、编辑原因、撤销、刷新重读通过，截图/tmp/gym-blacklist-permission-browser.png，资料保留。

SELF从本人产品“申请改价”入口已在原前端提交BROWSER-SELF-PRICE-product9-20261015，由既有gymstore批准，课程产品9实际149.99→150，截图/tmp/gym-self-price-browser-submitted.png、/tmp/gym-self-price-browser-approved.png、/tmp/gym-self-price-browser-price.png；不能将审批列表通用发起下拉限制泛化为所有SELF发起路径失败。已恢复admin。前端未改，本轮review已完成，用户授权的第三方和独立前端/旧外部存储限制见progress最终审计。


## 2026-10-16 格式化验证

- `mvn -N spotless:apply`：官方Maven插件依赖已下载并执行。最终根目录配置采用Spotless3.10.4/google-java-format1.36.1/AOSP；全部7,723个Java源/测试文件中7,504个变化，219个未变。
- `mvn -B -ntp -N spotless:apply spotless:check`：最终配置连续修正/检查通过，检查无待格式化文件；日志`/tmp/server-spotless-final.log`。
- 现有完整相关回归命令（Gym*Test及infra文件三组）通过301项：Gym256+infra45，0失败/错误/跳过，日志`/tmp/server-format-tests.log`。最终AOSP import次序统一后，再次`mvn -B -ntp -q -DskipTests -pl yudao-server -am test-compile`通过，日志`/tmp/server-format-final-compile.log`。
- 使用当前JDK词法扫描器与原Git源码核对，忽略注释、空白和import声明后，唯一差异为CRM类CrmReceivablePlanPageReqVO的三处`final static`规范化成`static final`，已逐项检查等价；其余声明/语句token及字面量值未变。import排序/未使用import清理由引擎处理，不把所有改动笼统称为仅空白。
- `verify-controller-permissions.py`兼容多行注解参数，离线逐方法比对150个PreAuthorize表达式前后完全一致。运行HTTP核对尝试遇当前48080未启动而连接拒绝，未将其记为本轮HTTP通过，也未为格式化任务自动启动服务。
- `git diff --check`通过，前端Git状态为空。原有未跟踪`script/gym/__pycache__/`保留。未提交、暂存或重置用户工作区；没有改业务规则。

## 2026-10-16 后端菜单分类验证

在 server 根目录运行：

```bash
mvn -B -ntp -N spotless:apply spotless:check
mvn -B -ntp -q -pl yudao-server -am package \
  -Dtest='GymAuthMenuTreeCustomizerTest,MenuServiceImplTest,AdminAuthServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
```

结果：Spotless检查7,726个Java文件通过，构建通过，42项测试（分类7、既有菜单服务18、认证服务17）全部通过。分类测试覆盖35页的分类与全部旧URL、无重复路由地址/ID/名称、局部角色只组织已授权页面、空目录不返回、显式隐藏及隐藏根目录、审批记录优先、报表前缀、未知菜单/输入不被修改、重复适配稳定和虚拟ID冲突。日志 `/tmp/gym-menu-format.log`、`/tmp/gym-menu-package.log`。

真实HTTP在替换后端前后分别登录并读取 `GET /admin-api/system/auth/get-permission-info`，比较角色、完整权限码集合、非空组件集合及旧gym地址：

| 账号 | 权限码数（前后相同） | 授权组件数（前后相同） | 原gym地址全部保留 | 返回gym分类数 |
| --- | ---: | ---: | ---: | ---: |
| admin | 1980 | 546（含系统模块） | 35 | 12 |
| gymadmin | 57 | 35 | 35 | 12 |
| gymstore | 39 | 26 | 26 | 9 |
| gymselfqa | 12 | 9 | 9 | 6 |

SELF没有财务/员工分类；各响应菜单ID无重复。比较数据仅保存本机 `/tmp/gym-menu-auth-before.json` 与 `/tmp/gym-menu-auth-after.json`，不入库、不纳入仓库、不记录令牌。部署运行方式仍为 `bash script/gym/run-local.sh --logging.file.name=/tmp/gym-menu-server.log`，本轮替换了本地IDE旧进程，当前以构建JAR运行48080，gym本地文件存储沿用4。

浏览器刷新现有5666前端，确认显示首页与门店/员工/会员/卡/课程/合同/审批/设备/内容/财务/报表分类；展开课程管理并点击课程产品，加载 `/gym/training/course-products` 及真实列表；直接访问原 `/gym/course-products` 同样加载列表。截图 `/tmp/gym-backend-menu-categories.png`。frontend `git status --short` 始终为空，没有修改或格式化前端代码。

已知前端适配限制必须随交付说明：原地址隐藏兼容入口可以打开页面，但首页用 `getMenuByPath` 查可见菜单的固定旧地址，因而部分快捷卡片被隐藏；同页旧/新地址会出现不同页签。浏览器已确认该现象，不能将本次证据写成“所有前端行为完全保持不变”。

## 2026-10-16 启动后数据库缺表验证

- `bash script/gym/seed.sh --schema-only`：26 个业务迁移执行成功，连续两次均退出 0；跳过权限/菜单/演示账号脚本。
- `python3 /tmp/jss-verify-startup-schema.py`：59 张 gym 表、872 个必需字段无缺失；服务启动前原有 64 张表的 INSERT 数据与迁移前备份一致。备份 `/tmp/jss-before-schema-repair-BA2bPx.sql`。
- 初始化脚本的隔离命令验证：26 个业务脚本均被选择且不含 system 表写入；模拟第 3 个 SQL 失败，退出 1，后续脚本未执行，原始错误与 `05-gym-salary.sql` 文件名均保留。`bash -n script/gym/seed.sh` 通过。
- `mvn -T 1C -DskipTests -pl yudao-server -am package`：BUILD SUCCESS，日志 `/tmp/jss-startup-build.log`。未将跳过单测的构建记为单测通过。
- `bash script/gym/run-local.sh --logging.file.name=/tmp/jss-runtime-verified.log`：10.206 秒启动成功，48080 监听；启动后观察超过两分钟，自动履约无缺表或批次失败。
- `python3 /tmp/jss-startup-http-smoke.py`：OpenAPI HTTP 200、真实 admin 登录、37 个管理端业务 GET 查询接口返回 `code=0`、未登录薪资列表返回 `code=401`。明细 `/tmp/jss-startup-http-result.json`。本构建未提供 `/actuator/health` 路由，就绪验证使用实际 OpenAPI 与业务接口；试探该路由产生一条 404 警告，与数据库修复无关。

本次未执行全部写入流程、并发或第三方渠道回归。上述 `/tmp` 文件是本机本轮验证证据，不是仓库中的常驻验证工具。

## 2026-10-16 dashboard 菜单登记修复验证

- 真实 admin（昵称“芋道源码”）修复前权限菜单只有2种gym组件：门店、员工；开发库type=2的gym菜单也只有2条。前端integration/menus.json包含35页。
- `bash script/gym/seed.sh --menus-only`：19个现有菜单登记脚本执行成功。执行前备份 `/tmp/jss-before-menu-repair-G33VJt.sql`；执行后、登录验证前，角色/角色菜单/账号/用户角色4表数据与备份一致。
- `python3 /tmp/jss-verify-dashboard-menus.py`：真实登录后35种gym组件与前端清单完全一致；35个组件文件全部存在；按前端过滤规则复核无页面丢失。认证返回12分类、11可见分类，现有隐藏策略保留。详细结果 `/tmp/jss-menu-verification.json`。
- 第二次执行 `--menus-only` 后，与第一次执行后的菜单INSERT数据逐字节一致，无重复菜单。
- `python3 /tmp/jss-verify-seed-modes.py`：仅菜单19、仅业务迁移26、完整初始化47个脚本范围通过；菜单模式没有角色授权/账号/业务表写入；业务模式无system表写入。两种局部模式模拟第3个SQL失败时立即停止，原始错误和对应文件名保留。`bash -n script/gym/seed.sh`、`git diff --check`通过。

本次仅通过真实认证接口、数据库及前端源码核对菜单链路，未进行浏览器视觉验收；前端源码保持未修改。动态路由已生成的会话需要退出重新登录获取新菜单。

## 2026-10-16 SQL执行阶段审计与临时缓存清理

本轮没有接入或执行Flyway，没有运行现有业务库的seed，没有启动后端或HTTP/授权脚本。

- 静态核对：`YudaoServerApplication`、当前POM依赖、主资源配置、seed.sh、Docker挂载及测试SQL初始化类。确认gym初始化依赖显式执行seed.sh；H2初始化属于数据库单元测试上下文；BPM当前禁用，Flowable选项不负责gym表。
- 只读本地MySQL：47个SQL提取的59张gym表与information_schema表名集合相同，缺少/额外均为空；没有Flyway/Liquibase历史表。只验证表名集合，不宣称列/索引或所有历史回填都已逐项比对。
- `PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py`：在随机隔离库回放47文件两轮通过，59业务/支持表、35页面、61示范页面授权，测试库自动清理；修复旧名单漏算gym_file，日志`/tmp/gym-sql-management-migrations.log`。
- `PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-legacy-seeds.py`：独立库验证2门店、2员工、2任职、3账号、2角色；两轮幂等、同名冲突拒绝及回滚通过，独立库自动清理，日志`/tmp/gym-sql-management-legacy.log`。
- 用`ast.parse`核对31个Python源文件，`bash -n script/gym/seed.sh script/gym/run-local.sh`通过；不导入或执行有业务副作用的HTTP脚本。
- 清理9个未跟踪pyc，共97,880字节；剩余0个。`git check-ignore`确认__pycache__与pyc规则生效；未删除SQL或Python源工具，47个历史SQL和开发seed内容均无diff；`git diff --check`通过。

完整待接入方案见`script/gym/README.md`首节；现有业务库未建立迁移基线、未改DDL或授权。此前文档“58表”源于验证器遗漏gym_file，实际完整DDL/本地库均为59表，此处勘误，历史记录不改写。


## 2026-10-16 拉取远端更新后的冲突验证

四处文档冲突已合并；保留远端seed.sh/GymFileController和原本地7文件改动，保持未暂存状态。离线执行`PYTHONDONTWRITEBYTECODE=1 python3 /tmp/verify-server-pull-seed-modes.py`，fake-mysql捕获三种模式47/26/19个SQL、失败第3文件即停、局部写入范围和互斥参数检查通过，不连接数据库。31个Python源文件AST、`bash -n script/gym/seed.sh script/gym/run-local.sh`通过。

`mvn -B -ntp -q -pl yudao-module-gym -am test -Dtest=GymFileControllerTest -Dsurefire.failIfNoSpecifiedTests=false`（沿用本机Mockito javaagent）通过9项测试，0失败/错误/跳过，日志`/tmp/server-pull-file-controller-tests.log`。`mvn -B -ntp -N spotless:check`通过7,726个文件，日志`/tmp/server-pull-format-check.log`。`git ls-files -u`为空，`git diff --check`通过，HEAD=origin/main=15cf33b3c8；未启动后端或运行现有业务库seed。


## 2026-10-16 Flyway版本迁移与独立开发seed落地

### 实际数据库回归

`GYM_TEST_DB_USER=root GYM_TEST_DB_PASSWORD='' PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py`：**31项检查通过**。使用真正的Flyway Maven13.10.3 + flyway-mysql + Connector/J9.7.0，5个随机MySQL测试库均自动清理。日志 `/tmp/gym-flyway-integration.log`。覆盖：

- 新库显式baseline0、V001～003、全部59表/35页面/当前控制器权限定义；生产迁移不产生账号、角色、部门或授权。
- 重复migrate不改历史；已管理库拒绝init；已应用文件校验和漂移拒绝；当前代码落后于数据库基线时拒绝seed。
- 开发环境声明、显式租户、当前迁移已完成；初次演示授权仍61条页面授权；重复seed逐表数据相同；不覆盖密码、不恢复撤销的角色菜单/用户绑定、不跨租户。
- 已有完整旧库核对结构后先备份，baseline1跳过V001，执行必要定义/回填，账号/角色/部门/数据范围/授权快照不变；会员阶段与门店关系必要回填执行。
- 列/索引漂移与SQL NULL和字面量NULL默认值差异均能识别；失败校验不生成迁移历史。
- 同名非种子账号和角色拒绝，事务回滚，不扩大既有访问权。

`verify-legacy-migrations.py` 和 `verify-legacy-seeds.py` 归档兼容验证通过：旧47脚本两轮59表/35页/61页面授权，旧开发导入2店/2员工/2任职/3账号/2角色与冲突回滚；各自随机库清理，日志 `/tmp/gym-flyway-legacy-history.log`、`/tmp/gym-flyway-legacy-seed.log`。47文件sha256及旧开发SQL字节内容与移动前保持一致。

### 当前开发库接管证据

- 只读完整结构指纹与V001匹配；三类待回填（会员门店关系、prospect阶段、source_coach）数量均0。
- 显式 `db.sh adopt-legacy` 成功，先完整mysqldump备份 `/tmp/gym-db-backups/ruoyi-vue-pro-before-flyway-20261016-205827-111751.sql`，1,577,567字节、权限600，再写 `flyway_gym_schema_history`：1/BASELINE、002/SQL、003/SQL，success均1。日志 `/tmp/gym-flyway-adopt-local.log`。
- 接管前后system_menu、system_role、system_role_menu、system_users、system_user_role、system_dept完整行哈希完全一致，未打印用户数据；只保留本机 `/tmp/gym-before-flyway-auth-hashes.json`。
- 接管后再次migrate提示up to date，历史记录逐字节不变；validate通过。未对现有库执行开发seed、未启动后端或修改前端。

### 静态检查与边界

Spotless apply/check检查7,726个Java文件通过、0改动；33个Python源文件AST、3个shell的bash -n、独立POM XML和GitHub workflow YAML语法检查通过；git diff --check通过，script目录缓存0。新增 `.github/workflows/gym-database.yml` 使用MySQL9.7与同版本容器客户端运行同一回归；本轮尚未在GitHub远端执行CI。

本机MySQL9.7.1超出Flyway声明的最新官方验证版本9.4，Flyway输出提示；上述是本机实际回归证据，不扩展为其他MySQL版本的验证声明。结构指纹绑定旧V001，用于保守接管，不代替未来增量迁移后的通用漂移检测。框架基座、H2测试资源和第三方集成不由本次Flyway接管。完整运行及失败处理见script/gym/README.md。

## 2026-10-16 数据库操作手册与贡献指南

新增 `script/gym/DATABASE.md` 与根目录 `CONTRIBUTING.md`，并在脚本 README、文档索引中添加入口。仅文档变更，未执行迁移、seed、应用启动或前端修改。

- 使用 Python `pathlib` / 正则逐项检查两份新文档：9 个相对文件链接全部存在，代码围栏成对且文件以换行结束。
- 核对文档中的数据库子命令与 `script/gym/database.py`；实际执行 `bash script/gym/db.sh --help`、`bash script/gym/seed.sh --help` 均退出 0，未连接数据库。
- `git diff --check` 通过。本轮无 Java 或 SQL 修改，无需重新运行 Java 格式化和数据库集成回归；此前迁移回归证据见上节。

## 2026-10-24 商城日统计结构与完整迁移链

- `python script/jss/verify_statistics_schema.py`：在拥有的 MySQL 8.4.9 隔离实例（13316）创建随机库，四项通过后清理自己的库。覆盖两个当前 DO 全部持久化列、空表无伪造统计、租户/日期/商品唯一性、逻辑删除后重算、交易日维度而非时间戳唯一，以及有符号佣金冲回。
- `python script/jss/verify_migrations.py`：九项通过；执行包含 V009 的全部当前迁移，验证 baseline 0、禁止环境覆盖历史表、无隐式账号/绑定/角色授权、重复迁移保留数据、拒绝重复 init、校验和漂移、缺框架和未托管表。
- 上述是隔离 MySQL/Flyway 证据，未将其视为实际业务库部署、HTTP 报表或支付联调通过。V009 不修改 V001～V008，也不触碰已有工作区业务改动。

## 2026-10-19 美团验券历史 POST 请求

仅本地模拟与构建；未启动或重启应用、未访问美团生产接口、未运行数据库迁移或修改前端。业务范围、配置及官方文档见 [团购验券业务说明](group-buy-verification.md)。

在 server 根目录实际执行：

```bash
mvn -o -B -ntp -N spotless:apply spotless:check
mvn -o -B -ntp -pl yudao-module-gym -am test \
  -Dtest='GymMeituan*Test,GymGroupBuy*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine="-javaagent:${HOME}/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar"
mvn -o -B -ntp -pl yudao-server -am -DskipTests package
git diff --check
```

结果：Spotless 检查通过；15 测试、0 失败、0 错误、0 跳过；服务端及启用依赖链 package 成功（该打包命令跳过测试，测试证据来自前一命令）。本机 JDK 25 的 Mockito 动态自挂载首次失败，随后通过显式加载已有本地 Mockito 5.23.0 agent 完成验证，未改项目测试运行配置。

| 测试类 | 数量 | 验证内容 |
| --- | --- | --- |
| GymMeituanCouponClientTest | 7 | 官方签名固定示例、表单编码、biz 类型序列化、券码前导零与金额精度、配置隔离、HTTP/业务/格式错误 |
| GymMeituanConfigurationTest | 2 | 默认关闭且空 developerId 可启动；按租户/门店绑定配置 |
| GymGroupBuyControllerTest | 4 | JSON POST、默认分页、6 类无效参数、功能权限拒绝及 GET 不支持 |
| GymGroupBuyServiceTest | 2 | 门店校验在出站调用之前、租户取自上下文、无权限不访问平台 |

MVC 测试使用项目实际全局错误转换器，错误沿用 HTTP 200 + CommonResult 业务 code（例如 400/403/405），不强行改为其他状态契约。出站 HTTP 使用 MockRestServiceServer，未使用真实 token。

文档检查：对 README、architecture、group-buy-verification 及归档契约的 18 个相对文件链接逐项验证，全部存在，代码围栏成对。dashboard 工作区只读检查为空，无前端改动。历史契约移至 archive，CHANGELOG 历史条目保持不变。


## 2026-10-19 公共请求 DTO 与本轮代码审查

公共参数移至 `MeituanCommonRequest`，Client 通过 setter 构建请求，仅在签名/表单边界调用 `toParameters()`。DTO 不保存 signKey、不生成包含 token 的 toString；biz 保留同一份序列化原文。业务和公共参数的类型边界见 [业务说明](group-buy-verification.md)。

审查范围：本轮 Controller、请求/响应模型、Service、Client、配置和错误码、四个测试类，以及 docs 整理和 application.yaml 的全部变更。重点核对功能权限、租户及门店范围、授权选择、默认关闭、签名/编码一致性、超时和平台业务失败、券码和金额类型，以及真实接入边界。未发现需要阻止当前请求骨架交付的代码问题；真实平台兼容性不属于这次本地验证结论。

新增多业务授权 realOpBizCode 的编码/签名回归及网络超时回归，补强 MVC 成功响应的嵌套 code/traceId/券码/isVoucher 字段断言。使用上一节同一测试命令，最终 **17 测试、0 失败、0 错误、0 跳过**（Client 9、配置 2、Controller 4、Service 2）；Spotless apply/check 通过。文档六个文件的 24 个相对链接及围栏检查通过，git diff --check 通过。服务端 package 验证单独记录在当次 CHANGELOG；未调用美团生产接口，前端未改。


## 2026-10-19 服务零售团购核销全目录请求框架

范围为官方“生活服务 → 服务零售 → 团购核销API”全部16项，官方链接、路径和字段类型详见 [API清单](meituan-service-retail-api.md)。本轮不访问平台生产接口、不运行目标数据库迁移、不修改角色或前端、不启动/重启业务服务。

在 server 根目录运行：

```bash
mvn -o -B -ntp -N spotless:apply spotless:check
mvn -o -B -ntp -pl yudao-module-gym -am test \
  -Dtest='GymMeituan*Test,GymGroupBuy*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine="-javaagent:${HOME}/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar"
mvn -o -B -ntp -pl yudao-server -am -DskipTests package
git diff --check
```

最终相关回归 **114项，0失败、0错误、0跳过**。Spotless apply/check通过；服务端及启用依赖链package成功（该命令跳过测试，测试结果来自前一命令）。沿用既有本地Mockito agent配置，无需下载新依赖。

| 测试类 | 数量 | 证明范围 |
| --- | --- | --- |
| GymMeituanRetailClientTest | 23 | 独立16项文档契约的POST路径、58业务编号、表单/签名、全部业务字段及类型化响应；餐饮混用、租户门店隔离、默认状态变更关闭、6项变更超时不重试、异常/部分执行结果处理 |
| GymMeituanRetailControllerTest | 74 | 16个真实Controller→Service→mock Client链路、16项功能权限拒绝、16项门店拒绝、25种参数错误、缺少可信身份拒绝；操作人伪造值被覆盖，租户取自上下文 |
| GymMeituanCouponClientTest | 9 | 旧餐饮历史接口显式业务编号1下的签名、表单、响应及错误回归 |
| GymMeituanConfigurationTest | 2 | 空凭据默认关闭可构建；租户门店配置绑定且默认业务编号58 |
| GymGroupBuyControllerTest | 4 | 旧本地入口参数、权限及响应契约回归 |
| GymGroupBuyServiceTest | 2 | 旧入口门店校验顺序和上下文租户回归 |

HTTP使用MockRestServiceServer；MVC保留实际方法权限代理、请求校验及GymMeituanRetailService，门店数据服务与平台Client为mock。平台接口样例独立记录在 yudao-module-gym/src/test/resources/meituan/retail-contracts.json，期望路径不从生产枚举生成。批量count是字符串、普通count是整数；预退款对象与退款查询数组分别反序列化；券码前导零、大整数和BigDecimal金额单独断言。部分执行错误1004回归验证返回结果待确认，不一概判定全量失败。

完整性静态核对：16个文档路径与MeituanApi零售白名单、16个本地POST路径、Client/Service方法逐项匹配，16个方法禁用请求/响应体访问日志。检查README、architecture、业务说明、API清单的23个相对链接及代码围栏均通过。SQL V004只检查了权限定义和不含角色/用户授权的静态内容，未执行MySQL迁移，不能宣称真实数据库验证。dashboard工作区保持干净。

审查覆盖DTO参数、两类商品接口标题/路径差异、分页起点、公共签名原文一致性、授权场景隔离、方法与门店权限、可信操作人、变更默认关闭、超时/部分成功语义，以及文档真实性。已修复“变更的非成功业务结果可能部分执行却被一概报告拒绝”的问题；本轮请求框架未发现其他阻塞问题。真实平台返回差异与行业开通条件仍需后续联调验证。

## 2026-10-23 小程序会员身份、本人查询与私有文件

本批实现 17 个新的小程序接口，另复用 3 个认证和 1 个加盟源码路由；[135 个接口总表](uniapp/uniapp-api-summary.md) 与 [实施边界](uniapp/uniapp-implementation.md) 区分待开发及第三方暂缓部分。未修改前端，未启动业务服务或迁移业务数据库。

在 server 根目录执行：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check
mvn -B -ntp -pl yudao-module-gym -am test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine="-javaagent:${HOME}/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar"
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
git diff --check
```

最终 gym **404 项通过、0 失败、0 错误、0 跳过**；所选 member 状态测试 **4 项通过、1 项既有禁用测试跳过**。Spotless apply/check 通过；server 及依赖 package 成功（跳过测试，完成于 22:57:33）。显式 Mockito javaagent 避免受限环境自附加失败；根 `-N` 模式本地 BOM 解析失败，因此本次使用包含依赖的 reactor 格式化命令。日志位于本机 `/tmp/uniapp-final-tests.log`、`/tmp/uniapp-final-package.log`、`/tmp/uniapp-format.log`，不提交临时日志。

| 新增测试 | 数量 | 证明范围 |
| --- | ---: | --- |
| `GymMobileMemberServiceTest` | 23 | H2、真实 MyBatis、租户拦截与事务；并发首次关联、同键重放及冲突/回滚、保留客户阶段、会员与租户隔离、分页 total、历史门店/计划、日期边界、头像归属、私有文件和人脸拒绝、合同引用；卡的当前适用门店和有效状态分页、课包筛选与余额 |
| `GymMobileIdentityControllerTest` | 4 | MockMvc 请求头及参数校验、JSON 禁止手机号/未知字段、头像 null 与省略区分、通用手机号入口拦截 |

跨模块 `MemberUserApi` 和 `FileApi` 为 mock；Service 测试保留真实数据库、租户 SQL 改写和业务逻辑。MVC 使用 standalone 控制器及异常处理，不代表部署后的过滤链或真实微信 OAuth 已验收；没有把真实第三方支付、自动续费、电子签约、设备或监控当作已验证。

隔离 MySQL/Flyway 回归 **34 项通过**，临时库全部清理；日志 `/tmp/uniapp-migrations.log`。验证 V005 后 62 张 gym 表、member_user 关键列和会员关联唯一索引；保留旧基线接管校验、升级幂等、已应用文件校验和拒绝、未知未来版本与未迁移 seed 拒绝等原有检查。只操作随机测试库，未升级现行业务库。新增迁移的生产结构不再以旧 59 表基线指纹判定完全相等；旧指纹仍用于旧库接管。模拟待应用版本改为扫描后递增版本，避免与已有 V004 冲突而错误通过检查。

接口审计结果 **135 个契约、21 个源码映射、114 个缺失**；总表/机器清单的唯一方法与路径一致，无重复目标路由，已实现条目的证据文件存在。`--require-all` 目前应失败，不能宣称全量接口已完成。文档链接、代码围栏和 `git diff --check` 通过；dashboard 工作区检查为空。原始 `docs/uniapp/uniapp.md` 的用户已有暂存/未暂存变更保持原样。

## 2026-10-23 小程序预约、课消查询及两端并发

在前述小程序测试命令基础上，使用同一完整选择器 `Gym*Test,MemberAuth*Test,MemberUser*Test`：最终 **gym 425 项通过、0失败/错误/跳过**，member所选状态测试4项通过、1项既有禁用测试跳过。`GymMobileBookingsServiceTest` 新增15项真实H2/MyBatis/事务测试，`GymMobileBookingsControllerTest` 新增5项MockMvc请求/响应测试，`GymOrganizationServiceTest` 增加1项后台与会员混合并发测试（该类共213项），另补既有后台签课的真实余额快照断言。之前的17个移动接口与已有后台用例一起回归。

覆盖重复创建不二次预占、相同键变更请求报冲突、取消原因持久化、重复取消/重订后旧键重试、截止时刻和提前量、教练离职/门店停用后的释放、释放中途异常的整笔回滚、本人及租户隔离、禁入黑名单、产品与课包有效期、日期按课程开始时间筛选、completed映射、关联签到/课消越权防护、消费时余额与当前余额区分，以及课程可约状态。并发覆盖：两会员抢一时段、同一会员抢重叠时段、同一课包最后一课时抢两个相邻时段、两个取消请求、后台与会员混合创建。线程使用独立安全/租户上下文及实际事务，业务Mapper未mock；账号API仍mock，未替代部署后登录验证。

```bash
# 本批聚焦回归：260项通过（移动专项47 + 组织/后台213）
mvn -B -ntp -pl yudao-module-gym -am test \
  -Dtest='GymMobile*Test,GymOrganizationServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine="-javaagent:${HOME}/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar"
# 最终验证同时执行前节完整测试、Spotless和server package命令
```

真实MySQL/Flyway隔离回归 **35项通过**：新增检查V006两列存在、允许NULL且默认NULL，所有随机测试库清理完成。没有向业务库应用V005/V006。Spotless apply/check与server package通过（打包跳过测试，完成于23:18:29）；源码路由审计 **27 / 135**，余108项明确待开发。日志：`/tmp/uniapp-booking-final-tests.log`、`/tmp/uniapp-booking-format.log`、`/tmp/uniapp-booking-package.log`、`/tmp/uniapp-booking-migrations.log`。这些是本机验证，非远端CI、部署HTTP或真实第三方渠道联调。

## 2026-10-23 移动工作台档案、跟进和版本分配

新增总表89–91、98–103共9项。最终完整选择器 `Gym*Test,MemberAuth*Test,MemberUser*Test` 的 **gym 442项通过、0失败/错误/跳过**，member所选4项通过、1项既有禁用跳过。使用前节同一Java25/Maven命令和显式Mockito javaagent。`GymOrganizationServiceTest` 新增12项工作台回归后共225项，`GymMobileWorkControllerTest` 新增5项MVC检查；现有会员、预约和后台业务一起回归。先前229项聚焦回归在新增任职日期用例前通过，最终以完整442项为准。

验证内容：9个Controller方法逐项实际通过方法权限代理拒绝并核对所需权限码；有效管理员、禁用账号、会员令牌、租户不匹配；SELF只能操作负责会员、撤销关系后的旧键重放拒绝；体测范围、计划失败回滚后原键重试、合同真实权益归属及公开字段；跟进作者取自员工关联、未来联系日期与过期后的合法重放、租户/负责关系隔离；分配的旧后台版本递增、不变集合不增版、过期版本/重复员工/未来入职任职拒绝、其他门店版本不变和两个并发请求只成功一个。所有业务测试保留真实H2、Mapper、租户拦截、事务和服务；system账号/权限API及文件存储为mock。

MVC通过全部9个路由验证参数绑定、5个写入口的必填幂等头、拒绝伪造creator/coachId/employeeId/signingStatus、日期时区转换、分页界限、体测范围、空跟进正文及缺失版本。MVC standalone不证明部署过滤链，功能权限的证据来自上述有Spring Security代理的组织测试。

Spotless apply/check与server package成功（打包跳过测试，完成于23:43:48）。新增V007后的真实MySQL/Flyway隔离回归 **36项通过**：63张gym表，已有门店关系版本列非空且默认0；所有随机库已清理，无业务库迁移。日志：`/tmp/uniapp-work-final-tests.log`、`/tmp/uniapp-work-format.log`、`/tmp/uniapp-work-package.log`、`/tmp/uniapp-work-migrations.log`。最终只清理了新访问服务的类型导入，无行为变化，随后格式化并重新打包通过。

源码审计 **36/135映射、99缺失**；机器清单为32项新增本地实现、4项复用源码、99项待开发。文档链接/围栏与git diff --check通过。工作台会员列表/详情、文件、角色上下文等仍待开发；特别是assignmentVersion的设计读取入口尚未交付。旧后台分配保持原无期望版本的整体替换行为，只在修改后更新共享版本；不能把移动端版本校验误称为所有旧客户端都受乐观锁保护。未改前端、角色授权，未做真实外部渠道或部署HTTP联调。

## 2026-10-24 工作台会员列表、详情与权益摘要

总表87–88两个GET入口已实现。执行与前节相同的reactor Spotless apply/check、完整 `Gym*Test,MemberAuth*Test,MemberUser*Test` 测试和显式Mockito javaagent：最终 **gym 452项通过，0失败/错误/跳过**；member所选4项通过、1项既有禁用跳过。`GymOrganizationServiceTest` 本批新增9项后共234项，`GymMobileWorkControllerTest` 新增1项后共6项。已有权限测试还增加两个新Controller方法的真实权限代理拒绝及`gym:members:read`核对。第一次聚焦238项通过后补充了跨租户子表及无限期卡测试，最后以452项完整回归为准。

新增证据覆盖：多门店会员不重复计数、分页后空页保留total、脱敏响应无原手机号/内部字段、分配版本读取闭环；SELF分页与撤权、指定门店拒绝、其他门店权益不混入；实际卡门店关系、到期上下边界和默认30天、未来生效/到期/失效/耗尽排除；私教课包剩余与预占分别合计；潜客按客户阶段而不是消费历史、员工筛选与门店关联、关键词参数绑定；恶意跨租户子表引用既有会员ID不会影响筛选或摘要；无期限卡数量及null到期时间；无效账号/令牌/租户和分页范围拒绝。HTTP测试核对两个GET路由、全部列表参数、详情必填门店和非法筛选拒绝。业务测试使用真实H2、MyBatis、租户拦截和事务，跨模块账号及权限API仍mock；不能等同部署后HTTP联调。

日志：`/tmp/uniapp-member-query-final-tests.log`（23日23:58:10完成）、`/tmp/uniapp-member-query-format.log`、`/tmp/uniapp-member-query-package.log`。server依赖打包成功（23日23:58:36完成，跳过测试）。本批没有数据库结构或迁移脚本变更，因此没有重复执行MySQL迁移；上一批36项隔离迁移证据仍属其原批次，不冒充本批新验证。

路由审计 **38/135映射、97缺失**，清单为34项新增本地测试实现、4项已有源码接口。总表与JSON条目方法/路径一致，文档链接/围栏及`git diff --check`通过。dashboard工作区为空，原始uniapp.md用户变更保留。未部署、未迁移业务库、未自动授权角色；四类第三方实际接入继续暂缓。

## 2026-10-24 公开门店、首页、配置与媒体引用

总表7–11五项实现，另提供不计入135项目标的公开媒体辅助GET。最终执行相同reactor Spotless apply/check、`Gym*Test,MemberAuth*Test,MemberUser*Test`及显式Mockito javaagent后，**gym 471项通过、0失败/错误/跳过**，member所选4项通过/1项既有禁用跳过。`GymOrganizationServiceTest`新增11项后共245项；新增`GymMobilePublicControllerTest`4项和`GymPublicMediaTenantFilterTest`4项，本批合计19项。先前470项全量通过后发现加盟模型组引用失效应整组撤销，补充该行为及回归；最终以471为准。

业务证据覆盖：匿名租户内active门店、停用/其他租户详情拒绝、总数及分页；经纬度成对/范围、有坐标距离排序先于分页、跨日界线球面距离和缺失位置最后；环境素材真实引用、门店归属、私有类别拒绝、存储URL不外露、下架/跨租户拒绝及实际内容魔数；上海零点成功入场计数，排除失败/未来/其他门店/租户记录，实时人数保持未知；配置总部回退、禁用门店整份覆盖、App福利分平台、三模型的数量/图片/URL及失效组撤销；公告发布和到期边界、去重验证。

MVC实际绑定五个公开路由及辅助媒体路由，检查匿名注解、参数拒绝、媒体全量200、字节范围206/Content-Range、越界416、未发布404、no-store及nosniff。媒体租户测试组合真实`GymPublicMediaTenantFilter → TenantContextWebFilter → TenantSecurityWebFilter`，证明只处理精确GET媒体路径、不改变已有header、不接受多值/非法/溢出选择器、支持context path及Long上界、保留租户validTenant调用及上下文清理、拒绝登录租户不一致和过期租户。租户状态API与文件存储仍mock；没有把这些测试冒充完整部署的Spring Security链、真实对象存储或代理端验收。

日志为`/tmp/uniapp-public-final-tests.log`（00:23:57完成）、`/tmp/uniapp-public-format.log`和`/tmp/uniapp-public-package.log`。最终server及依赖package成功（00:26:46完成），跳过测试；行为修改后的最终测试与打包均重新执行；最后为新增公开VO及配置子对象指定唯一OpenAPI模型名，避免Notice/Model等短类名冲突，随后重新格式化及打包验证。首次聚焦运行的失败来自测试上下文缺少WebProperties.adminUi及签到夹具缺少bookingId、重复Mockito抛错桩，均修正后通过，未放宽生产校验。

本批无DDL或迁移脚本变更，不重复执行迁移回归，不使用真实第三方渠道。覆盖审计 **43/135映射、92缺失**；JSON与总表为39项新增本地测试实现、4项已有源码。已检查相对文档链接、代码围栏及diff；dashboard工作区为空。App下载/品牌/模型需管理员提供实际发布内容，没有写入演示素材或虚假人数。

## 2026-10-24 公开卡、私教与包月商品目录

目标14–16、27已实现。沿用上述reactor Spotless、完整`Gym*Test,MemberAuth*Test,MemberUser*Test`及显式Mockito agent命令：最终gym **480项通过、0失败/错误/跳过**，member所选4项通过、1项既有禁用跳过。组织服务新增7项后共252项，新增商品MVC2项，共增加9项。执行中断后继续原任务，最终测试于18:51:13完成；未把中断状态当作成功。

业务证据覆盖真实门店售价/模板适用范围、销售时窗边界、过滤后分页总数、停用与草稿拒绝、缺省门店确定选择和租户隔离、坏JSON关闭展示；包月成对条款、协议版本、周期价与账单参数；私教在职和门店任职日期先于分页、明确公开字段；版本创建1、真实编辑递增、无变化保持、审批后变价递增以及拒绝审批不增版。MVC验证全部4条路径、匿名注解、列表必填门店和详情可选门店/必填类型。H2与事务为真实，跨模块API仍mock，未做部署HTTP或外部渠道联调。

真实MySQL/Flyway隔离回归 **38项通过**，随机库已清理；新增V008非空默认1及旧商品价格/课时保留验证，gym仍63表，未迁移业务库。日志：`/tmp/uniapp-catalog-migrations.log`（00:40:25）、`/tmp/uniapp-catalog-format.log`（00:41:01）、`/tmp/uniapp-catalog-final-tests.log`（18:51:13）、`/tmp/uniapp-catalog-package.log`（18:52:34）。server及依赖打包成功，打包跳过测试。源码审计47/135，43项新增本地实现、4项复用源码，余88项待开发。

## 2026-10-24 教练公开资料与查询

总表35–36两个GET实现。最终执行相同reactor的`spotless:apply spotless:check test`、选择器`Gym*Test,MemberAuth*Test,MemberUser*Test`及显式Mockito javaagent：gym **489项通过、0失败/错误/跳过**，member所选4项通过、1项既有禁用跳过。组织服务新增7项后共259项，教练MVC新增2项；首轮组织服务/MVC聚焦260项通过后增加共享引用撤销用例，最终以489为准。

业务测试验证：员工默认不公开、显式发布、筛选先于分页/total、匿名公开投影无手机号或内部字段；租户和任职子表隔离、跨门店详情拒绝、未来入职/任职日期、离职和停用门店；发布沿用共享员工全部门店写范围；私有/跨租户/其他门店图片、重复资质或擅长项、超长介绍拒绝，失败不留下部分员工；省略发布对象保持原值、清空图片、下架与文件删除后仍可下架；公开图片按当前任职撤销，多个发布引用保留到最后一个失效。坏JSON不带出图片或内部字段。MVC核对两条路径、匿名注解、必填门店、有界分页及参数绑定。

真实MySQL/Flyway隔离回归 **40项通过**，新增V009默认私有/无资料列验证及历史员工不自动公开、手机号保持验证；随机库全部清理，仍63张gym表，未迁移业务库。最终日志：`/tmp/uniapp-coaches-final-tests.log`（19:10:02）、`/tmp/uniapp-coaches-migrations.log`、`/tmp/uniapp-coaches-package.log`。Spotless通过，server及依赖package成功（跳过测试）；未做部署HTTP、真实对象存储或第三方联调。源码审计49/135，45项新增本地实现、4项复用源码、86项待开发。

## 2026-10-24 本人教练资料与私有草稿预览

总表83–84实现，另加不计入135项的本人绑定图片读取辅助路由。最终执行上述reactor `spotless:apply spotless:check test`、`Gym*Test,MemberAuth*Test,MemberUser*Test`选择器和显式Mockito javaagent：gym **499项通过、0失败/错误/跳过**，member所选4项通过、1项既有禁用跳过。组织服务新增7项后共266项，新增本人资料MVC3项，共10项。

覆盖：真实在职员工和后台账号/令牌/租户校验、数据库唯一账号绑定约束、未来入职和任职日期、停用门店；已有资料及真实私教商品两种教练依据，初次草稿不自动公开；SELF只改本人字段，其他员工、姓名、手机号、账号、发布状态保持，记录更新人为本人；空对象不写、字段省略/清空/null拒绝、重复特长及回滚；新图片上传人、类别、当前任职门店、租户检查，旧管理员绑定可保留；私有草稿按当前绑定读取，未绑定/已清除/其他员工及内容魔数不符拒绝；两个并发事务更新不同字段后两者都保留。

实际Spring方法安全代理验证三个入口拒绝匿名；MVC standalone验证GET/PATCH和辅助媒体路径、字段存在性、拒绝伪造身份/发布状态和超长请求、原始字节及no-store/nosniff、未找到404/非本人403。数据库、Mapper、事务及租户拦截为真实H2；账号API和存储仍mock，不等同部署过滤链与真实存储联调。首轮测试试图构造同租户重复账号绑定，被现有唯一索引拒绝；修正为验证约束拒绝，生产约束没有放宽。

日志：`/tmp/uniapp-coach-self-final-tests.log`（19:22:34完成）、`/tmp/uniapp-coach-self-package.log`。Spotless及server依赖package通过（跳过测试）。无DDL或迁移变更，未重复执行MySQL迁移，上一批40项证据保留原批次归属；部署仍需V009。源码审计51/135，47项新增本地实现、4项复用源码、84项待开发。工作台文件上传/下载仍待补齐，未改前端或原上传授权。

## 2026-10-24 工作台文件与不可变上传目标

总表74、76实现。最终执行上述reactor `spotless:apply spotless:check test`、选择器`Gym*Test,MemberAuth*Test,MemberUser*Test`和显式Mockito javaagent：gym **511项通过、0失败/错误/跳过**，member所选4通过/1既有禁用跳过。组织服务新增9项后共275项，工作台文件MVC3项，共增加12项。

验证SELF教练上传→本人资料绑定→私有预览闭环；固定员工目标和账号重绑拒绝；文件功能权限及业务权限同时要求，真实Controller代理拒绝无功能权，非法令牌/租户和缺失目标拒绝。体测/训练记录通过原业务服务绑定，其他会员（包括整店管理员尝试）被拒绝；报告上传人取消负责关系后失去下载权，另一负责员工在绑定前不可读、绑定后可读。会员合同材料不能作为公共模板，无会员模板材料需整店权限；上传不写签署状态。类型魔数、声明不匹配、大小、文件名清理、随机存储名、缺失/类型不符的下载、历史文件范围均覆盖。

MVC验证multipart参数、伪造上传人/员工/类别/租户拒绝、缺失文件/非法用途/门店/会员、HTTP头不能代替表单门店、二进制响应及no-store/nosniff、404/403。HTTP测试发现当前Spring扩展绑定器会加入请求头，已在该Controller关闭header绑定并保留表单白名单；补上下载nosniff。首轮两条业务断言缺少带参数错误文案，修正断言后通过。

真实H2事务测试还验证后续回滚同时移除gym_file及目标行，但mock外部存储对象仍存在，自动孤立对象清理未实现。功能和数据授权用真实业务服务与权限代理，账号/存储API仍mock；没有做部署HTTP和真实存储联调。

真实MySQL/Flyway隔离回归 **42项通过**：64张gym表、tenant_id/file_id唯一目标、历史文件不补造目标且保留元数据；随机库全部清理，未迁移业务库。日志：`/tmp/uniapp-work-files-final-tests.log`（19:46:06）、`/tmp/uniapp-work-files-migrations.log`、`/tmp/uniapp-work-files-package.log`。Spotless及server依赖package成功（跳过测试）。源码审计53/135，49项新增本地实现、4项复用源码、82项待开发。

## 2026-10-24 工作台上下文与系统权限API

验证命令（仓库根目录）：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test,PermissionApiImplTest,PermissionServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **518项通过**（组织服务280项，新增5项上下文范围/身份业务测试；上下文MVC新增2项）；system权限 **27项通过**（新增PermissionApiImplTest 3项、既有PermissionServiceTest 24项）；member状态4项通过、1项既有禁用测试跳过，均无失败或错误。Spotless通过，server及依赖打包成功（打包命令跳过测试）。源码映射 **54/135**，余81项，不能代替部署HTTP联调。日志：`/tmp/uniapp-work-context-tests.log`、`/tmp/uniapp-work-context-package.log`。

覆盖无员工的总部账号、租户隔离、停用门店、空范围与非实体部门、入职/任职生效日期、离职、停用账号、错误令牌类型及匿名方法鉴权。本人任职门店可以显示，但没有SELF负责关系或整店范围时，真实会员列表仍拒绝。系统API测试执行真实菜单停用父子过滤，停用角色不参与菜单解析，重复/空权限码不输出；超级管理员保留原角色及菜单解析。MVC验证请求身份参数不用于选择账号及错误信封。首轮新增测试使用无类型Mockito匹配器导致重载编译歧义，改为集合匹配器后完整验证通过。

无DDL变更，不重跑已有V010隔离迁移回归，也不迁移业务库。真实后台登录、缓存撤权及部署端到端联调未在本批验证；前端未修改。

## 2026-10-24 工作台预约、签到与课消查询

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **527项通过**（组织服务286项、工作台课程查询MVC新增3项），member状态4通过/1既有禁用跳过，均无失败/错误。Spotless通过；server依赖打包成功，打包命令跳过测试。源码路由 **58/135**，余77项。日志：`/tmp/uniapp-work-course-final-tests.log`、`/tmp/uniapp-work-course-package.log`。此前先运行组织服务286项通过，日志`/tmp/uniapp-work-course-tests.log`。

新增6项H2/事务/实际Mapper业务测试，通过原后台创建真实私教产品、排期、权益、预约、签到和确认课消，核对移动查询的历史余额和状态。权限矩阵覆盖本人授课但不负责会员、负责会员但其他教练授课、整店范围、跨租户、撤销负责关系、无功能权限及错误令牌；分页总数与明细均应用范围，SELF允许查看负责会员的其他教练签到但不能查看其预约/课消。

日期测试覆盖上海午夜左闭右开与失败/未确认行排除；缺失余额快照不伪造，停用门店历史在整店授权范围仍能查。人为错绑会员、门店或删除排期时，不产生错误签到标记、课消链接或可访问的预约详情。MVC验证全部四路由及必传/非法日期、状态、分页上限、正整数筛选、明确响应字段。无DDL，不重跑V010迁移或迁移业务库；本批未运行部署HTTP、真实设备或扫码签课联调。

## 2026-10-24 工作台排课与代约

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **538项通过**（组织服务294项，新增8项业务；新MVC3项）；member状态4项通过、1项既有禁用跳过。无失败/错误。Spotless、server依赖打包通过，打包命令跳过测试。源码映射 **60/135**，余75项，仍不代表全部完成或上线。日志：`/tmp/uniapp-work-course-write-final-tests.log`、`/tmp/uniapp-work-course-write-package.log`。

新增业务测试覆盖本人无负责会员时排课但旧后台整店写仍拒绝、他人教练和未生效任职拒绝、未来/时长/商品/门店/冲突校验、相邻时间允许、同键参数冲突、当前权限撤销后不能重放、实际预约预占及取消后重放不重复预占、无效课包和错误身份不留写入。

在H2真实Mapper/事务环境并发运行移动与旧后台：同教练重叠排期仅一条提交、同名额预约仅一次预占；两个并发相同键只生成一个排期和一份预约，两个操作可以使用相同键且相互隔离。对幂等响应列增加临时失败约束，证明保存结果失败时排期、预约、课包预占和请求键一起回滚，移除约束后原键可成功重试。MVC验证键、带时区时间、非法类型/ID/额外状态字段及业务错误信封。

本批无DDL，未迁移业务库、未重跑V010隔离迁移；混合并发证据来自H2，尚未在部署MySQL/HTTP下重复验证。前端及真实第三方接入未改。

## 2026-10-24 短时二维码与会员确认签课

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **549项通过**（组织服务302项，新增二维码签课8项；新MVC3项），member状态4通过/1既有禁用跳过，均无失败/错误。Spotless通过；server及依赖打包成功（跳过测试）。真实隔离MySQL/Flyway **45项检查通过**、65张gym表，全部随机测试库清理；没有迁移业务库。源码映射 **62/135**，余73项。日志：`/tmp/uniapp-lesson-qr-final-tests.log`、`/tmp/uniapp-lesson-qr-migrations.log`、`/tmp/uniapp-lesson-qr-package.log`。

新增真实Mapper/事务测试覆盖短时签名、未配置、TTL窗口裁剪、等于过期边界、租户签名、本人/他人预约、错绑定与预约版本、缺签到不补造、当前签发负责关系撤销、原键重试和新键过期拒绝。人工与扫码混合并发、两次扫码并发均只产生一条课消、只扣一次并保留首次来源。通过二维码使用列失败约束验证课消、预占/余额、预约状态、幂等结果与使用记录一起回滚。MVC验证严格字段、键、ID、长度、错误信封及访问日志正文禁用注解。

原后台人工确认改为共享服务后全部gym回归通过，新增同预约签到关联校验；原卡/课包及不限次数余额行为保留。首轮新增测试用Instant.parse读取省略秒的合法偏移时间失败两项，改为OffsetDateTime解析后通过，业务时间字段格式未改变。MySQL验证覆盖迁移元数据/精度/唯一索引和历史接管，不代表已在生产MySQL验证Java并发。真实账号、扫码页面、设备联调及签名密钥部署尚未执行。

## 2026-10-24 会员月度签到汇总

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **556项通过**（组织服务307项，新增5项月度统计业务；新MVC2项），member状态4通过/1既有禁用跳过，均无失败/错误。Spotless及server依赖打包通过，打包命令跳过测试。源码映射 **63/135**，余72项。日志：`/tmp/uniapp-attendance-final-tests.log`、`/tmp/uniapp-attendance-package.log`；专项7项先通过，日志`/tmp/uniapp-attendance-focused.log`。

真实H2/Mapper/租户拦截器验证多次签到与自然日去重、同分竞争排名、零签到/空月/未来月、失败/删除/孤立档案/跨租户/其他门店/未来事件排除、停用会员的历史参与。边界覆盖上海月初与下月月初、2024闰年二月、1991夏令时午夜。关系删除后无历史不能读取，有本人成功历史仍能读取；未来月也先鉴权。错误账号类型、停用账号/会员、租户不匹配和匿名方法调用拒绝。

MVC验证必填月份/门店、非法月份和ID、仅五个聚合响应字段，客户端memberId不能选择统计主体。最初测试夹具误用旧后台String类型会员ID，修正为Long后专项及完整回归通过，未改变业务ID契约。本批无DDL，不重跑既有V011迁移回归，不迁移业务库；MySQL实际查询性能与部署HTTP、门禁设备联调未验证，前端未修改。

## 2026-10-24 移动工资查询与明确发布

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **566项通过**（组织服务314项，新增7项工资业务；新MVC3项），member状态4通过/1既有禁用跳过，均无失败/错误。Spotless与server依赖打包通过，打包命令跳过测试。真实隔离MySQL/Flyway **48项检查通过**，全部临时数据库清理；65张gym表、V012新增发布列与本人查询索引，旧确认工资保持金额及confirmed状态且未发布。源码映射 **67/135**，余68项。

日志：`/tmp/uniapp-payroll-final-tests.log`、`/tmp/uniapp-payroll-migrations.log`、`/tmp/uniapp-payroll-package.log`；专项日志`/tmp/uniapp-payroll-focused.log`、`/tmp/uniapp-payroll-mvc.log`。初次新增回滚夹具引用未声明JdbcTemplate导致编译失败，改为使用既有DataSource创建实例后通过；最终回归包含旧SELF测试明确发布及新增离职后旧入口拒绝断言。

通过原薪资配置、真实销售/退款流水和工资计算生成双员工批次，验证5000.00底薪、130.00提成、100.00加项、50.00扣项与5180.00实发，明细负扣项之和一致。本人入口即使有整店权限也只返回本人，未确认/未发布、他人、软删除明细/批次及其他租户不可读。管理筛选和权限先于分页，草稿可审核，SELF不能查看全店；功能权限撤销、账号停用、未入职/离职和匿名调用拒绝。

旧后台混合SELF/门店权限测试证明草稿不通过SELF泄露，但授权整店审核仍可用。发布必须显式确认、有效账号和门店权限；时间和操作者只保存首次值，重复确认/发布不解冻或重算。H2真实事务用约束故障注入验证发布失败回滚两列，两个线程并发重试得到相同首次结果。MVC覆盖四个移动路由与辅助发布路径、月份/门店/分页校验、客户端无法选择本人身份、金额字符串和受控字段。Java并发证据来自H2，MySQL验证为迁移/旧库接管，不替代真实部署HTTP、工资打款或大数据量性能验证；业务库与前端未修改。

## 2026-10-24 活动发布、公开读取与本人报名

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **578项通过**（组织服务323项，新增9项活动业务；新MVC3项），member状态4通过/1既有禁用跳过，无失败/错误。Spotless、server依赖打包通过，打包命令跳过测试。真实隔离MySQL/Flyway **50项检查通过**，全部临时数据库清理；V013可空微秒取消截止列，65张gym表，历史已发布不限名额活动保持不变。源码映射 **73/135**，余62项。

日志：`/tmp/uniapp-activities-focused.log`、`/tmp/uniapp-activities-final-tests.log`、`/tmp/uniapp-activities-migrations.log`、`/tmp/uniapp-activities-package.log`。先验证新旧活动专项，再完整回归；补充工作用途图片不可挪为公开活动封面的断言后再次完整通过。

实际H2/Mapper/事务测试覆盖创建和发布流程、当前门店/功能权限、同键恢复与内容冲突、合法时段、公开分页前过滤草稿/取消/非营业门店、跨租户、跨店与不限名额。本人报名重复键/新键同记录、取消释放名额、旧键不重新报名、新键重报沿用原ID且更新时间；后台报名和会员报名竞争最后名额仅一方成功，两次会员同键并发只产生一条记录。

取消截止等值拒绝、旧后台管理取消仍按开始前、报名截止等值拒绝、已有报名后取消规则不可修改。会员默认店与当前会员关系决定首次跨店报名，默认变更不转移原店，关系撤销阻止新报名但可取消本人记录。停业门店与删除活动保留本人报名历史，活动已删除时详情为空；错误身份和匿名私有入口拒绝。用幂等响应约束故障验证报名、名额和键一起回滚。

封面通过既有受控URL读取真实字节；取消最后引用后不可读，其他门店和有工作用途绑定的图片不得挪用，既有引用在新增受限用途绑定后也立即不可下载。MVC覆盖全部六路由、月份无关的状态/分页/门店参数、带时区时间、幂等头、无body报名、容量、额外状态及伪造会员字段。Java并发为H2证据，MySQL验证仅迁移与旧库接管；真实部署HTTP、海量带封面列表性能及活动消息推送未验证。业务库和前端未修改。

## 2026-10-25 匿名反馈、门店回复与图片隔离

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym **592项通过**（组织服务332项，新增反馈业务9项；反馈MVC3项、图片2项），member状态4通过/1既有禁用跳过，无失败/错误。Spotless及server依赖打包成功，打包跳过测试。真实隔离MySQL/Flyway **54项检查通过**并清理全部临时库；V014后67张gym表，检查默认不同意卡摘要、附件唯一、两项权限定义及旧库接管不创建反馈数据。源码映射 **77/135**，余58项。

日志：/tmp/uniapp-feedback-final-tests.log、/tmp/uniapp-feedback-migrations.log、/tmp/uniapp-feedback-package.log。真实H2/Mapper/事务覆盖本人隔离、门店分页、功能及整店权限、SELF拒绝、租户/身份拒绝、附件同人同店同用途及一图一次绑定、默认不附卡信息、显式同意快照且后续卡变化不改写、回复一次与重放时撤权拒绝。两线程同键提交只一条，竞争不同回复不覆盖，竞争复用同一图片只一方成功；幂等结果约束故障导致反馈、附件、请求键一起回滚，重试成功。停业后旧键和历史可读，新提交拒绝。

图片测试写入带测试身份文本的PNG元数据，重新编码后元数据消失、像素保留；覆盖JPEG、伪装/损坏/超限图片。专用下载验证通用名称、no-store、nosniff和403/404，旧通用文件读取拒绝member_类别；MVC严格字段、必填键、非法评分/分页/ID、正文日志禁用注解均通过。初轮两项测试断言/Mock重设错误修正后全量通过（通用文件的正确拒绝码为FILE_STORE_NO_PERMISSION；已有异常桩重设用doThrow）。

Java并发证据来自H2，MySQL仅证明迁移与旧库接管；真实部署HTTP、生产图片并发容量、用户主动写入画面的身份遮盖及外部通知未验证/未实现。未迁移业务库或修改前端。

## 2026-10-25 活动报名跨进程摘要兼容

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

活动专项11项通过后，完整gym **594项通过**（组织服务334项），member4通过/1既有禁用跳过；Spotless与server依赖打包通过，打包跳过测试。两项新增用例将真实gym_mobile_request行分别改为activityId/memberId两种排列的旧SHA256摘要，取消报名后调用真实服务，均重放首次结果且不占名额、不修改历史摘要与响应。新请求断言固定摘要，活动变更、不同会员的逆序摘要及非法键仍拒绝；其他接口的请求编码保持不变。原活动并发名额及失败回滚测试同时通过。

证据是穷举该两字段历史编码的H2真实事务测试，不代表已执行生产重启、多节点滚动部署或旧版本回滚测试。无DDL，不重复既有V014迁移验证。日志：/tmp/uniapp-idempotency-focused.log、/tmp/uniapp-idempotency-final-tests.log、/tmp/uniapp-idempotency-package.log。源码覆盖仍77/135；前端和业务数据库未修改。

## 2026-10-25 器械场景码、公开图文与视频教学

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

器械专项9项通过后，完整gym **603项通过**（组织服务341项，新增器械业务7项；新MVC2项），member4通过/1既有禁用跳过；Spotless及server依赖打包通过，打包跳过测试。隔离MySQL/Flyway **57检查通过**，全部临时库清理；V015新增nullable scene及租户唯一索引，历史器械保持NULL、原状态和型号不变，表数仍67。源码映射 **79/135**，余56项。

实际H2/Mapper/权限测试验证明确发布之前不可读取、匿名发布后解析、32位场景约束、图文/视频排序和受控字段、不同租户拒绝、错误身份/SELF/错误门店/功能权限拒绝。教程、器械、门店停用及删除影响公开引用；撤销旧码后重发不同码，旧码失效，同文件另一有效公开引用仍可下载。私有类别、工作用途绑定、跨店文件不能从教程泄露；损坏或缺失存储内容拒绝。并发发布得到同一码，数据库失败约束证明失败不留scene。MVC验证两条目标及两条辅助路由、严格body字段、畸形scene、无body发布；非正ID由实际服务校验测试证明。

初轮三个测试断言问题已修正：将公开器械名称中的编码误判为内部字段泄露；匿名功能权限拦截实际抛AuthorizationDeniedException；无方法校验代理的独立MVC测试不能证明路径参数@Positive，改由实际服务负数/零ID拒绝断言验证。修正未放宽生产校验。视频内容测试使用MP4魔数测试字节验证受控读取，并非可播放视频，不能推断编码器兼容或手机拖动播放成功。既有公开媒体范围响应测试随完整回归执行。

日志：/tmp/uniapp-equipment-focused.log、/tmp/uniapp-equipment-final-tests.log、/tmp/uniapp-equipment-migrations.log、/tmp/uniapp-equipment-package.log。Java并发为H2证据，MySQL检查为迁移和旧库接管；未执行真实微信码生成、手机扫码、部署HTTP、视频播放或高并发/大视频内存压测，前端与业务数据库未修改。

## 2026-10-25 移动销售报表、退款跨期与归属份额

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

销售报表专项9项通过后，完整gym **612项通过**（组织服务348项，新增销售业务7项；MVC2项），member4通过/1既有禁用跳过，无失败/错误。Spotless与server依赖打包通过，打包跳过测试。隔离MySQL/Flyway **60检查通过**，全部临时库清理；V016两条索引顺序匹配、旧流水12.34金额和NULL归属保持不变，表数仍67。源码映射 **80/135**，余55项。

实际H2/Mapper/租户/权限测试：通过原Commerce服务及测试验款器接收40.00+59.99两笔付款和20.00退款，报表为99.99实收、20.00退款、79.99净额，与旧经营报表相同，重复凭据不增加金额；未付款订单不计入。另用明确账本夹具验证多人归属合计190.00实收、30.00退款、160.00净额，两个订单去重、三行份额、员工/商品筛选、分页与超大页码不改变summary。

跨期100付款在前日、30退款在当日，当前区间净额-30、付款订单数0、均价0.00，paidAt保留原付款时间；日期左闭右开、上海1991夏令时和366自然日边界均验证。多商品名快照不放大金额；订单与事件门店不符时隐藏编号/名称但保留账本。停用门店历史可查，软删流水、跨租户和无权限范围不计入；SELF不能获取门店汇总，错误身份/停用后台账号拒绝。非法事件类型/商品类型由既有数据库约束拒绝，首轮测试误以为可插入这些非法夹具，改为验证约束抛错后全量通过，未修改生产约束。

MVC验证日期/门店/员工/商品/分页绑定与不合法输入，金额字符串、完整summary及无会员字段。日志：/tmp/uniapp-sales-report-focused.log、/tmp/uniapp-sales-report-final-tests.log、/tmp/uniapp-sales-report-migrations.log、/tmp/uniapp-sales-report-package.log。H2证明应用SQL及业务口径；MySQL只证明迁移与历史数据保留，未执行生产EXPLAIN/大数据量压测、真实渠道交易或部署HTTP联调。业务数据库和前端未修改。

## 2026-10-25 员工与门店业绩、SELF范围和零业绩平均

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

专项9项通过后完整gym **621项通过**（组织服务355项，新增业绩7项；MVC2项），member4通过/1既有禁用跳过；Spotless和server依赖打包成功，打包跳过测试。源码映射 **81/135**，余54项；无DDL，不重跑既有V016迁移检查。

真实H2/Mapper/权限测试覆盖多店归属200.00总净额、180.00员工归属额、三名员工（含零业绩）、两店、每人60.00/每店100.00；退款后同额员工排名1/1/3，未归属行无排名不占人数。课消记录每条sessionUnits=3的夹具仍按记录条数计数，确认/软删/时间边界/跨租户分别过滤。门店分组、超大页码、分页summary不变，实际收99.99退20.00与105报表一致。

SELF仅本人80.00及2条课消、禁止store分组，指定本人单店仅40.00；错误门店、任职删除、离职、无SELF、错误账号类型和租户拒绝。混合权限默认只整店范围、范围外本人门店显式下钻不扩大权限。历史员工/负退款保留、未来任职不作为零业绩分母；没有员工的纯未归属收入均值为0。MVC验证必填日期/groupBy、非法筛选和页大小、Money字符串、rank可空及伪造employeeId不能选择主体；最终清理为每参数仅一个值的夹具后全量通过。

日志：/tmp/uniapp-performance-focused.log、/tmp/uniapp-performance-final-tests.log、/tmp/uniapp-performance-package.log。这些测试证明当前数据口径，不证明可重建全部历史在职人数；缺少完整人事变更历史的限制保留。未执行生产MySQL聚合计划/规模压测或部署HTTP联调，前端和业务数据库未修改。

## 2026-10-25 会员汇总、跨店去重及临期边界

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am spotless:apply spotless:check package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

专项9项通过后，完整gym **630项通过**（组织服务362项，新增会员汇总7项；MVC2项），member4通过/1既有禁用跳过。Spotless和server依赖打包通过，打包跳过测试；源码映射 **82/135**，余53项。没有DDL，不重跑既有V016迁移；仍67表，上一批隔离MySQL60检查的证据不代表本次生产SQL性能验证。

真实H2/Mapper/权限测试通过原会员、开卡与私教发放服务建立跨店会员、多张卡/包，验证全域3人、分店各2人、私教/临期持有人各1人；会员关系与权益必须在同一授权店相交，关联表和权益租户过滤不可绕过。精确到微秒验证恰好现在/30天/超过30天，永久权益、未开始、停用、删除、耗尽与预占不重复扣减。初次注册日上海零点左闭右开、未来注册/关系排除、当前潜客计入、当前失效档案排除；改变date只改变新增人数，停用门店保留、删除门店排除。

功能权限、SELF不能读取整店、显式跨店拒绝、会员令牌/租户不匹配/停用后台账号拒绝；无数据、日期上下界和非法门店参数覆盖。MVC验证日期与门店绑定、无效日期/缺参/零门店拒绝、整数与updatedAt序列化、传入memberId不能选择主体。响应不含个人档案字段。完整回归后仅补充OpenAPI说明，最终格式化及打包覆盖该注解改动。

日志：/tmp/uniapp-member-summary-focused.log、/tmp/uniapp-member-summary-final-tests.log、/tmp/uniapp-member-summary-package.log。未做历史快照还原、生产MySQL聚合计划/规模压测或部署HTTP联调；未改前端或业务数据库。

## 2026-10-25 移动首页概览、逐项授权及当前待办计数

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

先完成10项专项，补充实际签到/签课业务链后完整gym **641项通过**（组织服务371项，新增概览9项；MVC2项）；member4通过/1既有禁用跳过。Spotless和server依赖打包通过，打包跳过测试。源码映射 **83/135**，余52项，无新DDL、表或迁移。

真实H2/Mapper/权限测试：两店当日净额630/月累计750，self仅90/210；整店成功签到4/昨日1、确认课消4/昨日1/月6，self仅负责会员签到2和本人授课1/月3。跨店成员3人、分店各2人，self当前负责1人；未授权对象序列化为null，不能伪装成0或返回其他人金额。混合权限默认只整店，显式选择其他本人任职店才self，停用店SELF拒绝、整店历史保留。注册与关系按当前状态过滤，撤销负责关系后历史记录也不继续暴露。

审批通过真实模板和发起服务创建12条两级审批：首审批人12条、下一审批人0，首人处理一条后分别为11和1，变更账号绑定后撤回该待办；范围外不计，不受列表10条上限影响，选择历史日期不更改当前待办数。真实课程服务预约、签到、签课及重复确认后，概览与工作台明细一致为1，跨租户负责关系撤回访问。其他夹具验证月初昨日跨月不混入月累计、微秒边界、未来事件、软删/失败记录、租户与功能权限、无数据/错误身份/日期/门店参数。

首轮新增签到夹具漏设非空booking_id，数据库约束拒绝，补齐唯一预约引用后通过，未放宽生产约束。日志：/tmp/uniapp-dashboard-focused.log（首轮失败）、/tmp/uniapp-dashboard-focused-2.log（10项通过）、/tmp/uniapp-dashboard-final-tests.log、/tmp/uniapp-dashboard-package.log。业务统计证据来自H2实际SQL及服务，不代表已做生产MySQL聚合压测、完整历史快照或部署HTTP联调；前端和业务库未修改。

## 2026-10-25 移动审批、并发版本与真实业务回滚

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

15项专项通过后完整gym **656项通过**（组织服务383项，其中本批移动审批12项；MVC3项）；member4通过/1既有禁用跳过，Spotless及server依赖打包通过，打包跳过测试。真实隔离MySQL/Flyway **62项检查通过**，所有临时库清理，V017为旧审批增加version1并保留pending/current_step2/原因；仍67表。源码映射 **87/135**，余48项。

实际H2/Mapper/权限/事务测试：mine/pending/processed在分页前隔离，后续waiting步骤不成为待办，本人处理但后续未结束的审批可在processed且status=pending查询。申请人历史、当前有效指定审批人和整店管理者详情权限，账号重新绑定即撤回参与者访问，租户隔离；受控响应不含手机号、卡号、附件或完整请求/结果。可用模板只published且全部当前审批账号具备该店权限，SELF仅当前负责店price_change选项。

真实两步审批移动处理version1→2，旧后台处理→3；移动旧键返回首次pending/version2，重绑审批账号后旧键拒绝。拒绝空原因、错误步骤、过期版本及未知业务指令拒绝。最终审批实际发卡一次；黑名单拦截导致状态、任务时间和version全部回滚，同键解除阻断后可成功。两个线程同键返回同一快照、相反决策仅一项生效；旧后台拒绝与移动批准竞争共享锁/版本且仅一方成功。MVC覆盖四路由、分页/模板参数、必填版本/幂等头、禁止附加业务指令和金额字符串。

首轮新增测试缺少GymBusinessPageReqVO import导致编译失败，补齐后专项与全量通过；未修改业务约束绕过测试。日志：/tmp/uniapp-approvals-focused.log（编译失败）、/tmp/uniapp-approvals-focused-2.log、/tmp/uniapp-approvals-final-tests.log、/tmp/uniapp-approvals-migrations.log、/tmp/uniapp-approvals-package.log。尚未验证生产滚动部署/查询规模、部署HTTP和真实第三方；业务库及前端未修改。

## 2026-10-25 会员卡/私教申请与冻结条款发放

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

12项专项通过后新增商品编辑/审批并发测试，完整gym **669项通过**（组织服务394项，本批新增11项；MVC2项），member4通过/1既有禁用跳过；Spotless及server依赖打包通过，打包跳过测试。隔离MySQL/Flyway **64项检查通过**，全部临时库清理，V018仅定义新权限且不授予角色，仍67表；源码映射 **88/135**，余47项。

真实服务/SQL验证：提交仅pending、无权益和合同；最后审批产生一张实际会员卡或两课时私教包及对应内部unsigned合同，财务事件始终0。新卡模板版本不替换原选中模板；私教版本或课时变化阻断批准，旧申请仍version1/pending，新申请才按三课时发放。商品编辑与批准并发时只能按原两课时发放或批准失败，不能混用新条款。私教无固定到期日，教练/营业店/会员状态重查，当前条件恢复后失败决策键可重试。

合同文件读取失败发生在课包创建后，验证课包、合同和审批版本全部回滚；恢复后同键只发放一次。并发同申请键只创建一条审批，规范化空白原因重放，改内容冲突，撤销门店范围后旧键拒绝。create与issue两个功能权限缺一拒绝、SELF拒绝、会员门店/商品门店/租户和账号类型校验；旧审批course-products来源拒绝混合卡模板或改价指令。MVC拒绝自填amount/status及非法产品类型、空原因和缺失幂等头。

首轮测试缺少课程请求类型引用而编译失败；补齐引用并改用已有带测试文件内容的合同模板夹具后专项及全量通过，未修改业务约束。日志：/tmp/uniapp-card-issue-focused.log、/tmp/uniapp-card-issue-focused-2.log（同一编译问题尚未写入修复）、/tmp/uniapp-card-issue-focused-3.log、/tmp/uniapp-card-issue-final-tests.log、/tmp/uniapp-card-issue-migrations.log、/tmp/uniapp-card-issue-package.log。合同文件为本地测试内容，不证明真实签署或可供实际签约的完整PDF；未做外部支付、生产多节点/规模或部署HTTP联调。前端及业务库未改。

## 2026-10-25 私教专属改价申请与公开售价隔离

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

11项专项通过；增加旧程序快照兼容保护后，最终完整gym **680项通过**（组织服务403项，新增9项；新增MVC2项），member4通过/1既有禁用跳过。Spotless及server依赖打包通过，打包跳过测试。源码映射 **89/135**，余46项。本步无DDL，不重复隔离迁移；V018仍为最新迁移、67表，先前64项迁移证据不算本步重跑。

真实服务/Mapper/事务测试：指定会员和未限定会员申请，审批批准/拒绝均不修改公开价格或商品版本，无课包/合同/财务事件；响应和持久化快照区分quote范围，未保存旧公开改价必须的sourceId。商品编辑后原申请不能批准且步骤/版本回滚，新申请反映新版本；当前会员停用导致决策失败，恢复后同键重试成功。商品编辑与批准双线程竞争保持审批快照和公开售价稳定。

SELF只允许本人的商品及所负责会员，撤销负责关系后拒绝旧键；功能权限、后台身份、跨店、跨租户与总部账号下真实会员门店关系均验证。并发同键只有一个申请，金额150与150.00及原因首尾空白规范化重放，改金额冲突；错误模板/同价申请失败后可复用键，旧后台混合卡模板指令拒绝。MVC覆盖六字段绑定、Money及审批投影、非法金额/空原因、未知amount/status和缺失幂等头。

日志：/tmp/uniapp-course-price-focused.log、/tmp/uniapp-course-price-final-tests.log（首次全量）、/tmp/uniapp-course-price-final-tests-2.log（最终含兼容保护）、/tmp/uniapp-course-price-package.log。旧二进制的拒绝行为由已检查的旧分支必需参数推断，本步验证了持久化快照缺少该字段，未执行旧新版本混部署；上线前统一升级审批实例。未实现117–118报价或订单结算，未验证生产HTTP/规模/真实第三方。业务库及前端未修改。

## 2026-10-25 私教报价生成、固定期限和会员读取

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

17项专项通过，完整gym **697项通过**（组织服务417项，本批14项；MVC3项），member4通过/1既有禁用跳过；Spotless与server依赖打包通过，打包跳过测试。真实隔离MySQL/Flyway **73项检查通过**，全部临时库清理；V019后68表，下一版本V020。源码映射 **91/135**，余44项。

真实H2/Mapper/身份/权限/事务验证：整店原价和批准优惠价真实持久化，金额不接受客户端传入；旧公开改价不能替代专属审批，pending/rejected/错会员/错商品/错店拒绝。SELF必须使用本人已批准申请，不能按原价绕过审批或借用别人申请；撤销当前负责关系/门店权限以及功能权限后缓存不能绕过授权。

同审批不同键双线程生成一份报价，同键并发原价报价也只一份；同键换范围冲突。已生成优惠报价在不同键下保持同ID/金额/原截止时间，到期前1微秒仍active，恰好到期expired；同键仍重放首次响应。商品改变前未发出的优惠报价拒绝，已发报价expired且冻结值不变，商品编辑/报价创建竞争不会用新条款生成旧优惠。门店停用导致首次生成失败，恢复后原键可成功，失败事务未留下报价。

会员读取以令牌真实绑定为准，拒绝后台令牌、其他指定会员、跨租户、已解除门店关系和停用会员；未限定会员的报价仍要求同店关系。营业门店/教练状态/任职日期按查询时重查，安全投影不含内部审批和签发人。MVC验证四字段创建、三个未知价格/期限/状态字段拒绝、必传幂等头和会员读取，查询中的memberId不能冒充身份。

迁移额外检查审批/订单绑定唯一索引、微秒精度、权限唯一且无角色授权；实际MySQL允许多个NULL审批原价报价、拒绝同租户重复审批、允许不同租户相同审批编号，旧库接管不伪造报价。日志：/tmp/uniapp-quotes-focused.log、/tmp/uniapp-quotes-final-tests.log、/tmp/uniapp-quotes-migrations.log、/tmp/uniapp-quotes-package.log。used订单状态仅预留字段和读取映射，本步没有订单消费流程或其测试，不据此宣称结算可用。未验证前端分享页面存在/打开、真实支付、生产HTTP及规模；业务库与前端未改。

## 2026-10-25 会员订单准备、报价消费与未支付取消

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终完整gym **716项通过**（组织服务433项，本批16项；新增MVC3项），member4通过/1既有禁用跳过，Spotless及server依赖打包通过，打包跳过测试。真实隔离MySQL/Flyway **77项检查通过**，全部临时库清理；V020后仍68表，下一版本V021。源码映射 **95/135**，余40项；路由覆盖不证明付款与新会员自动履约已实现。

真实H2/Mapper/事务/身份验证：卡使用门店销售价和当前版本快照，改配置后原幂等键保留旧价、新键用现价；私教现价及批准0元报价写真实订单，0元不产生财务事件或权益。实际订单/明细/策略与used_order_id同事务；同报价不同键并发只一张订单，同键并发也一张，换商品冲突。报价到期、目标不是本人、商品变化拒绝，下单后used、取消后不释放；订单期限最多30分钟且不超过报价截止。

本人列表SQL先做身份/租户/单商品线上订单及支付状态过滤再分页；解除门店关系仍保留本人历史，其他会员、跨租户、后台令牌不得读取或取消。旧后台实际已付款并发卡的订单可读真实权益ID且expiresAt=null。通过原可信收款/退款服务产生部分收款、全额收款、部分退款、全额退款，验证每阶段详情与SQL筛选一致；未以直接修改金额冒充这条测试的业务过程。测试渠道为受控mock，不证明真实支付接入。

取消保存第一次原因与时间，新键/同键不覆盖，创建旧键仍重放首次快照；同键改取消原因冲突。取消与原收款双线程竞争时要么取消且无资金账本，要么已收款且取消失败。支付发生时刻恰好达到新截止时刻时原收款服务拒绝。销售配置缺字段/坏JSON/重复商品/未启用及销售开始前、结束时拒绝且不残留订单；修复后同失败键可重试。黑名单、数量1、会员门店关系及card不能携带quoteId都校验。MVC验证四路由、Money字符串、分页筛选、必传幂等头及禁止注入会员/金额/支付状态。

首轮完整筛选未包含App前缀命名的新增MVC类，已改名GymMobileOrdersControllerTest纳入最终回归；随后补真实收退款筛选与旧配置/销售窗口测试。日志：/tmp/uniapp-orders-focused.log、/tmp/uniapp-orders-final-tests.log、/tmp/uniapp-orders-final-tests-2.log、/tmp/uniapp-orders-final-tests-3.log、/tmp/uniapp-orders-final-tests-4.log（最终）、/tmp/uniapp-orders-migrations.log、/tmp/uniapp-orders-package-final.log。迁移验证旧partially_paid订单保留100.00应收/20.00实收和状态，四个新增字段NULL，不伪造历史信息。

新会员订单自动履约/重试未接入，目前真实待履约；本步不能证明新会员下单后已发卡/课包。23支付会话、25–26退款申请、真实渠道的在途支付/取消与晚到通知补偿仍待实现；前端、业务库未改，未做生产HTTP/分享页面/规模联调。


## 2026-10-25 履约任务账号类型隔离

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='GymOrganizationServiceTest#fulfillmentPrincipal*' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-module-gym -am spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

4项专项和完整gym **720项通过**（组织服务437项），member4通过、1项既有禁用跳过。验证实际领取/失败持久化、次数及退避时间、SecurityContext恢复；数字ID相同的会员任务不查询有效员工账号、不检查其后台权限、不产生权益或额外财务事件。未知类型同样拒绝。后台重试失败后类型、账号、状态及次数预算保持；类型2仍可由有权后台人员承接并增加8次预算。会员调用原后台入队在写任务前拒绝，后台调用保存显式类型2。

接口源码映射仍95/135，余40项；本批没有新增路由，不证明会员自动履约已交付。新会员订单仍未入队。日志：/tmp/uniapp-principal-focused.log、/tmp/uniapp-principal-final-tests.log、/tmp/uniapp-principal-migrations.log、/tmp/uniapp-principal-package.log。前端、业务库未修改，未做真实支付、部署HTTP或规模联调。

Spotless及server依赖打包通过（打包跳过测试）。真实隔离MySQL/Flyway **82项检查通过**，V021后仍68表；全部临时库清理。新增检查覆盖类型默认2、显式会员与同号后台独立、显式NULL拒绝、旧processing任务的微秒租约/claimToken/失败原因/次数及原账号保留、不为旧订单伪造任务。首次在沙箱内连接本机MySQL被限制，获授权后运行隔离测试通过；未迁移业务库。


## 2026-10-25 会员购买实际履约、合同及失败重试

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='GymOrganizationServiceTest#memberFulfillment*+fulfillmentPrincipal*' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增15项实际H2/Mapper/权限/事务业务测试，最终完整gym **735项通过**（组织服务452项），member4通过、1项既有禁用跳过；Spotless及server依赖打包通过，打包跳过测试。接口源码映射仍95/135，没有新增路由计数。

真实会员零元卡、批准零元报价课包实际发放，实收和财务事件仍0。正价私教通过受控核验器mock分两次实际记账40+160，未足额不履约，重复凭据不重复入账；private收入总额200。商品编辑前后及双线程竞争均保留原两课时、60分钟和200元成交价。旧60分钟排期可预约，新30分钟排期写入拒绝且会员查询bookable=false；不扣实际课时，只预占1。报价绑定后若入队失败，订单/明细/策略/used_order_id/幂等结果一起回滚，恢复后原键可重试。

缺必需合同模板、文件读取失败及最终任务succeeded写入失败，都验证权益、合同、文件元数据、客户阶段和策略完成信息回滚；修复后自动调度或有权人工重试成功，会员主体50/类型1保持。内部合同unsigned/unconfigured，事件真实会员类型1，后台uploader_id为空；购买合同不能引用其他来源权益。外部blob不受数据库事务保护，测试不宣称存储服务原子回滚。购买钩子要求已有事务，未支付不能直接发放。

验证会员账号停用、门店失效、解除会员关系、教练任职未来、商品停用及导入数据中的教练不匹配拒绝（正常商品修改API禁止换教练），恢复资格后重试；后台令牌、其他绑定会员、跨租户和快照错误租户均拒绝。需审批订单无自动任务，已领取任务转人工审批后不能继续发放。取消、真实受控退款与旧token完成不产生权益；并发履约只有一张卡/一份合同，已履约重放无副作用。course_sale不能进入旧卡退款逻辑。

真实隔离MySQL/Flyway **94项检查通过**，V022后仍68表；全部临时库清理。验证旧课包余额/单价不变且未知时长NULL，旧合同事件保持账号、微秒时间及类型2。另建真实V021库，模拟15种旧订单，只有4个符合条件的会员订单补任务；零元/足额pending，未付/部分付waiting_payment，原后台processing任务的账号/租约/token/次数不变；不改原订单/策略或伪造权益/付款，重复迁移不重复任务。首次升级夹具遗漏框架表的baseline 0导致Flyway拒绝，补齐与正式init一致的baseline后最终通过；未修改迁移逻辑来绕过验证。

首轮旧隔离测试仍期待“会员类型暂不支持”的文案；会员分支接入后改为验证真实会员身份不成立的错误，仍断言不查询或借用管理员。日志：/tmp/uniapp-member-fulfillment-compile.log、/tmp/uniapp-member-fulfillment-focused.log、/tmp/uniapp-member-fulfillment-all.log、/tmp/uniapp-member-fulfillment-final.log、/tmp/uniapp-member-fulfillment-final-2.log、/tmp/uniapp-member-fulfillment-final-3.log（最终）、/tmp/uniapp-member-fulfillment-migrations-final.log、/tmp/uniapp-member-fulfillment-package-final.log。没有真实微信/渠道付款、电子签约、部署HTTP、多节点或规模联调证据；前端和业务库未改。23支付会话、25–26退款申请及私教退款权益处理仍待后续。

最后补教练快照一致性检查时，测试尝试通过正常商品修改API换教练，被已有不可变约束拒绝。保留该约束，改为先断言正常API拒绝，再显式模拟历史导入数据不一致并验证履约拒绝；恢复后可重试，未新增正常换教练能力。

## 2026-10-25 退款申请、审核及可信凭据关联

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增20项退款申请业务测试和5项MVC测试。最终gym **760通过、0失败/错误/跳过**（组织服务472项）；member4通过、1项既有禁用跳过。Spotless及server依赖打包通过，打包跳过测试；系统权限测试本批未重跑。MVC验证路由/字段/头和响应，真实方法权限、SQL范围及事务另由组织服务测试验证。

覆盖实付余额计算、拒绝零元/未付/伪造订单金额、本人/租户/账号类型隔离、停用账号和撤销门店权限后旧键拒绝；门店停用或解除会员门店关系仍可申请/读历史。双线程创建只得一份活动申请、批准/驳回只允许一个版本胜出、创建和其他退款竞争不伪造进度。幂等原键重放首次结果，新键复用相同活动申请或在终态后重新申请。

批准不写退款、不撤卡；已领取履约任务暂停且旧token不能落地，直接发放和后台重试也不能绕过。拒绝恢复等待收款/待执行、保留耗尽次数和原人工审批等待。实际可信mock凭据分笔40+59.99更新关联申请及原卡权益，重复凭据不增加流水；其他退款关闭余额不足申请而不计入已退金额，旧凭据不能补领到新申请。部分付款申请冻结40，后付59.99不提高上限；审核前发生的凭据拒绝。私教可申请审核，但旧卡退款路径在查询渠道前拒绝私教。

分别注入暂停任务、驳回恢复、最终申请完成写入的数据库约束失败，验证申请/版本/幂等键/队列或真实退款/财务/权益同事务回滚，移除故障后原键/原凭据可重试。无真实退款渠道或伪造生产支付，测试核验器mock仅证明内部逻辑。

真实隔离MySQL/Flyway **107项检查通过**，69张gym表，临时数据库全部清理。新增13项检查覆盖活动唯一及跨租户独立、终态释放、多份终态历史、微秒时间、拒绝错误/缺失占位及零元申请、默认未审未退、旧退款事实与NULL关联保留、无历史申请回填。旧V021升级夹具按版本移除所有V022及以后迁移后再升级到最新，继续验证V022历史任务恢复；已应用迁移未改。业务库未迁移。

日志：/tmp/uniapp-refund-focused.log、/tmp/uniapp-refund-final-tests.log、/tmp/uniapp-refund-final-tests-2.log（最终，含数据库CHECK约束）、/tmp/uniapp-refund-migrations.log、/tmp/uniapp-refund-package.log。源码覆盖97/135、仍缺38项，不表示真实微信、部署HTTP、多实例或规模联调已通过；前端未改。

## 2026-10-25 工作台设备与柜格分页查询

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增7项真实H2/权限/租户业务测试与3项MVC测试，完整gym **770通过**（组织服务479项），member4通过、1项既有禁用跳过；Spotless与server依赖打包通过，打包跳过测试。系统权限和隔离MySQL迁移本批未重跑，没有修改schema、迁移或角色授权。

验证指定门店、door/locker过滤及configured条件先于分页，total与当前页一致、空页仍保留总数；不返回camera/face_reader、移除及逻辑删除柜格。停用店/设备可查询配置，删除父设备/门店拒绝；非柜机不得查柜格。实际方法权限代理拒绝无功能权限，撤销门店及纯SELF范围同样拒绝；会员类型、停用账号、租户不匹配与跨租户父设备均拒绝。额外storeId不能改变柜格真实归属，-1/201页长和非法参数拒绝。

只输出设备4字段和柜格3字段，ID为字符串；不泄露设备UID、厂商、位置、遥测和占用状态，不把available改成空闲状态，不生成命令。首次隔离夹具试图让两个租户复用同一个门店主键，被现有全局主键约束正确拒绝；改为独立门店202并断言不可读原门店101，未放松生产约束。最终日志/tmp/uniapp-device-final-tests.log，打包/tmp/uniapp-device-package.log；早期7项夹具记录/tmp/uniapp-device-focused.log。源码路由99/135，剩余36项；未做真实设备、部署HTTP或前端联调。

## 2026-10-25 站内通知、权限撤销与会员偏好

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='GymOrganizationServiceTest#notifications*' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增14项真实H2/权限/事务测试和4项MVC测试；最终完整gym **788项通过**（组织服务493项），member4通过、1项既有禁用跳过。Spotless与server依赖打包通过，打包跳过测试。业务场景实际发卡、课包、未签合同和活动报名，不由测试直接插通知冒充业务生产。

验证预约/活动重复执行不重复消息，取消后同一时钟重新参与可生成新事件；源投影并发每接收人只有一份通知。会员与员工独立收件箱，同号管理员不能读会员通知；跨租户、停用账号及错误收件人拒绝，会员档案/账号共同匹配。员工当前业务权限、负责关系、任职日期及授课范围撤销后，历史正文/分页total和已读操作一并拒绝。总部也只能读发给本账号的消息。解除会员门店关系后本人历史仍可读，不给后来初始化的账号迁移历史消息。

未读过滤先于分页，已读自然幂等且并发保留首次时间；偏好多键原子更新、同值保留原时间、并发不同键不覆盖，非法键/重复键/额外身份或授权字段拒绝。注入通知取消写入失败，验证预约、预占、幂等结果及通知事务回滚并可原键重试；偏好某键CHECK失败会撤销同批其他键更新。活动对外businessId指向活动，会员卡/课包以type区分目标。原完整预约/发放/合同事务用例继续通过。

隔离MySQL/Flyway **117项检查通过**，71表，全部临时库清理。新增10项覆盖微秒时间、事件收件人唯一、相同数字账号类型/租户隔离、默认未读、非法账号类型、意愿唯一/受限键/拒绝授权状态、显式退订保存与旧库不生成历史消息或意愿。无业务库操作。

首轮完整回归有1项MVC断言把严格未知字段错误期望为400，实际框架按既有规则返回MOBILE_REQUEST_INVALID=1070023005；只修正断言，字段仍拒绝，业务14项当时已通过。最终日志/tmp/uniapp-notification-final-tests-2.log；专项/tmp/uniapp-notification-focused.log，迁移/tmp/uniapp-notification-migrations.log，打包/tmp/uniapp-notification-package.log。源码映射104/135，剩余31项；没有真实外部推送、门禁事件、部署HTTP、多节点或规模联调证据，前端未改。

## 门店服务私信（2026-10-25，V025）

可复现命令（仓库根目录）：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

最终gym810项通过（组织服务510项），member4通过、1项既有禁用跳过；新增17项业务、5项MVC。Spotless通过、server依赖打包成功（打包跳过测试）。源码路由110/135，剩余25项；已读辅助接口不增加目标总数。

测试覆盖：跨员工同店会员唯一会话、未绑定潜客到核验绑定后的历史读取、实际两类发送人、正文与严格字段；权限/负责关系/账号撤销后旧键重试拒绝、跨会员/租户/会话隔离；停用门店及删除门店关系后的读写区别；倒序游标与分页total；GET无已读副作用；旧游标不回退、并发新消息不误清；同键并发单消息/异键并发多消息；创建、发送、已读数据库失败回滚后原请求重试。MVC另核对七个入口正文日志关闭，方法权限由真实代理业务测试验证。

真实隔离MySQL/Flyway129检查通过，全部临时库清理；新增12项验证73表、微秒时间、租户/会员/门店唯一、未读及游标约束、两类账号审计、正文/发送类型约束、未读索引、两项权限唯一定义及无自动角色授权、无旧库会话消息回填。V001–V024未修改，业务库未迁移。

首轮专项的潜客测试缺少有效平台会员账号夹具，导致先报账号无效；已补有效账号并仍保持身份未绑定，正确验证身份初始化前后差异。首轮全量中17项新业务及5项MVC通过，另有既有课程购买快照测试因夜间第二节课跨日超出查询窗口失败；查询结束日改为第二节实际排期次日，保持60分钟购买快照与短课程不可预约断言，未改业务规则。最终全量重跑成功，不以失败的首轮日志当作通过证据。

最终日志/tmp/uniapp-conversation-final-tests-2.log，初轮/tmp/uniapp-conversation-final-tests.log，专项/tmp/uniapp-conversation-focused.log，迁移/tmp/uniapp-conversation-migrations.log，打包/tmp/uniapp-conversation-package.log。本地验证不证明真实微信、部署HTTP、多节点、规模或外部消息送达；前端未改。

## 邀请推广与首次归属（2026-10-25，V026）

复现命令：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增12项数据库业务测试及3项MVC，完整gym825通过（GymMobileMemberServiceTest35、组织服务510），member4通过、1项既有禁用跳过。Spotless通过，server依赖打包成功（打包跳过测试）；源码映射111/135，剩余24项。

覆盖随机持久化码及原码/路径/期限重放、新键不复用；当前账号/会员/门店/关系撤销后重试拒绝；幂等参数冲突和安全页面配置；首次实际归属、已存在身份不覆盖、老客户非新建且不迁移原店/客户阶段；未知、错店、过期边界、删除及跨租户码拒绝；无效邀请人、原绑定撤销及自邀拒绝；空邀请与旧文本不伪造归属；并发同键一条/异键多条及同一被邀请人并发首次建档唯一；发码及最终身份写入失败时完整回滚，原键重试。MVC验证请求头、门店ID、身份/码/积分字段拒绝、公开响应仅3字段及发码正文日志关闭。

真实隔离MySQL/Flyway141检查通过且临时库全部清理，新增12项包括74表基线之外的微秒时间、可空归属、租户内唯一码、跨租户命名空间、有效期、完整归属及双重自邀约束、老会员标记；单独建真实V025库升级，验证旧自由文本码与原身份信息保留、无历史邀请回填及账号权限不变。V001–V025未修改，业务库未迁移。

日志：/tmp/uniapp-invitation-focused.log（会员专项35通过）、/tmp/uniapp-invitation-final-tests.log（最终全量）、/tmp/uniapp-invitation-migrations.log、/tmp/uniapp-invitation-package.log。未做真实微信分享、小程序页面、部署HTTP、多节点或规模联调；生成链接不证明积分奖励已完成，前端未改。

## 会员本人订阅查询（2026-10-25，V027）

复现命令：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增8项数据库业务测试及3项MVC；最终gym836通过（会员专项43、组织服务510），member4通过、1项既有禁用跳过。Spotless通过、server依赖打包成功（打包跳过测试）。源码映射113/135，剩余22项。

覆盖本人/租户/门店/删除条件先于分页、所有本人渠道记录、ID倒序及total；他人和跨租户详情、同号后台账号及令牌租户不符拒绝；11种内部状态到固定公开状态的映射；原协议版本/最大精确金额/微秒时间、不依赖当前商品配置；门店关系移除、门店停用/删除后历史保留；每次查询重新验证身份状态；参数边界；多次读取的原表完整快照及请求记录不变，未产生周期/卡/合同/订单/支付。MVC验证分页绑定、非法参数、客户端memberId不能成为查询身份、列表/详情严格8字段及历史版本null。

真实隔离MySQL/Flyway144检查通过，全部临时库清理；本批新增3项检查：版本列可空且无伪造默认值、本人门店索引、旧协议升级后金额/状态/渠道协议号/微秒时间不变且版本保持NULL。V001–V026未修改；表数仍74，无权限变更，业务库未迁移。

日志：/tmp/uniapp-subscription-query-focused.log（专项46通过）、/tmp/uniapp-subscription-query-final-tests.log（全量）、/tmp/uniapp-subscription-query-migrations.log、/tmp/uniapp-subscription-query-package.log。未做真实微信签约/代扣/解约、部署HTTP、多节点或规模联调；测试通过证明内部读取契约，不证明渠道授权有效或状态实时。

## 积分账本与本人读取（2026-10-26，V028）

复现命令：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

新增16项业务及3项MVC；最终gym855通过（会员专项59、组织服务510），member4通过及1项既有禁用跳过。Spotless通过、server依赖打包成功（打包跳过测试）；源码映射115/135，剩余20项。

业务测试覆盖真实空账户读取不建账、入/出/返还/冲回后的每笔余额和累计值、同键相同载荷重放与变更冲突、强制外层事务/输入与数值边界、无余额扣分回滚后原键重试、原流水成员/方向/类型/累计额度限制、原获分已花后的负余额及禁继续超支、当前身份及门店历史边界、SQL本人/租户/类型分页、GET前后账户/流水不变、并发同键/异键入账、并发消费不超支、并发返还不超原额、流水/账户写失败和上层业务失败全部回滚。MVC核对整数精度、三字段余额与五字段流水、分页与类型参数及无法选择他人身份。

真实隔离MySQL/Flyway159检查通过且临时库全部清理，本批新增15项覆盖账户零默认、租户会员唯一及隔离、余额恒等式/非负累计/安全整数上限、业务键唯一/大小写/租户、零值及符号/原流水/说明约束、微秒时间、分页索引、旧库不补造积分。V001–V027未修改，当前76表；业务库未迁移、无角色变更。

日志：/tmp/uniapp-point-ledger-focused.log（会员专项59通过）、/tmp/uniapp-point-ledger-final-tests.log（全量）、/tmp/uniapp-point-ledger-migrations.log、/tmp/uniapp-point-ledger-package.log。内部append仅由测试在隔离事务调用，当前无生产奖励/兑换调用者；不能据测试声称实际消费或邀请已发积分。前端未改，未做部署HTTP、多节点、规模或兑换闭环联调。


## 积分兑换商品（2026-10-26，V029）

复现命令沿用上节全量Gym/Member、Spotless、隔离MySQL、server package和源码覆盖命令。新增10项会员/配置业务、1项后台功能权限、3项MVC；最终gym869通过（会员专项69、组织服务511），member4通过及1项既有禁用跳过。Spotless及server打包成功（打包跳过测试），覆盖116/135、缺19；git diff --check通过。

业务覆盖真实配置字段及查询无账本/发卡/库存副作用、完整替换与null不限库存、旧版本冲突和同值不变、固定门店/模板、跨租户及会员不能管理、参数/模板适用性、上架/库存/模板过滤后分页总数、当前身份/门店关系、模板删除仍可下架、三并发仅一次更新、写失败时库存/成本/版本整体回滚。后台三个操作均验证独立权限拒绝；MVC校验五字段、整数精度、ID字符串、分页参数、完整配置和输入拒绝。

隔离MySQL/Flyway170检查通过且临时库全部清理，本批新增11项：不造默认商品、禁用/无限库存/版本默认、零成本/超安全整数/负库存/无效版本/空名拒绝、售罄及最大成本保留、查询索引、独立权限定义、旧库不造商品；既有角色快照检查仍通过。V001–V028不变，业务库未迁移。

日志：/tmp/uniapp-point-products-focused-final.log（69通过）、/tmp/uniapp-point-products-final-tests.log、/tmp/uniapp-point-products-migrations.log、/tmp/uniapp-point-products-package.log。最初两轮发现测试夹具重复使用全局模板族ID/门店ID，已修正为独立真实主键，保留业务断言后重跑。尚未证明62–63兑换、真实发卡和失败返还闭环；无部署HTTP、多节点及规模性能验证。


## 积分兑换与持久化恢复（2026-10-26，V030）

复现命令沿用前文完整Gym/Member测试、Spotless、隔离MySQL、server package及源码覆盖命令。新增23项业务和3项MVC；最终gym895通过（组织服务534、会员专项69），member4通过及1项既有禁用跳过，Spotless通过；server打包成功（打包跳过测试）。覆盖118/135，仍缺17，git diff --check通过。

真实服务/数据库测试覆盖实际次卡/周期卡发放、无现金订单收入、被冻结的成本/模板、同键最新状态及通用幂等记录清理、processing持久化与GET无副作用、余额/库存不足准备全回滚、当前及其他有效会员/租户隔离、缺真实扣分不得发卡、发卡故障全额返还、通知/合同链路故障回滚、返还自身故障保持processing和恢复、并发同键一次扣分、不同键不超卖/超扣、资格撤销及停用账号后台返还、定时恢复与停用租户跳过、上下文恢复、积分上限返还空间及最大库存、真实未签合同会员审计、成功状态不能重复补偿。MVC验证四字段、ID和幂等头、字段校验、processing/failed及无内部数据。

真实隔离MySQL/Flyway189检查通过，临时库全部清理；新增19项覆盖空兑换表、库存/数值预留默认与边界、业务请求键唯一/大小写/租户、终态约束、一次扣分不能资助多个兑换、微秒时间及恢复索引，以及实际V029升级时既有余额/商品/版本/角色保持。V001–V029未修改。初次全量测试暴露H2多上下文重复ALTER初始化，已改为最终建表结构并重跑；隔离脚本表数与升级夹具同步后通过。

日志：/tmp/uniapp-point-redemptions-focused.log（初期19业务通过）、/tmp/uniapp-point-redemptions-final-tests.log、/tmp/uniapp-point-redemptions-final-migrations.log、/tmp/uniapp-point-redemptions-package.log。测试账本和商品只存在隔离数据库，未导入业务库；未运行部署HTTP、真实外部存储、多节点、规模压力或真实电子签约联调。


## 风控告警、一次处置与证据授权（2026-10-26，V031）

复现本批最终验证：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py
git diff --check
```

完整gym **913项通过**、0失败/错误/跳过（组织服务548、会员专项69、文件服务19）；member所选4通过、1项既有禁用跳过。新增14项实际业务测试、3项MVC和1项通用文件隐私测试；Spotless通过。server及依赖打包成功，打包阶段跳过测试。覆盖Contract135/mapped120/missing15；源码映射不替代部署HTTP验收。

业务验证涵盖真实登记设备归属、规范化来源去重和冲突、相同来源并发唯一、调用者事务回滚、租户隔离、状态/整店范围过滤后分页、历史设备删除、真实操作人/微秒时间、无卡冻结或签到副作用、权限撤销后的重试拒绝、SELF/会员身份拒绝、相同键并发返回同一结论、不同键竞争只成功一次、版本冲突和注入失败后审计/幂等整体回滚。证据验证真实关联、范围撤销、元数据/字节魔数冲突、文件删除、私有下载响应及通用文件入口不可绕过。MVC验证9字段投影、参数/版本/请求键绑定及无效请求拒绝。

真实隔离MySQL/Flyway **204项检查通过**，临时随机库全部清理，累计80张gym表。新增15项覆盖空表、open/version1、来源唯一/区分大小写/租户、类型和状态版本约束、处置唯一/结果/非空说明/旧版本/操作人约束、微秒审计、索引、权限定义及升级不生成历史风险记录。已应用V001–V030保持不变，业务数据库未迁移。早期专项失败为Mockito重复when触发已配置异常，改为doThrow重新配置后完整回归通过，未放松生产鉴权或异常处理。

最终日志：`/tmp/uniapp-risk-final-tests.log`（10:39:33）、`/tmp/uniapp-risk-migrations.log`、`/tmp/uniapp-risk-package.log`（10:39:52）。未验证真实设备/存储、部署HTTP、多实例或规模性能；当前无生产风险事件或证据写入调用者，未验证/实现自动冻结和外部推送。前端未改。


## 设备命令结果与完成时间（2026-10-26，V032）

沿用上一节完整Gym/Member、Spotless、隔离MySQL、server package和源码覆盖命令。新增8项真实数据库业务测试、2项MVC；最终gym923通过（组织服务556、会员专项69），0失败/错误/跳过；member4通过及1项既有禁用跳过。Spotless通过，server依赖打包成功（打包阶段跳过测试）。源码覆盖Contract135/mapped121/missing14，git diff --check通过。

测试覆盖既有后台未发送失败与确切微秒完成时间、只读重复访问、本人在权限撤销和设备删除后的历史、其他管理者当前功能权限加整店范围/SELF拒绝、权限撤销、停用账号/会员令牌/跨租户/删除隔离。真实已完成记录、模拟/缺少完成时间的成功、queued/sent/unknown均独立断言；7字段响应不泄漏请求键、哈希、原因、操作者或厂商原错误；DB约束拒绝完成早于请求、未终态完成和模拟成功完成。MVC验证长ID字符串、完整字段、路径类型错误及业务不存在。

隔离MySQL/Flyway212检查通过，新增8项验证可空微秒列、默认无完成时间、三种无效完成状态、合法微秒结束及真实旧版本升级保留NULL和原状态/模拟标志/操作者/时间/请求。仍80张gym表，随机库全部清理；V001–V031未改，业务库未迁移。早期专项测试遗漏显式Import新服务，并将服务层非法ID误期望为注解异常，修正夹具注册和断言后完整回归通过，未放松生产参数校验。

最终日志：`/tmp/uniapp-command-result-final-tests.log`（10:52:25）、`/tmp/uniapp-command-result-migrations.log`、`/tmp/uniapp-command-result-package.log`。没有真实厂商命令/回执、部署HTTP、多节点或规模证据；成功状态的隔离夹具不代表真实硬件验证，查询不发命令。前端未改。


## 会员团购券归属预览（2026-10-26，V033）

复现沿用前文完整Gym/Member测试、Spotless、随机隔离MySQL、server package及源码覆盖命令。新增15项实际H2业务/加密/平台适配测试及3项MVC；完整gym941通过（会员专项84、组织服务556）、0失败/错误/跳过，member4通过及1项既有禁用跳过。Spotless和server依赖打包成功，打包阶段跳过测试。源码覆盖Contract135/mapped122/missing13；git diff --check通过。

平台准备及手机号查券使用Mock Client返回独立夹具；现有Client/Transport真实组装HTTP的既有回归也随完整套件通过，未对真实平台发起查询或核销。覆盖完整手机号归属、掩码必须精确本人券码、两个platform枚举反转、分页偏移和300条上限、未知或异常响应、原始券码/手机号不透出。网络外部无DB事务、网络前后设置版本/映射/账号重查、保存故障无预览残留、外层事务禁止进入；初次用例误用了34字节测试密钥，修正为32字节后通过，生产密钥长度限制保留。

门店/会员关系与状态、有效购卡黑名单、verification-settings开关/自动签到/上海时段、发布模板及门店范围、单张普通券类型/数量/时段/真实毫秒到期、5分钟有效期和无消费/发卡副作用均有断言。AES-GCM测试验证前导零、随机nonce、跨租户/门店/用户AAD拒绝、格式错误及不同租户HMAC隔离。MVC验证5字段、长ID字符串、非法参数、未知身份字段拒绝及归属错误不泄露券码。Redis限流注解已配置，部署后真实限流未验证。

真实隔离MySQL/Flyway222检查通过，新增10项覆盖空预览表、合法平台/版本、严格到期、可兑换必须非空卡及模板快照、不可兑换必须说明、微秒身份记录及3索引，旧库升级不造历史预览。新增一表至81表、下一V034；V001–V032保持不变，所有临时库清理，业务库未迁移。

最终日志：`/tmp/uniapp-coupon-preview-final-tests.log`（11:24:57）、`/tmp/uniapp-coupon-preview-migrations.log`、`/tmp/uniapp-coupon-preview-package.log`（11:25:29）。预览不证明33核销/34结果已实现，也不证明真实渠道、实券、部署HTTP、轮换密钥、多实例或规模场景已通过。前端未改。

## 会员单券消费与查单适配准备（2026-10-26，无迁移）

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py --show-missing
git diff --check
```

新增49项GymMemberCouponConsumptionTest及2项配置绑定测试，完整gym992通过、0失败/错误/跳过（组织服务556、会员专项84保持）；member所选4通过，1项既有禁用跳过。Spotless及server依赖打包通过，打包阶段跳过测试。源码覆盖Contract135/mapped122/missing13不变；本批为未接通业务编排的内部适配，不能计为33–34路由实现。

新增测试使用真实GymMeituanRetailClient/Transport和MockRestServiceServer拦截HTTP，不访问外网。验证签名、表单、固定requestId、count=1、显式商家审计身份与不发送会员资料；确认凭据不含明文券码/手机号/令牌。18种缺失/不匹配/多条/关联券/标识无效响应保持未知；超时只发一次，不隐式重试/查单。公共参数拒绝与业务/JSON/空响应分开判定。7种授权变化阻止旧scope消费及查询；移动消费独立开关、商家配置/密钥缺失、平台变更开关、租户/店无回退及行业错误在HTTP前拒绝。

只读getConsumed在关闭移动消费/平台mutation后仍可查原授权，但其观察类型不含本次requestId或消费流水，不能充当Confirmation。不相关券/商品/类型及不完整订单拒绝。真实Spring事务代理结合H2事务验证两个网络方法在外层事务中拒绝执行。配置绑定验证预览开启不启动消费及精确租户/门店商家账号映射。

日志：`/tmp/uniapp-coupon-consumption-focused.log`（专项49通过）、`/tmp/uniapp-coupon-consumption-final-tests.log`（12:24:38）、`/tmp/uniapp-coupon-consumption-package.log`（12:26:08）。本批无SQL、表或权限变更，未重复运行MySQL迁移检查，上一批222检查仍为V033历史证据。没有真实平台/实券、部署HTTP、多节点或热更新验证；没有会员核销API、占券/持久化幂等、发卡或恢复证据。前端及业务库未改。

## 会员团购持久化核销、真实发卡与恢复（2026-10-26，V034）

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py --show-missing
git diff --check
```

新增24项真实数据库业务/并发/恢复测试和4项MVC；专项28通过，最终完整gym1020通过、0失败/错误/跳过（组织服务580、会员专项84），member4通过及1项既有禁用跳过。Spotless通过，server及依赖打包成功（打包阶段跳过测试）；源码覆盖Contract135/mapped124/missing11。路由覆盖只证明方法/路径匹配，实际业务证据如下。

测试使用实际事务、Mapper、CardService、ContractService和通知链路；平台Client模拟独立响应，实际HTTP组装仍由上批49项适配及既有Client测试覆盖。核销成功验证真实卡/核销记录、固定起始时间及无现金订单/积分；网络处于SQL事务外，consume调用前已提交sending。未知结果保留占用、GET无副作用、同预览换键/同键缓存清理后幂等、不同预览同券拒绝、重复或不同键并发单次派发、并发confirmed恢复只发一卡均验证。跨账号/租户、过期预览、配置变化、最终归属/手机号改变、停用会员在派发/履约阶段均有断言。

注入卡或旧核销记录写失败后，卡、内部合同、会员通知与终态全部回滚，保持已核销待发卡；恢复不重消费且在预览过期/消费开关关闭后仍可完成。真实内部未签合同记录会员actor类型1和用户ID，不冒用员工；周期卡按冻结期限发放，不受后续模板归档/期限修改影响。缺少/不匹配消费凭据拒绝发卡；已提交sending后模拟进程中断，只查单不重发。迟到确认可完成未知态，后续观察/失败调用不能覆盖成功。模拟确认持久化两次均失败时保留未知，观察不冒充成功。

查询租约验证每60秒最多6次、已验记录无法转成功、关闭消费开关仍可查、密钥变化导致解密失败也按上限停止；当前没有提供强制成功或手动无限重试入口。恢复器验证prepared崩溃恢复、停用租户跳过、上下文恢复；外层SQL事务禁止调用编排和网络。MVC验证previewId/幂等头、正数输入、四字段投影、长ID字符串及未知身份/状态字段注入拒绝。

真实隔离MySQL/Flyway242检查通过，新增20检查覆盖空核销表、微秒初始状态、请求/预览/跨店券占用唯一及大小写/租户隔离、查询上限、有效身份/派发时间/占用约束、NULL凭据/流水不能确认、已确认不能伪装未消费失败、成功必须关联卡和旧核销、平台流水不可重复、失败释放仅供新预览、恢复/查询/关联索引、旧库不造核销。新增一表至82表，随机库全部清理；V001–V033未改，未迁移业务库或调整角色。

日志：`/tmp/uniapp-coupon-workflow-focused.log`（28通过）、`/tmp/uniapp-coupon-workflow-final-tests.log`（12:40:05）、`/tmp/uniapp-coupon-workflow-migrations.log`（242检查）、`/tmp/uniapp-coupon-workflow-package.log`（12:41:00）。初期修正了访问日志注解应标方法、测试预览方法名/订单表名/JsonUtils导入，未放宽业务校验；修正后专项及全量通过。没有真实商家授权/实券、部署HTTP、外部文件存储、多实例或压力验证，未知结果的受信外部关联仍未接入。前端未改。

## 2026-10-26 认证器二次确认与一次性令牌（V035，总表126）

最终命令及证据：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
python3 script/gym/verify-uniapp-api-coverage.py --show-missing
mvn -B -ntp -pl yudao-server -am package -DskipTests
git diff --check
```

完整gym1057项通过（组织服务605项），member选定4项通过、1项既有禁用跳过；新增37项=25项真实H2/权限/事务业务测试+9项TOTP测试（含6个RFC向量）+3项MVC。最终日志`/tmp/uniapp-action-final-tests.log`在13:13:02成功；Spotless通过。MySQL/Flyway257项检查通过，所有随机临时库清理，未迁移业务库；日志`/tmp/uniapp-action-migrations.log`。打包日志`/tmp/uniapp-action-package.log`，打包跳过测试，不能代替上述测试。路由核对125/135，缺10项，不声明全量上线。

覆盖：RFC 6238 SHA1向量按6位取模、前导0、时间窗口、规范Base32、相邻码碰撞防重放；有效后台身份、权限/SELF/租户隔离、配置关闭/缺失；持久化失败锁定、期限不延长、到期重置、逻辑删除不重置、时钟回退；跨门店并发签发只成功一次；绑定设备/柜格/操作、令牌过期、密钥/权限撤销、模拟及非queued指令拒绝；一次消费/并发消费、外层事务回滚、拒绝过时queued快照；最小响应、禁缓存、敏感请求/异常不回显。

快照回归在H2 REPEATABLE_READ下由数据库返回SQLState40001并回滚，随后新事务明确拒绝sent指令；该用例验证失败关闭及令牌未消费，不冒充MySQL多节点并发联调。初次并发测试的线程内Mockito重复配置已改为主线程预配置，最终全量通过。隔离MySQL验证的是迁移、唯一性/检查约束与旧库保留；未进行真实认证器手机、生产数据库、设备厂商、部署HTTP或多节点压力验证。旧V001–V034和原始uniapp.md的内容/暂存状态保持不变，dashboard工作区干净。

## 2026-10-26 审查修复：私教销售名称

新增2个参数化H2用例，使用会员私教订单及受信收款测试凭据产生财务事件，分别覆盖新course与历史private明细；课程更名后仍返回成交名称快照，无筛选/按course筛选均正确，金额与订单数不变。修复前2项中course用例失败（预期私教产品、实际null），证据`/tmp/uniapp-review-sales-red.log`。修复后运行：

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='GymOrganizationServiceTest#mobileSalesReport*' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
```

销售报表9项通过、Spotless通过，证据`/tmp/uniapp-review-sales-green.log`（13:43:11 BUILD SUCCESS）；`git diff --check`通过。本批无DDL/配置/协议变更，不重复运行迁移；真实渠道及前端不在本次修复范围。

## 2026-10-26 审查修复：风控处置摘要稳定与历史重放

新增3项真实H2回归：新请求固定id→request顺序；手工构造两种历史外层字段顺序对应的已持久化摘要，同一请求均返回原处置结果，保留原摘要/响应及唯一审计；修改告警ID、结论、备注或expectedVersion均冲突。修复前3项中2项报1070023004，证据`/tmp/uniapp-review-risk-red.log`；这验证了不同历史编码的重放，不声明真实多JVM部署联调。

```bash
mvn -B -ntp -pl yudao-module-gym -am spotless:apply spotless:check test \
  -Dtest='Gym*Test,MemberAuth*Test,MemberUser*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
python3 script/gym/verify-uniapp-api-coverage.py --show-missing
git diff --check
```

最终gym1062项通过（组织服务610），含本轮两处修复新增5项；member4项通过、1项既有禁用跳过。Spotless通过，测试日志`/tmp/uniapp-review-fixes-final-tests.log`（13:46:17 BUILD SUCCESS）。server打包成功，日志`/tmp/uniapp-review-fixes-package.log`（13:46:35），打包跳过测试。路由覆盖仍125/135，暂缓项未计完成。没有DDL或旧摘要回填，不重复跑迁移；业务库、前端及用户原始文档的内容/暂存状态保持不变。没有真实渠道、多节点或部署HTTP验收。

## 2026-10-26 main 同步 jdk25 上游及合入移动接口分支

先合入upstream/master-jdk25（70ad21ad6c，保留JDK25和Spring Boot4适配），处理275项内容/格式冲突；再合入codex/uniapp-135-apis至f27ce5ad7f。第二次只有CHANGELOG追加记录冲突，双方记录原文按时间保留。未运行部署或业务数据库升级。

```bash
mvn -B -ntp -pl yudao-server -am spotless:apply spotless:check test \
  -DargLine='-javaagent:/Users/hh/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
mvn -B -ntp -pl yudao-server -am package -DskipTests
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
python3 script/gym/verify-uniapp-api-coverage.py --show-missing
git diff --check
```

默认启用的server依赖链全部运行：2018项，1999通过、0失败/错误、19跳过；其中infra跳过10、system跳过8、member跳过1，gym1062项全过。未启用的其他业务模块没有编译/测试，不用此结果宣称全部可选模块已验收。测试日志`/tmp/server-main-merged-tests.log`，14:02:40 BUILD SUCCESS。打包成功，日志`/tmp/server-main-merged-package.log`，14:03:49；打包跳过测试。隔离MySQL/Flyway257项检查通过、全部随机库清理，日志`/tmp/server-main-merged-migrations.log`。

原功能分支的gym源码、SQL迁移、脚本及uniapp契约与合并结果逐字节一致；所有V001–V035保留。新旧上游依赖与本地权限菜单扩展一起验证，环境连接参数保留。源码目标映射125/135，剩余10项仍为已授权暂缓；真实渠道联调及上线验收不在这次合并验证内。工作区原始uniapp.md通过独立stash保护，最终按原内容与原暂存状态恢复，不提交或推送该文件。

## 2026-10-26 JSS 合并主线接续复测（尚未通过）

对 `e548c85428704aa30dc3e50ab71667fa3e1a5ec2` 重跑 30 模块 `jss-mall verify`。当前受限执行环境的 Java 用户目录与开发用户不同，原 Maven 缓存已只读复制到工作区忽略目录 `.runtime/maven-repository`，未调整生产依赖版本。测试 JVM 的临时目录也须位于工作区：

```powershell
& D:\Project\JSS\.tools\apache-maven-3.9.11\bin\mvn.cmd -o -B -ntp `
  '-Dmaven.repo.local=D:/Project/JSS/JSS-server/.runtime/maven-repository' `
  '-DargLine=-Djava.io.tmpdir=D:/Project/JSS/JSS-server/.runtime/java-temp' `
  -Pjss-mall -pl yudao-server -am verify `
  '-Dspotless.skip=true' '-Djss.build.final-name=yudao-server-main-merge'
```

最新运行 `16:00:17 +08:00` 结束：基础模块及 infra 通过，infra 为 245 项、0 失败/错误、10 项既有跳过；其中代码生成 17 项通过。system 缺少以下缓存依赖，后续模块全部未执行：`spring-boot-starter-mail:4.1.2`、微信 MP/Miniapp starter `4.8.6-20261010.094603`、`captcha-spring-boot-starter:2.0.0`、`jcl-over-slf4j:2.0.20`。在线构建另已确认下载受当前环境网络权限限制。日志 `.runtime/main-merge-verify-resumed.log`；这不是完整构建成功。

统计 Mapper 复测同样止于上述依赖解析，日志 `.runtime/main-merge-statistics-resumed.log`，随机 schema 已清理。隔离 Redis 启动本机 WinGet 包中的程序返回 `WinError 5`，本轮真实 HTTP/H5 没有开始，不能沿用合并前的通过报告。只读检查确认 HTTP/统计随机 schema 均无残留；开发库、默认运行 jar 和原服务未替换。

兄弟前端已准备第 3 个真实浏览器场景，覆盖用户 CRUD、状态、密码、角色与写权限撤销后的接口拒绝；仅通过静态类型检查和用例发现，尚无真实运行结果。环境恢复后先完成完整构建，再运行 `script/jss/verify_workspace_http.py --browser` 和 `script/jss/verify_statistics_mappers.py`。仍须核对 JUnit 10 项、Playwright 3 项、日志脱敏及清理结果，然后才更新整合版本。

## 2026-10-26 合并复测：Gym 亚微秒边界修复

本轮已获准下载缺失依赖及运行本机隔离服务，上一轮的依赖/程序权限限制已解除。基础单测使用内嵌 Redis 16379，因此完整 `verify` 期间必须停止同端口的已归属隔离 Redis；真实 HTTP 验收在构建结束后再串行启动。清理端口冲突后，完整构建的 system 514 项中 0 失败/错误、8 项既有跳过，Gym 1062 项中出现 3 项时间精度失败，后续模块尚未执行，日志 `.runtime/main-merge-verify-online.log`（17:27:36 结束）。

为原有三个用例设置确定的时间：工资使用 `.123456700Z`，售卖窗口保留 700ns 的微秒余数，会员卡精确开始边界使用 `datetime(6)` 可完整表示的时刻。修复前专项运行 3 项，其中工资幂等时间和截止瞬间拒绝两项稳定失败，会员卡查询通过；`.runtime/main-merge-time-precision-red.log`。修复后同命令 3 项全部通过，无失败、错误或跳过，17:34:14 结束；`.runtime/main-merge-time-precision-green.log`。

```powershell
& D:\Project\JSS\.tools\apache-maven-3.9.11\bin\mvn.cmd -B -ntp `
  '-Dmaven.repo.local=D:/Project/JSS/JSS-server/.runtime/maven-repository' `
  '-DargLine=-Djava.io.tmpdir=D:/Project/JSS/JSS-server/.runtime/java-temp' `
  -Pjss-mall -pl yudao-module-gym -am test `
  '-Dtest=GymOrganizationServiceTest#payrollFailureKeepsPreviousItemsAndConfirmationFreezesSnapshot+mobileWorkMemberSummaryCountsUnlimitedDurationWithoutInventingUsesOrExpiry+mobileOrderMalformedLegacyOfferAndExactSaleWindowFailClosedWithoutLeavingRequests' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

售卖窗口用未经截断的时刻判断 `[start,end)`，订单持久化时间仍为微秒；工资确认写入前归一化至微秒，首次响应和数据库重读一致。没有修改历史迁移、放宽业务断言或跳过失败用例。专项通过不替代后续完整构建与真实 MySQL/Redis/HTTP/H5 验收。

三个修改的 Java 文件单独通过 Spotless apply/check（17:37:51），日志 `.runtime/main-merge-time-format.log`。插件的 `spotlessFiles` 参数使用正则表达式：`.*GymPayrollService\.java,.*GymMobileOrdersService\.java,.*GymOrganizationServiceTest\.java`；根目录 `-N` 执行前须将当前 `yudao-dependencies/pom.xml` 安装到同一个工作区 Maven 缓存，以解析内部 BOM。

## 2026-10-26 合并复测：支付过期订单夹具精度

Gym 修复后的完整组合继续通过全部 1062 项 Gym 测试，随后在支付模块的 346 项中失败 5 项（35 项既有跳过）。五项均为过期时间的内存纳秒值与 H2 `timestamp` 读回微秒值不同，未发现订单关闭/支付状态的断言失败；原始整对象断言仍全部保留。失败日志保存在 `.runtime/main-merge-verify-before-pay-fixture.log`。

仅将 `PayOrderServiceTest` 五处“当前时间减一分钟”夹具设为整秒（`withNano(0)`），与 MySQL 支付表的 `DATETIME` 精度一致；不修改生产时间工具、表结构或支付业务。`-Pjss-mall -pl yudao-module-pay -am test -Dtest=PayOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true` 的 67 项全部通过、无失败/错误/跳过，17:48:53 结束；日志 `.runtime/main-merge-pay-test.log`。使用同一个工作区 Maven 缓存和测试临时目录，完整构建仍须在此改动后重跑。

该测试文件的 Spotless apply/check 通过（17:49:44），日志 `.runtime/main-merge-pay-format.log`，差异检查通过。

## 2026-10-26 完整组合通过与联合初始化顺序

在 `5bccd71770` 上重新执行上述完整 `jss-mall verify`，30 模块全部成功，17:58:51 结束、耗时 8:01；日志 `.runtime/main-merge-verify-final.log`。219 份本轮 Surefire XML 与 Maven 模块汇总一致：2583 项，0 失败/错误，154 项既有或条件跳过，即 2429 项执行通过。跳过的 Mapper/HTTP 集成用例及第三方测试不计作真实验收。独立产物 `yudao-server-main-merge.jar` 的 SHA-256 为 `b4fe34ea4308a84eae56be2c9d7a398fbbbee480dbca7aaa9f9a464f4c7c81c2`；没有替换开发运行 jar。

随后真实联合工具遇到 Gym V005 的 `CREATE TABLE IF NOT EXISTS member_user` 与 JSS 初始化已有业务表守卫冲突。修复只调整 `verify_workspace_http.py`：框架基座 → JSS 显式 init → Gym baseline/migrate → Quartz。JSS 先建立完整会员表，Gym 的条件建表保留该定义；`database.py` 的拒绝自动接管业务表规则与所有历史迁移均未修改。新随机 schema 的实际历史已核对 JSS 10 条、Gym 36 条（各含 baseline 0），完整应用及浏览器验收继续执行中，尚未登记 HTTP/H5 通过。

## 2026-10-26 真实用户管理复测：角色分配目标租户校验

首次联合运行的 9 项 HTTP 全部通过；H5 新增跨租户检查复现：持有合法工作台写权限时，为另一租户的用户 `33001` 分配角色返回业务码 0，预期为用户不存在。该请求来自真实浏览器登录取得的 `jssw_` 凭证，未拦截 API；证据为 `.runtime/main-merge-http-before-role-boundary.log` 及对应随机运行目录中的脱敏浏览器日志。

`PermissionController.assignUserRole` 现先通过当前租户作用域查询目标用户及全部角色 ID，缺失/外租户目标分别返回 `USER_NOT_EXISTS`、`ROLE_NOT_EXISTS`，再调用原权限服务。保留原权限码检查，已有停用角色允许保留或移除，其本身仍不贡献有效权限；合法原生后台分配路径保留。

复测 9 项 HTTP 仍全部通过，H5 中普通会员/员工、动态角色菜单、完整用户 CRUD 及写权限拒绝、租户停用/过期恢复 4 项通过；用户管理场景明确验证两个外租户目标被拒绝，合法角色分配仍成功。剩余菜单生命周期场景发现异步导航竞争，由前端另行修复，因此 JUnit 汇总为 10 项中 1 项浏览器启动失败，**不能登记联合验收通过**。日志保存在 `.runtime/main-merge-http-role-fixed-navigation-pending.log`。控制器格式检查通过；修改后的完整组合仍需重新执行。

## 2026-10-26 本轮最终联合验收通过

代码基线 `50950b35be4dc16e7cd29849374c59bdf1ce797c` 已重新执行完整 30 模块 `jss-mall verify`，18:34:17 成功结束，耗时 8:46。Maven 各模块汇总为 2583 项，2429 通过、154 项既有或条件跳过、0 失败/错误；`.runtime/main-merge-verify-final.log`。跳过项继续不计真实验收。

串行执行 `python script/jss/verify_workspace_http.py --browser` 成功：本轮新生成的 JUnit 报告 10 项、0 失败/错误/跳过（9 项真实 HTTP + 1 项浏览器启动），Playwright 6 项全部通过（约 1.1 分钟）。场景包括：

- 从真实审核页面绑定及撤销，目标会员获得访问并在撤销后旧凭证被拒绝；审核员复用隔离库已有的已审核关系，保留一对一绑定限制。
- 普通会员/员工登录、真实查询、撤权、换号及缓存篡改深链拒绝。
- 同一凭证下角色菜单增减和空工作台。
- 用户创建/详情/编辑/启停/密码重置/角色分配/删除；编辑不提交密码或状态，真实日期数组查询，外租户用户及角色目标拒绝，以及六项撤权后写接口拒绝。
- 按钮、父菜单、入口停用后退出受保护页面并保留会员，恢复后重新访问。
- 另一租户停用/过期后保留会员、关闭工作台，恢复后取得新凭证。

服务由随机隔离 schema 完整启动，无 API 拦截或向浏览器注入登录 Token。工具校验报告为本轮生成，服务器日志未出现测试密码；日志 `.runtime/main-merge-http-final.log`，实际浏览器日志与服务日志由 `.runtime/jss-http/last-run.json` 定位。真实详情及审核撤销截图已在兄弟前端目视核对。前端另通过 32 项单测、7 项夹具、类型检查及 H5 构建；导航竞争修复的单测先复现失败再通过。

随后 `python script/jss/verify_statistics_mappers.py` 的 3 项真实 MySQL 8.4.9 生产 Mapper 测试通过，无失败/错误/跳过；`.runtime/main-merge-statistics-final.log`。只读确认本轮 HTTP/统计随机 schema 均无残留、已归属的专用 Redis 进程停止。没有升级开发库或替换运行 jar。

独立产物 `yudao-server-main-merge.jar` SHA-256 为 `47dbaf23e55afc0ce841f5c182c5c67c2925146ed3fce7f8957cc48749f72fcd`；包内角色归属、工资确认和售卖窗口三个修复 class 与当前编译产物逐字节一致。兄弟前端的整合版本更新到上述代码基线；整体仍为进行中，商城完整交易、统计 HTTP/任务、其他管理模块与跨端发布尚未完成。

## 2026-10-26 商城真实 HTTP：购物车、结算与未支付订单

新增入口 `python script/jss/verify_workspace_http.py --suite mall`，默认 `--suite workspace` 继续运行原工作台场景。商城入口使用相同的随机 MySQL schema、完整迁移、已归属 Redis 及真实 Spring Boot 服务，商品和交易前置数据通过管理 API 创建。当前商城入口不支持 `--browser`，H5 后续单独接入。

先后实际复现并修复下架商品加入购物车、订单商品列表未嵌套校验，以及他人/缺失地址返回通用 500。补入五项请求校验单测、两项购物车拒绝写入单测和一项地址错误单测；交易模块回归 71 项（55 执行通过、16 既有条件跳过，0 失败/错误），`.runtime/mall-trade-unit.log`。复现命令：

```powershell
mvn -Pjss-mall -pl yudao-module-mall/yudao-module-trade -am test '-Dtest=cn.iocoder.yudao.module.trade.**' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
python script/jss/verify_workspace_http.py --suite mall
```

最终真实 HTTP 四项全部通过，无失败/错误/跳过：公开商品按租户和上下架过滤；购物车合并数量、库存上限和所有者写入限制；结算、篡改客户端金额仍按 2598 分创建实际支付订单、扣库存及取消恢复；非正数量、空项、缺少数量、他人地址和他人购物车项拒绝且不新增订单或改变库存。GET 结算和 POST 下单均验证，合法仅 cartId 的方式保留。

日志 `.runtime/mall-http-verification.log`，随机目录由 `.runtime/jss-mall-http/last-run.json` 定位；本轮报告时效、服务日志不含测试密码、随机 schema 已删除及专用 Redis 已停止均通过。没有操作开发库/运行 jar。本次没有渠道收款、物流或售后证据；71 项中的条件跳过不计真实验收，也未将局部回归称为新的完整 30 模块 verify。

格式验证按本次修改/新增的 Java 文件定向执行 Spotless check。额外运行的无筛选根目录 `spotless:check` 发现 8117 个既有文件的 CRLF/LF 差异（`.runtime/mall-format-check.log`），未批量重写无关文件；这项全仓格式检查不登记为通过。

## 2026-10-26 商城真实 H5 联合验收通过

现已支持 `python script/jss/verify_workspace_http.py --suite mall --browser`。工具使用兄弟前端 `playwright.mall.config.ts`，启动独立 9103 H5，拒绝 48080 开发后端；测试商品由当前 Java 场景通过生产管理 API 创建，图片也来自此本地 H5 服务。`JSS_TEST_BROWSER=1` 才启用浏览器场景，未提供隔离环境时不能直接启动此前端配置。

最终本轮 JUnit 五项全过（四项真实 HTTP + 一项浏览器启动），无失败/错误/跳过；Playwright 一项全程实际操作通过，9.9 秒。UI 登录会员后，按商品列表、详情、加入购物车、改为两件、结算、提交、收银台、详情取消的顺序操作。通过实际查询同时核对金额 2598 分、SKU/SPU 库存 8→6→8、购物车已清空、订单取消状态 40 和未支付状态；页面无运行错误。没有拦截 API 或注入登录 Token。

初轮有两个测试定位问题（重复金额区域、英文确认按钮），均已改为具体容器/按钮类。真实下单还复现订单成功后收银台跳转被点击节流丢弃；前端 `397bd1c` 保留普通点击节流并让业务重定向即时执行，新增单测先失败后通过。最终确认页、已关闭订单截图位于前端 `test-results/mall`，已实际查看；日志 `.runtime/mall-browser-verification.log`，子日志由 `.runtime/jss-mall-http/last-run.json` 定位并已脱敏。

本轮服务日志密码扫描、报告时效、随机 schema 清理、已归属 Redis 停止均通过；新增 Java 定向格式检查通过。前端 33 项单测、7 项夹具、类型检查、验收 TypeScript 检查及 H5 构建另行通过。没有执行渠道付款、发货或售后，本次不代表完整交易闭环，也没有将历史完整 30 模块构建重新归到本次提交。

## 2026-10-26 购物车合并数量溢出回归

已有 3 件时加入 Integer.MAX_VALUE 件，原 int 加法溢出为负数，未触发库存不足异常。新增 `CartServiceImplTest.testAddCart_mergeCannotOverflowToNegativeQuantity` 先复现失败，`.runtime/mall-cart-overflow-before.log`；改用 long 合并并在库存校验后转 int，交易模块专项回归 72 项（56 执行通过、16 既有条件跳过，0 失败/错误），`.runtime/mall-cart-overflow-after.log`。

再次执行 `python script/jss/verify_workspace_http.py --suite mall --browser` 成功，本轮 JUnit 五项全过、无跳过（四项 HTTP + 一项 H5 启动），新增真实极端数量请求被拒绝且原购物项/库存保持不变，正常 UI 购物和取消恢复继续通过；日志 `.runtime/mall-cart-overflow-http.log`。Java 定向 Spotless 和差异检查通过；工具的本轮报告、日志密码扫描与随机库/Redis 清理检查通过。未重复完整 30 模块构建，没有新增第三方支付或物流证据。

## 2026-10-26 钱包真实扣款、履约及售后退款

商城 suite 增为七项实际 HTTP；启用 `--browser` 时预期八项 JUnit（加一项浏览器启动）。新增钱包支付与发货前退款、发货/本人收货/退货退款、余额不足三项，原商品/购物车/非法订单/未支付取消继续运行。配置和测试余额通过实际管理 API 建立，内部钱包扣款/退款走生产 WalletPayClient，并由 PayNotifyService 向随机端口实际发出支付及退款 HTTP 通知；不直接写支付状态、调用服务替代回调或伪造渠道结果。

首轮复现同一会员第二次手工调账业务号冲突；`PayWalletControllerTest` 先失败，再由每个手工请求独立 UUID 修复。支付模块命令 `mvn -Pjss-mall -pl yudao-module-pay -am test '-Dtest=cn.iocoder.yudao.module.pay.**' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'` 通过，353 项中 312 执行通过、41 既有条件跳过，无失败/错误；`.runtime/mall-wallet-pay-unit.log`。没有修改或移除支付/退款流水唯一约束。

最终 `python script/jss/verify_workspace_http.py --suite mall --browser` 八项全过、无失败/错误/跳过；`.runtime/mall-wallet-http.log`。每次支付 1299 分实际扣本人余额且只增一笔支出流水；另一会员余额不变，客户端传入的他人钱包 ID 被覆盖。真实支付通知成功一次后交易状态 10；重复支付或重复通知不重复扣款。两种售后均经实际退款通知完成，状态 50、交易全额退款关闭，余额恢复、流水 -1299/+1299、库存 8→7→8；重复退款和回调不重复入账/回库存。他人支付单读写、伪造未支付通知、他人售后操作、超额退款、未发货提前收货均拒绝；余额不足无扣款，保留未支付并可取消。

回调查询使用真实管理通知分页，要求成功且通知次数为 1；业务状态通过不带 sync 的查询等待实际回调结果。App 售后 DTO 不暴露内部退款编号，测试通过管理详情 API 查询该编号。物流公司/单号是业务发货/退货状态的隔离数据，没有向外部物流平台发请求。原 H5 未支付购物场景继续通过，H5 钱包付款/售后页面仍待扩展；第三方支付与充值渠道不计作已验证。Java 定向格式、差异、报告时效、日志密码扫描和随机库/专用 Redis 清理通过，没有改动开发服务或宣称完整 30 模块重跑。

## 2026-10-26 钱包版本完整构建与两项真实 H5

代码 `1fdf0a0f82b0a517b02ffef1c953e941b8224854` 已重新执行以下完整组合，20:36:58 成功结束，30 模块全过。Maven 汇总与本轮 221 份 Surefire XML 一致：2593 项，2439 执行通过、154 既有/条件跳过、0 失败/错误。日志 `.runtime/mall-full-verify.log`，结构化复核 `.runtime/mall-full-verification-summary.json`。全仓格式差异仍存在，构建跳过 Spotless，变更 Java 定向检查另有通过证据。

```powershell
mvn -B -ntp -Pjss-mall -pl yudao-server -am verify '-Dspotless.skip=true' '-Djss.build.final-name=yudao-server-mall-verified' '-Dmaven.repo.local=D:/Project/JSS/JSS-server/.runtime/maven-repository' '-DargLine=-Djava.io.tmpdir=D:/Project/JSS/JSS-server/.runtime/java-temp'
python script/jss/verify_workspace_http.py --suite mall --browser
```

独立 jar SHA-256：`6eff59ecf7ae2fe9d791118b1b759bca82962ce365ecb18388e2ba9b1273ce13`。嵌套 pay/trade/system jar 内手工调账、购物车数量和角色目标校验 class 与各自当前 target/classes 逐字节一致，没有替换开发运行 jar。完整构建的内嵌 Redis 与真实验收的认证 Redis 均用 16379，已按先完整构建、再串行 HTTP 的顺序运行。

最终商城联合日志 `.runtime/mall-wallet-browser-final.log`：JUnit 八项全过、无失败/错误/跳过；Playwright 两项全过（23.9 秒）。新 H5 从真实会员登录、直接购买、余额支付进入售后，取消申请后重新申请，商家通过实际管理 API 同意/退款，再从会员 UI 查看退款成功及钱包。金额 1299 分，余额恢复、两条 -1299/+1299 流水、库存 8→7→8；钱包隐藏/显示和每行实际发生时间也与 API 一致，前端修复了错误的时间取值。四张结果截图已目视核对。会员操作没有 API 替身或 Token 注入；商家支持操作不是管理售后 UI 验收。

随后串行执行 `python script/jss/verify_workspace_http.py --browser` 退出 0，JUnit 十项全过（九项真实 HTTP + 一项浏览器启动），六项真实 H5 全过，38.8 秒；日志 `.runtime/mall-wallet-workspace-final.log`，子日志 `.runtime/jss-http/jss_http_verify_8a0c0ba01003/authenticated-browser.log`。实际绑定审核/撤销、按钮/父菜单/入口撤权、租户停用/过期恢复、换号、角色刷新及完整用户 CRUD/角色外租户目标/写权限拒绝继续通过，覆盖公共导航修复后的管理端回归。

两个随机库 `jss_http_verify_590e1d94a19b`、`jss_http_verify_8a0c0ba01003` 均已只读确认删除，归属记录中的 Redis 进程已停止；工具核对本轮报告及服务日志无测试密码。前端已提交 `f756c32`，整合版本指向上述后端代码且仍进行中。微信/支付宝及充值渠道、物流平台、并发竞争、营销、统计 HTTP/任务、其他管理页面和真机发布继续未完成。

## 2026-10-26 真实并发购物车数量保护

新增 `concurrentCartAddsPreserveEveryAcceptedIncrementAndStockLimit`，用六个线程在起跑屏障后实际发送 HTTP。首次运行八项 HTTP 中一项失败、其余七项通过：已有 1 件，六次并发加 1 均返回同一购物项成功，但最终数量为 2，预期 7；`.runtime/mall-cart-concurrent-before.log`。这不是单元替身复现。

`CartMapper.updateCountIfUnchanged` 按购物项、会员及旧数量条件更新；`CartServiceImpl.addCart` 冲突后当前读、重新校验商品和合并数量再更新。首次并发插入遇到已有唯一约束冲突后也读取实际购物项合并，不直接返回 500；保留原唯一约束与 long 数量校验。当前读避免在 resetCart 的外层重复读事务中持续读取旧快照，未为普通加购新增长事务或锁服务。

交易模块专项命令与上一节一致，最终日志 `.runtime/mall-cart-concurrent-unit-final.log`：74 项，58 执行通过、16 既有条件跳过、0 失败/错误，购物车 12 项全过。首次新增重试单测遇到 Mockito 严格参数桩错误，已分别显式模拟第一次竞争失败及第二次成功；该次失败不计通过。变更 Java 的三组定向 `spotless:check`、Python 编译和差异检查通过。

`python script/jss/verify_workspace_http.py --suite mall --browser` 最终退出 0，预期改为九项 JUnit（八项真实 HTTP + 一项浏览器启动），全过且无跳过；两项真实 H5 全过，23.1 秒。日志 `.runtime/mall-cart-concurrent-final.log`，子日志 `.runtime/jss-mall-http/jss_http_verify_0c629312f09c/mall-browser.log`。新真实请求核对三阶段：已有项 1 + 六次并发 = 7；余量 1 时六请求恰好一成功、五受控库存拒绝，最终 8；删除后六次首次并发加购全部成功且返回同一行，最终 6。商品库存始终 8，加购不预扣库存。

复现/修复两轮随机库均已只读确认删除，专用 Redis 已停止，报告时效及日志密码扫描通过。最近完整 30 模块 verify 仍属于上一节 `1fdf0a0f82`，本次为交易专项及真实联合回归，未重新替换独立 jar/开发服务。订单抢库存、支付并发及其余完整整合范围继续验证。

## 2026-10-26 订单库存接续：重复 SKU 校验与待运行的真实竞争用例

`TradePriceServiceImplTest.rejectsDuplicateSkuBeforePricingEvenWhenEachLineFitsStock` 先复现：库存 8，同一 SKU 两行各 5 件仍通过价格计算，预期业务拒绝但没有异常；价格校验的 `convertMap` 覆盖了重复 SKU 的数量。两项单测中一项失败，零错误/跳过，日志 `.runtime/mall-duplicate-sku-unit-before.log`。这证明价格校验缺口，不是实际超卖证据。

价格服务现对解析后的 SKU 集合检查重复，返回 `1011000040`（同一商品规格不能重复提交，请合并购买数量）；直接购买、购物车重复项、购物车与直接购买混合项共用该入口，结算和创建均适用。无迁移、无锁服务改动。新增单测覆盖 5+5、2+3、1+Integer.MAX_VALUE，拒绝发生在商品查询及营销计算之前。

专项命令通过，49 项执行通过，零失败/错误/跳过；日志 `.runtime/mall-order-stock-unit-after.log`：

```powershell
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-module-mall/yudao-module-trade -am test '-Dtest=TradePriceServiceImplTest,AppTradeOrderSettlementReqVOTest,*PriceCalculatorTest,*CalculatorTest,CartServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

`JssMallHttpIT` 新增两个待运行场景：两个会员各从购物车购买 5 件、竞争库存 8；只允许一个成功，核对 SKU/SPU 库存 3、6495 分订单与支付单关联、订单/项/支付单/日志各仅增一条、失败购物车保留、他人/重复取消不增库存，以及胜者取消后败者重试和取消。重复 SKU 场景覆盖超库存/库存内直接项、重复购物车项、混合项及 GET 结算，并核对拒绝后库存、购物车和四张交易表数量。并发请求助手与既有加购用例共用屏障；脚本预期为十项 HTTP，带浏览器十一项 JUnit。

**新真实 HTTP/H5 未运行。** `python script/jss/verify_workspace_http.py --suite mall` 在创建随机库前因读取本机 WinGet 安装的 `redis-server.exe` 返回 `PermissionError: [WinError 5]` 退出，日志 `.runtime/mall-order-stock-before.log`；未启动新 Redis/后端、未创建本轮随机库。无环境变量的编译检查中 HTTP 类十一项全部条件跳过，不能计为通过；最终源码另执行全依赖 `test-compile -DskipTests`。四个变更 Java 的三组定向 Spotless 检查通过，Python 编译和差异检查通过。

恢复该已安装程序的读取/运行权限后，仍需先执行 `python script/jss/verify_workspace_http.py --suite mall --browser` 并检查实际报告、清理和截图。最近真实商城证据仍属于 `a0a583c44a`，完整 verify/独立 jar 仍属于 `1fdf0a0f82`；本轮没有新全量构建、没有更新开发库或替换运行 jar，不前移整合验收版本。支付并发及其余完整范围继续保留。

## 2026-10-26 支付并发接续：钱包待支付查询与竞争用例

`WalletPayClientTest` 新增两个待支付查询回归，先出现两项错误（原六项通过）：支付扩展单为 WAITING 时，尚无扣款流水或已有已提交的扣款流水，`getOrder` 都抛出 `PayClientException/IllegalStateException`。复现日志 `.runtime/mall-wallet-concurrent-unit-before.log`。钱包客户端现查询已提交的 PAYMENT 流水：无流水返回 WAITING，有流水返回 SUCCESS，供正常状态同步使用；原 SUCCESS 必须有流水、CLOSED 返回关闭的行为保留。没有新增锁或改变唯一键。

支付模块专项通过，355 项中 314 执行通过、41 项既有/条件跳过，零失败/错误；钱包客户端八项全过，支付订单 67 项、钱包服务九项继续通过。日志 `.runtime/mall-wallet-concurrent-unit-after.log`：

```powershell
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-module-pay -am test '-Dtest=cn.iocoder.yudao.module.pay.**' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

`JssMallHttpIT` 增加两个真实场景（尚未执行）：

- 同一支付单六请求竞争，余额 12990 分足够全部请求，不能以余额耗尽掩盖重复扣款；要求一成功，其余受控拒绝，支出仅 1299、流水仅一笔、累计支出仅增 1299、支付单/交易单成功，成功支付扩展、支付操作日志、真实 HTTP 通知任务与通知日志均只一次，另一会员余额不变；重复提交不改变结果。
- 同一会员两个 1299 分订单竞争仅够一笔的余额：要求恰好一成功、余额零、另一支付单与交易单仍未支付，败者无支付流水/成功扩展/通知/支付操作日志。通过实际管理 API 再入账 1299 后重试败者，要求该单恢复为支付成功且只扣一次，胜者不重复通知，两个库存预留保持 SKU/SPU 均为 6。

新脚本预期十二项 HTTP，带浏览器十三项 JUnit；实际 H5 仍为两项。三个变更 Java 定向格式检查、全依赖测试编译、Python 编译及差异检查通过；测试编译日志 `.runtime/mall-wallet-concurrent-compile.log`。

本轮再次尝试启动已归属 Redis，仍因读取已安装程序返回 `WinError 5` 失败，未启动新服务或创建随机库；新订单/支付竞争用例均未实际运行，不能登记并发验收或前移整合验收版本。恢复 Redis 程序读取/运行权限后，使用原 wrapper 串行执行商城 HTTP/H5 并检查无失败/错误/跳过、随机库清理和日志密码扫描；工作台真实回归与最新完整构建结论仍归属旧版本。

## 2026-10-26 优惠券领取与使用：真实 MySQL Service 回归

新增 `python script/jss/verify_coupon_services.py`：只使用已归属的 13316 MySQL，创建 `jss_coupon_service_verify_<随机值>`，载入原 V006，实际装配 `CouponServiceImpl`、`CouponTemplateServiceImpl`、两个生产 Mapper、租户插件及 Spring 事务代理；不使用 H2、Mapper/Service 替身或 Redis。三个未调用的会员/商品 API 仅为装配替身，不能视为完整模块/API/授权验收。日志先遮盖数据库密码再落盘，报告校验本轮时效与测试计数，最终删除随机 schema。

首轮六项中一通过、五失败，零错误/跳过：每人限领一张时六并发实际全部成功；批量领取在剩余量不足时仍成功；停用模板仍发券；缺失/外租户模板与他人券使用抛空指针。数据库写入失败回滚场景通过。证据 `.runtime/mall-coupon-services-before.log`、`mall-coupon-services-before-detail.log`、`mall-coupon-services-before.xml`。

修复后同模板领取先锁定模板行，再以当前读核验每人已领数量；避免等待期间沿用可重复读旧快照。校验本批领取后不超总量，Mapper 的计数条件也按完整增量比较；模板停用返回 `1013004006`，缺失模板/非本人券返回已有业务错误。无迁移、无新锁服务。注册发券内部入口复用相同校验，注册/购买赠券完整流程仍待单独验收。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_coupon_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test '-Dtest=cn.iocoder.yudao.module.promotion.**,TradeCouponPriceCalculatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

最终六项真实 MySQL Service 测试全部通过，零失败/错误/跳过，12.91 秒；`.runtime/mall-coupon-services-final.log`。核对总量 3 时先领 2、批量再领 2 拒绝且仍为 2、最后领 1；同会员六请求即使先在外层 RR 事务建立旧快照也只有一成功/五限领拒绝，实际券与模板计数均为 1。其他场景验证停用/过期及跨租户拒绝，券 UNUSED→USED→UNUSED→USED→EXPIRE、重复使用/返还拒绝，以及随机库内触发器故意拒绝写入时实际异常来自该触发器、券与计数均回滚为零。

营销专项 94 项中 20 执行通过、74 既有/条件跳过；交易优惠计算一项通过，合计 21 执行通过、74 跳过，零失败/错误，完整依赖及服务器测试编译成功；`.runtime/mall-coupon-promotion-regression.log`。其中六项新真实测试在无环境变量的常规回归中条件跳过，其真实结果以独立脚本为准，不能重复计数。八个变更 Java 定向 Spotless、Python 编译和差异检查通过。

`JssMallHttpIT` 已补一个尚未执行的实际优惠券用例：管理 API 创建仅适用本轮商品的券，会员领券/限领/归属拒绝，1299−200=1099 分结算、服务端重算和支付单金额，用券后拒绝重复下单且失败无订单/库存副作用，两轮取消返还后再用，停用模板拒绝发券。脚本新预期十三项 HTTP、带浏览器十四项 JUnit，实际 H5 仍两项。该 HTTP 用例与前轮订单/支付竞争均因本机 Redis 程序读取权限 `WinError 5` 尚未运行；无新的 HTTP/H5、全量 verify 或发布结论，开发库/服务与整合验收版本未改动。

## 2026-10-26 优惠券可领取展示状态

新增第七项实际 MySQL Service 场景，先以真实领取耗尽一张限量券，再读取 `getUserCanCanTakeMap`，复现已经耗尽的模板对游客仍返回 `true`；七项中六通过/一失败，零错误/跳过，`.runtime/mall-coupon-availability-before-valid-fixture.log`。原方法仅限制个人领取次数，未检查模板状态、总量、期限及领取类型。

现在游客与会员均先检查启用、直接领取、未到期和剩余总量，再叠加个人限领，个人次数查询不能把停用等模板重新改为可领取。新的真实测试使用生产 `YudaoSqlInjector`，也验证已领会员为 false、另一会员为 true、停用/过期/仅指定发放模板为 false；原六项 Service/事务测试同时继续通过。

`python script/jss/verify_coupon_services.py` 最终七项全过，零失败/错误/跳过，13.76 秒，`.runtime/mall-coupon-availability-after.log`；同脚本仍校验归属/清理随机库。三个 Java 定向格式、Python 编译、全依赖与服务器测试编译通过，`.runtime/mall-coupon-availability-format-check.log`、`mall-coupon-availability-compile.log`。上一节 21 项执行通过/74 跳过的营销专项仍对应 `7dd945ca30`，未冒充本次全量回归。

已有待运行的优惠券 HTTP 场景追加停用后另一会员读到 `canTake=false`；HTTP 数量仍为十三项，带浏览器十四项 JUnit。真实 HTTP/H5 和其他营销、注册/赠券依旧待后续验收，整合验收版本不变。

## 2026-10-26 注册与批量赠券的失败事务隔离

实际 MySQL Service 测试从七项扩为十项，使用类事务代理覆盖生产 `getSelf()` 调用。首轮十项中八通过、两失败、零错误/跳过：已耗尽的 REGISTER 模板插入券后更新计数失败，被注册入口捕获后留下未计数的券；批量 ADMIN 赠券在外层事务中捕获失败，仍因 REQUIRED 标记 rollback-only 导致 `UnexpectedRollbackException`，健康赠券及无关业务写入一起回滚。复现 `.runtime/mall-coupon-gifts-before.log`。

注册和批量赠券现在经类代理调用内部 `takeCouponWithSavepoint`，以 `Propagation.NESTED` 为每次赠券建立保存点。失败回滚该次券与计数，继续其余模板；成功赠券仍属外层事务，外层业务回滚时券与计数同时回滚。直接领取保留 REQUIRED 原子事务；无新依赖、表、迁移或对外接口。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_coupon_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test '-Dtest=cn.iocoder.yudao.module.promotion.**,TradeCouponPriceCalculatorTest,TradeOrderUpdateServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

修复后十项真实 MySQL 测试全过，零失败/错误/跳过，14.73 秒，`.runtime/mall-coupon-gifts-after.log`。新增场景核对失败注册券无残留、健康券发放及重复注册不超限；失败赠券不影响外层标记写入/健康赠券；健康赠券随外层主动回滚。原七项额度、并发、租户、归属、生命周期及写入故障回归继续通过。脚本清理随机库后只读查询同前缀 schema，无残留。

营销与交易专项 102 项中 21 执行通过、81 既有/条件跳过、零失败/错误，30 模块依赖与服务器测试编译成功，`.runtime/mall-coupon-gifts-regression.log`。本轮十项真实测试在常规专项中因没有隔离数据库环境全部跳过；订单更新三项既有测试也跳过，不能作为真实交易通过证据。两个 Java 定向 Spotless、Python 编译及差异检查通过。本次未运行全量 verify。

实际生产注册链/订单赠券 HTTP 及新商城/工作台回归仍待执行。Redis 已安装程序读取权限本轮仍为 `WinError 5`，没有启动新服务或修改开发库。兄弟前端已补第三项实际 H5 用例（领券、选券、取消返还、实付支付退款），仅类型与发现检查通过，尚未实跑；商城预期仍为十三项 HTTP + 一项浏览器启动，实际 H5 由两项增至三项。整合验收版本仍为 `a0a583c44a`。

## 2026-10-26 部门负责人可清空

迁移部门表单时新增回归 `DeptServiceImplTest.testUpdateDeptClearLeaderAndContacts`，在数据库预置负责人 123、电话和邮箱后，以完整更新表单提交 `leaderUserId=null`、两个联系字段空字符串。修复前 24 项中 23 通过、一失败（负责人仍为 123），零错误/跳过；`.runtime/departments-clear-leader-before.log`。

`DeptDO.leaderUserId` 增加 `FieldStrategy.ALWAYS`，完整部门更新允许将负责人列写为 NULL。当前生产 `deptMapper.updateById` 调用仅位于 `DeptServiceImpl.updateDept`；其表单必须携带要保留的负责人，接口不作为局部 PATCH 使用。电话/邮箱沿用空字符串清空，不改变租户/数据范围或权限检查，无迁移。

```powershell
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-module-system -am test '-Dtest=DeptServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

修复后 24 项全过、零失败/错误/跳过，14.26 秒，`.runtime/departments-clear-leader-after.log`；该套件使用 H2 和生产 Mapper/Service，权限服务为替身，不能计为真实 MySQL/HTTP/RBAC 验收。两个 Java 定向 Spotless 检查及差异检查通过。部门移动页面/真实授权链仍需单独验收；本机 Redis 程序读取 `WinError 5` 仍阻塞真实 HTTP/H5，整合验收版本未变。

## 2026-10-27 岗位实际 MySQL 与整批删除原子性

新增 `script/jss/verify_post_services.py` 和 `JssPostMySqlServiceIT`。只使用已归属的 13316 MySQL，在 `jss_post_service_verify_<12 位随机值>` 中载入原框架 SQL 的 system_post DDL；装配生产 PostServiceImpl/PostMapper、SQL 注入器、租户/分页插件和 Spring 事务代理，没有 Mapper/Service/API 替身。脚本遮盖数据库密码后写忽略日志，检查报告时效/四项计数及零失败/错误/跳过，并在 finally 核验归属和删除随机 schema。

首轮四项中三通过、一失败；补充同时核对异常和原行后再次复现：混合本租户/外租户 ID 的批量删除既没有业务拒绝，也已删除本租户行。日志 `.runtime/posts-services-before-atomic.log`，失败包含 `Mixed-tenant batch deleted an owned post`。原 `deletePostList` 仅调用带租户条件的 deleteByIds，忽略不可见行而产生部分成功。

现在先去重并按当前租户查询整批，存在不可见/缺失目标则返回已有 POST_NOT_FOUND；在 REQUIRED 事务内删除并核对实际删除数，不匹配时整批回滚。重复 ID 按一个目标处理；合法删除仍随调用方外层事务回滚。没有迁移或新权限路径。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_post_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test '-Dtest=PostServiceImplTest,DeptServiceImplTest,JssPostMySqlServiceIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

最终四项实际 MySQL Service 测试全过，零失败/错误/跳过，13.23 秒；`.runtime/posts-services-after.log`、保存的实际报告 `.runtime/posts-services-after.xml`。覆盖实际 CRUD/备注清空、分页名称/编码/状态筛选、停用岗位校验、同租户顺序重复名/编码拒绝、两个租户同名编码可并存、外租户读/改/单删拒绝、混合批次/缺失批次整体拒绝、重复 ID 合法批次、成功删除随外层回滚。随机 schema 无残留。

岗位/部门专项共 41 项中 37 项 H2 执行通过、四项实际 MySQL 用例条件跳过，零失败/错误；30 模块依赖与服务器测试编译成功，`.runtime/posts-regression.log`。实际 MySQL 结果以独立脚本为准，未将常规专项的四项跳过计为通过；两个 Java 定向 Spotless、Python 编译与差异检查通过。这不是全量 verify、HTTP 安全链或实际前端验收。

Redis 已安装程序读取本轮仍 WinError 5，最新商城/工作台真实 HTTP/H5 回归尚未执行。岗位移动页面与绑定/授权/用户岗位关联需独立实际验收；整合版本保持 a0a583c44a，开发库、运行服务及发布未变。

## 2026-10-27 角色授权接口的目标与部门校验

新增五项 `PermissionControllerTest`，直接调用控制器，协作者为 Mockito 替身，不包含 HTTP 过滤器、真实租户 SQL、Redis 或 RBAC。首个有效复现五项中一通过、四失败、零错误/跳过，`.runtime/role-grants-before-valid.log`：不可见角色可读取菜单关联、可收到菜单/数据授权请求，自定义数据范围包含不可见部门未被整批拒绝，非自定义范围仍向服务层传递旧部门 ID。最初一次测试夹具误将 TenantMenuHandler 当作 Consumer，已更正；该次夹具错误不计业务复现证据。

`PermissionController` 现在先用当前租户的 RoleService.getRole 核验目标，再读取/设置菜单或数据范围；自定义范围整批查询可见部门，任一缺失返回已有 DEPT_NOT_FOUND。非自定义范围统一传空部门集合。菜单分配继续保留原租户套餐过滤，并复制请求集合以接受 null/不可变集合，无新增权限或数据表。

```powershell
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test '-Dtest=PermissionControllerTest,RoleServiceImplTest,DeptServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

最终 51 项全过、零失败/错误/跳过：五项纯控制器前置校验，22 项角色 Service、24 项部门 Service 使用 H2；`.runtime/role-grants-after.log`。30 模块依赖与服务器测试编译成功，两个 Java 定向 Spotless 通过。此结果不是实际角色授权 HTTP、缓存失效或数据范围过滤验收；Redis 安装程序读取仍为 WinError 5，真实回归待恢复后执行。角色移动 UI、租户套餐及实际授权生命周期的完整结果另记，整合验收版本不变。

## 2026-10-27 审计查询实际 MySQL 与无浏览器登录日志

新增 `script/jss/verify_audit_services.py` 与 `JssAuditMySqlServiceIT`。仅连接已确认归属的 MySQL 8.4.9:13316，在 `jss_audit_service_verify_<12 位随机值>` 中载入原框架 SQL 的 system_login_log/system_operate_log DDL；装配生产 LoginLog/OperateLog Service、Mapper、字段填充、SQL 注入器及租户/分页插件，不使用业务替身或 Redis/HTTP。脚本遮盖数据库密码、检查报告时效/四项计数和零失败/错误/跳过，finally 再核验归属并删除随机 schema。

首个有效运行四项中三通过、一错误（零失败/跳过），`.runtime/audit-services-before-valid.log`：DTO 允许 UA 缺失，定时退出未提供 UA 时实际 MySQL 因 `Field 'user_agent' doesn't have a default value` 拒绝写入。最初新测试的继承链式 setter 导致编译失败已纠正，不计为业务复现。LoginLogServiceImpl 现在仅将 null UA 归一为空字符串，已有值原样保留，无表结构变更。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_audit_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test-compile '-DskipTests' '-Dspotless.skip=true'
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-module-system -am test '-Dtest=LoginLogServiceImplTest,OperateLogServiceImplTest,JssAuditMySqlServiceIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

最终实际 MySQL 四项全过、零失败/错误/跳过，报告 `.runtime/audit-services-final.xml` 和日志 `.runtime/audit-services-final.log`。覆盖账号/IP/布尔结果/时间组合与分页、操作人/业务大整数精确筛选、模块/操作名/操作内容和时间组合、两个租户的列表/详情隔离、社交登录及定时退出缺失可选账号/UA。脚本随机 schema 已清理。

常规日志专项九项中五项 H2 通过、四项实际 MySQL 条件跳过，零失败/错误；`.runtime/audit-regression-valid.log`，实际结果以独立脚本为准。服务器30模块依赖和测试编译成功（`.runtime/audit-compile.log`），两个 Java 定向 Spotless、Python 编译与差异检查通过。首次专项命令的 PowerShell 未引用带点 Maven 参数错误已修正，未计作执行通过。

Redis 安装程序读取仍 WinError 5，本批不包含实际 HTTP/RBAC/日志异步生成、详情名称翻译、导出或前端验收。没有新完整 verify、开发库/运行服务、部署或真机变更；前端新增审计查询四页及实际 H5 用例另行记录，整合验收版本未前移。

## 2026-10-27 共享字典实际 MySQL 与批删原子性

新增 `script/jss/verify_dict_services.py` 与 `JssDictMySqlServiceIT`。在已确认归属的 MySQL 8.4.9:13316 上创建 `jss_dict_service_verify_<12位随机值>`，仅载入原始 system_dict_type/system_dict_data DDL；装配两个生产 Service/Mapper、租户与分页插件、字段填充和真实 Spring 事务代理，无业务替身/Redis/HTTP。两个 DO 的 `@TenantIgnore` 原样保留，测试明确验证字典跨租户共享，不把它误称为租户私有数据。

首个有效运行七项中四通过/三失败，零错误/跳过，`.runtime/dict-services-before-valid.log`。缺失类型或数据 ID 混入批次时，既没有业务拒绝又已删除已有记录；隔离库中的专用触发器令第二条类型删除 SQL 失败时，第一条删除仍已提交。最初新测试的类闭合位置编译错误已纠正，不计业务证据。

现在两个批删先去重并检查整批存在，缺失时返回已有 DICT_TYPE_NOT_EXISTS/DICT_DATA_NOT_EXISTS；使用 REQUIRED 事务，类型逐行更新、数据批量逻辑删除均核对影响行数。类型 Mapper 仅更新未删除记录并返回行数，防止检查后目标变化被忽略。含子数据拒绝逻辑继续保留。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_dict_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test '-Dtest=DictTypeServiceImplTest,DictDataServiceImplTest,JssDictMySqlServiceIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

最终实际 MySQL 七项全过、零失败/错误/跳过，15.208秒；`.runtime/dict-services-after.log` 与保存的实际报告 `.runtime/dict-services-after.xml`。覆盖类型/数据完整 CRUD、备注/颜色/CSS清空、键值0及原字符串、类型名称/标识/状态/日期分页、数据类型精确/标签/状态和排序、全局共享与跨租户同标识唯一性、停用类型新增/修改拒绝、含子数据拒删、删除名称可复用、缺失批次整体拒绝、后续SQL失败整批回滚、重复ID和调用方外层回滚。触发器只创建于已校验的随机库并在 finally 删除；随机 schema 残留核对为0。

常规专项43项中36项H2执行通过（类型16、数据20），七项实际MySQL条件跳过，零失败/错误；服务器30模块依赖与测试编译成功，`.runtime/dict-regression.log`。四个变更Java定向Spotless、Python编译和差异检查通过。没有新完整verify、并发类型删除/数据新增竞争、HTTP/RBAC/缓存或前端实际验收。

Redis安装程序读取仍拒绝；真实商城/工作台需恢复后串行执行。字典仍是平台共享资源，未改变权限、表结构、共享边界或开发数据/运行服务；三端导出与其他管理模块继续待完成。

## 2026-10-27 参数配置实际 MySQL 与整批删除校验

新增 `script/jss/verify_config_services.py` 与 `JssConfigMySqlServiceIT`。在已确认归属的 MySQL8.4.9:13316 上创建 `jss_config_service_verify_<12位随机值>`，仅载入原框架 infra_config DDL；装配生产 ConfigServiceImpl/ConfigMapper/MapStruct转换器、租户与分页插件、Spring事务代理。没有业务替身、Redis或HTTP；一个场景直接调用生产ConfigController的按键可见性判断，不包含安全过滤器、方法鉴权或HTTP参数校验。ConfigDO原 @TenantIgnore 保持，测试明确验证平台共享。

首个有效运行六项中五通过/一失败，零错误/跳过，`.runtime/config-services-before.log`。缺失编号混入批删时，既未报CONFIG_NOT_EXISTS，也已删除有效记录。现在批删先去重并检查整批存在、保留系统内置保护；使用REQUIRED事务，逻辑删除影响数不符时抛既有错误并回滚。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_config_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test '-Dtest=ConfigServiceImplTest,JssConfigMySqlServiceIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

最终六项实际MySQL全过、零失败/错误/跳过，13.168秒；`.runtime/config-services-after.log` 与保存的实际报告 `.runtime/config-services-after.xml`。覆盖完整CRUD/备注清空/false/0和前导零原字符串、键名改名/旧键释放、大小写不敏感唯一性、名称/键名/类型/日期分页、内置参数单/混批拒删且允许编辑并保留内置类型、跨租户共享与同键唯一、缺失批次整体拒绝、重复ID和外层回滚。直接控制器分支验证按键读取不存在返回null、可见返回原字符串、不可见拒绝，而授权管理查询仍取得值；不把直接调用记为HTTP/RBAC。内置测试行只在随机库中由Mapper置为SYSTEM，不改开发配置。

脚本核验实例归属、遮盖密码、校验报告时效/六项计数及零失败/错误/跳过，并在finally删除随机schema；残留核对0。常规专项18项中12项H2执行通过、六项MySQL条件跳过，零失败/错误；服务器30模块依赖与测试编译成功，`.runtime/config-regression.log`。两个Java定向Spotless、Python编译和差异检查通过；没有新完整verify、配置并发改名或HTTP/RBAC结论。

Redis安装程序本轮读取仍拒绝。参数移动UI、按键可见性的真实安全链、工作台与原生后台共享范围、统一导出和其他管理模块需独立验收；本次无权限、表/迁移、开发库、运行服务或部署变更，整合验收版本保持原值。

## 2026-10-27 通知公告备注、V010与整批删除

上游移动公告有备注表单/详情，但原后端Save/Resp/DO与system_notice均无此字段。新增JSS V010仅追加remark varchar(200) NOT NULL DEFAULT ''，VO/DO和H2基座同步；管理VO可返回备注，DO@JsonIgnore排除原WebSocket实体推送中的后台备注。旧客户端省略/null备注保留原值，显式空字符串清空。部署新服务器前须备份、执行并validate V010；本批没有升级开发库/运行jar，V001–V009不改写。

```powershell
$env:JSS_MAVEN_COMMAND='D:/Project/JSS/JSS-server/.runtime/maven-integration.cmd'
python script/jss/verify_migrations.py
python script/jss/verify_notice_services.py
.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-module-system -am test '-Dtest=NoticeServiceImplTest,NoticeSerializationTest,JssNoticeMySqlServiceIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dspotless.skip=true'
```

实际Flyway十一项全过，`.runtime/notice-migrations.log`：完整初始化到V010、已有受管V009升级、旧正文/新备注保留、重复migrate/validate、历史漂移拒绝与前置/接管保护。Database前置表集合增加system_notice，临时旧版本资产副本只复制V001–V009，不改其原文件。三类迁移随机库和服务随机库残留均为0。

`verify_notice_services.py` 只在已归属MySQL8.4.9:13316的 `jss_notice_service_verify_<12位随机值>` 中载入原始公告表DDL并执行V010，再装配生产Service/Mapper、租户/分页插件和事务代理，无业务替身、Redis/HTTP。有效批删复现五项中三通过/两失败、零错误/跳过，`.runtime/notice-services-before.log`：混合外租户或缺失编号时，本租户有效公告已被删除且未拒绝。现在批删去重、整批存在检查、REQUIRED事务及实际删除数校验，任一不可见/缺失返回已有NOTICE_NOT_FOUND并保持已有行。

最终五项实际MySQL全过、零失败/错误/跳过，12.747秒；`.runtime/notice-services-final.log` 与保存的实际XML `.runtime/notice-services-final.xml`。覆盖完整CRUD/HTML原文/通知与公告类型/启停、备注写入/旧客户端null保留/显式清空、标题/状态分页、外租户详情/改/单删/混批、缺失批次整拒、重复ID与外层回滚。脚本遮盖数据库密码并检查报告时效/计数，finally核验归属、清理随机库。

最终常规专项13项中八项执行通过（七项H2 Service、一项生产JSON/Bean转换序列化）、五项实际MySQL条件跳过，零失败/错误，`.runtime/notice-final-regression.log`。序列化验证公告实体不带后台备注、管理VO仍带备注；没有发送消息或调用WebSocket。服务器30模块依赖与测试编译已通过，`.runtime/notice-regression.log`；补@JsonIgnore后system依赖编译及最终专项再次成功。六个变更Java定向Spotless、三个Python编译、差异检查通过。没有新全量verify或真实HTTP/H5/RBAC结果。

Redis程序仍读取拒绝，真实公告CRUD/备注/权限/租户与完整商城/工作台回归待恢复后串行运行；WebSocket在线消息、真机、导出及其他模块继续待验，不以Service或Flyway检查代替。详见architecture、sql/jss/README及前端通知公告说明。

## 2026-10-27 菜单层级、停用权限与共享授权清理

使用 `$env:JSS_MAVEN_COMMAND=(Join-Path (Get-Location) '.runtime/maven-integration.cmd'); python script/jss/verify_menu_services.py`。脚本只在已归属 MySQL8.4.9:13316创建 `jss_menu_service_verify_<12位随机值>`，载入原始system_menu/system_role_menu DDL，核对报告时间/计数并保存实际XML，finally核验归属并删除随机库。没有改写开发库或运行jar，没有新增迁移。

测试装配实际MenuServiceImpl、PermissionServiceImpl.processMenuDeleted、MyBatis Mapper/租户插件与真实Spring事务 advice；生产目标通过测试配置手工连线，未启动完整容器、Redis缓存切面、套餐选择或HTTP/RBAC。第一轮测试工具继承setter返回类型编译错误已修正，不计业务证据。有效修复前八项中三通过、四失败、一StackOverflowError，零跳过，14.642秒：允许移到后代、循环层级递归溢出、混合缺失批次静默删除、停用自身/祖先后权限编号仍被返回、其他租户角色授权残留。证据 `.runtime/menu-services-before-valid.log`、`.runtime/menu-services-before-valid.xml`。

最终八项实际MySQL全部通过，零失败/错误/跳过，15.166秒，`.runtime/menu-services-verified.log` 和脚本保留的 `.runtime/jss-mysql/menu-service-test.xml`。覆盖共享CRUD/显式清空/false/按钮字段规整、名称和组件名唯一/父类型/子项保护、禁止后代与带子项改按钮、旧循环/孤儿拒绝及万层无栈溢出、有效权限编号与停用祖先、缺失整批拒绝/重复ID、跨租户角色授权清理、后续SQL触发器失败时菜单和授权整体回滚及外层回滚。部分层级过滤使用构造的万层/循环节点调用实际纯方法，不冒充数据库产生这些历史脏结构。随机schema残留0。

服务器及依赖测试编译成功；菜单/角色/权限专项82项中74项执行通过、八项MySQL条件跳过，零失败/错误，`.runtime/menu-regression-final.log`，含MenuServiceImplTest20、RoleServiceImplTest22、PermissionServiceTest24、PermissionControllerTest5、PermissionApiImplTest3。旧权限缓存查询测试的随机状态/父编号不代表有效授权菜单，现显式启用根节点并加入停用/孤儿排除断言后通过。五个变更Java定向Spotless检查、Python编译与差异检查通过，不计全仓Spotless或全量verify。

权限编号缓存已有全局ignore-cache配置，菜单更新/删除继续清空该缓存；冷查询现读取完整树以核对祖先。角色反查缓存仍沿用既有租户缓存规则，不能以无Redis的MySQL测试认定跨租户缓存已验。需恢复Redis后运行实际菜单CRUD、同一员工Token的自身/祖先停用恢复、删除权限、套餐和并发改上级/增删竞争，再做冷缓存性能与完整商城/工作台HTTP/H5。整合验收版本保持原值。

## 2026-10-27 API日志处理竞争与Long处理人（V011）

`python script/jss/verify_apilog_services.py` 只在已归属 MySQL8.4.9:13316 的 `jss_apilog_service_verify_<12位随机值>` 中加载两张原始infra日志DDL，再应用V011；装配生产两Service/Mapper、租户/分页SQL及事务环境，无业务替身、HTTP或Redis。脚本配置/报告路径启动错误已修正，不计业务证据；有效修复前六项中三通过、两失败、一错误，14.497秒：非法状态不拒绝并写处理元数据、六个竞争处理者全成功、Long管理员号写入旧int列报超范围，见 `.runtime/apilog-services-before-executed.log`、`.runtime/apilog-services-before.xml`。

最终六项全部通过，零失败/错误/跳过，14.039秒，`.runtime/apilog-services-final.log`、`.runtime/jss-mysql/apilog-service-test.xml`。覆盖两类实际创建/完整筛选/日期/0/分页、双租户列表/详情/处理边界、INIT到DONE/IGNORE终态保护与非法目标无写入、9007199254740993处理人入库/VO保真、外层回滚、六线程只一胜者。隔离SQL触发器在UPDATE前短暂延迟制造重叠，不使用Mapper替身；失败者不改胜者状态/员工/时间。报告计数/时效检查并保存实际XML，finally核验归属、删随机库，残留0。

`python script/jss/verify_migrations.py` 最终十三项实际Flyway生命周期检查通过，`.runtime/apilog-migrations-verified.log`：框架基座 → baseline0/V001–V011；受管V009→V010公告保留，继而受管V010→V011类型扩展，旧最大int/NULL/0/负值与正文保留，写入Long后重复migrate/validate不丢失；其余账号/授权不seed、校验和和误初始化/前置表保护继续通过。最初升级测试行漏原DDL必填请求字段，修正完整夹具后重新全过，不视作生产迁移SQL失败。三类迁移随机库残留0，V001–V010未改写，开发库/运行包未升级。

服务器及依赖测试编译通过；API访问/错误日志常规专项15项中九项H2执行通过（3+6）、六项MySQL条件跳过，零失败/错误，`.runtime/apilog-regression.log`。既有成功用例从随机三状态改为只取DONE/IGNORE，INIT不是合法处理目标。六个变更Java定向Spotless、四Python编译和差异检查通过，无全量verify或新增HTTP证据。HTTP隔离工具的JSS历史计数改为迁移数量+显式baseline0，避免版本追加后硬编码失真。

真实H5准备使用仅src/test/专用profile的异常探针经真实异常处理器与异步日志API产生记录，检查UI读/处理、同记录双请求只一成功、租户隔离和无权限接口；不直接写表伪造HTTP日志，不新增生产Controller。Redis仍不可读，探针/实际异步链/HTTP/RBAC/原生导出与留存脱敏待实跑；最新工作台九HTTP加浏览器启动仍十项JUnit，真实H5增至十六项（十三文件），商城十三HTTP/三H5不变。必须先备份+migrate/validate V011再部署包含本批API日志处理的代码，整合验收版本不前移。

## 2026-10-27 会员标签/分组实际MySQL专项

`python script/jss/verify_member_segment_services.py` 在已归属 MySQL8.4.9:13316 上创建 `jss_member_segment_verify_<12位随机值>`，从不可变JSS V001提取原始 member_tag/member_group/member_user DDL，不导入业务账号或授权；finally再次核对实例归属并删除随机库。七项实际执行全过，零失败/错误/跳过，14.507秒，随机库残留0，见 `.runtime/member-segments-services.log`、`.runtime/jss-mysql/member-segment-service-test.xml`。脚本检查报告时间和七项数量后保存XML，日志口令脱敏。

测试运行生产Tag/Group Service、全部三Mapper、实际租户/分页插件和Spring事务。关联计数调用真实MemberUserServiceImpl的两个计数方法及真实SQL；该实例手工注入Mapper，未实例化这些计数方法不使用的短信/社交/MQ客户端，没有Service/Mapper替身。覆盖标签增改/名称重复/日期/逻辑删除后重建，分组状态0/启用精简列表/255字备注/第二页/日期，双租户读写/简单列表隔离，真实逗号标签和会员分组的删除保护，解除关联/已删除会员/其他租户会员边界，9007199254740993/995编号及外层事务同时回滚两类写入。它不是完整会员注册/短信/MQ或HTTP安全链验收。

常规命令 `mvn -o -Pjss-mall -pl yudao-server -am test -Dtest=MemberTagServiceImplTest,MemberGroupServiceImplTest,JssMemberSegmentMySqlServiceIT -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`（本机使用交接中的wrapper）20项中13项H2执行通过、七项MySQL条件跳过，零失败/错误；30模块服务器依赖及测试编译成功，见 `.runtime/member-segments-regression.log`。新增Java定向Spotless及Python编译通过；没有新全量verify、迁移或生产代码修改。

前端新增真实H5两项准备标签/分组页面CRUD、状态/备注、精简列表、标签重名、跨租户目标、工作台访问头和每类五接口无权限拒绝，只清理自建分类/绑定/会话，不拦截API或注入浏览器Token。工作台现十四文件十八项H5，仅类型和发现检查通过；关联会员删除保护当前只获上述实际MySQL证据，真实关联UI/API与并发分配/删除仍待验。Redis仍不可读，商城十三HTTP/三H5及工作台九HTTP/十八H5待串行实跑，整合验收版本不前移。

## 2026-10-27 会员等级配置、经验和调级实际MySQL专项

`python script/jss/verify_member_level_services.py` 在已归属MySQL8.4.9:13316上建立 `jss_member_level_verify_<12位随机值>`，只从不可变V001提取等级、等级记录、经验记录、会员四表原始DDL。七项实际执行全过，零失败/错误/跳过，15.559秒；随机库残留0，见 `.runtime/member-level-services.log`、`.runtime/jss-mysql/member-level-service-test.xml`。脚本校验报告时间及七项数量后保存XML，日志脱敏，finally复核实例归属再删随机库。

测试装配生产MemberLevel/MemberLevelRecord/MemberExperienceRecord三个Service、四Mapper、真实MemberUserServiceImpl读/更新/计数方法、租户/分页SQL和Spring事务；不使用这些方法以外的短信/社交/MQ客户端，无业务替身。七项覆盖完整配置增改/图标清空/折扣0/启停精简列表/排序筛选，名称/等级唯一和相邻经验门槛业务拒绝无写入，跨租户读写与会员目标，Long等级关联删除保护，人工调整/同级无重复/取消与两记录表落库，按最高启用经验门槛升级及降至较低等级、零增量无记录，最后会员SQL失败回滚前两条记录及外层回滚。等级变动通知仍是原TODO；这不证明真实消息、经验并发累加或幂等、溢出/低于全部门槛的业务策略。

常规 `mvn -o -Pjss-mall -pl yudao-server -am test -Dtest=MemberLevelServiceImplTest,MemberLevelRecordServiceImplTest,MemberExperienceRecordServiceImplTest,JssMemberLevelMySqlServiceIT -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`（本机wrapper）实际仅现有MemberLevelServiceImplTest十二项H2执行通过、新专项七项条件跳过，零失败/错误；不存在的两记录Service单测名不代表额外测试，服务器及依赖测试编译通过，见 `.runtime/member-level-regression.log`。新增Java定向Spotless和Python编译通过，没有生产代码/迁移/全量verify变化。

前端准备一个实际H5用例，通过真实会员登录/审核绑定、等级页面CRUD、数据库文件存储multipart上传及取回字节、外租户目标和每类五接口权限拒绝。仅对已守卫的隔离库临时创建DB文件配置/切主，finally清理自建文件/等级、恢复原主配置、删除自建配置、撤销自建绑定/登出；无API拦截或Token注入。工作台现十五文件十九项H5，仅类型/发现通过，Redis仍不可读，实际文件HTTP/缓存、完整HTTP/RBAC/原生上传和并发经验仍待验；整合验收版本及V011部署前置不变。

## 2026-10-27 会员经验并发、整数与变动记录

`python script/jss/verify_member_level_services.py` 已从七项扩为十五项，仍仅在归属MySQL8.4.9:13316的随机四表库执行生产Service/Mapper/租户SQL和事务，无业务替身/Redis/HTTP。有效修复前十五项七通过、八失败，16.449秒：六事务预读同一RR快照后各加10，最终90→100而非150；同级人工调级写六组记录；MAX+1溢出未拒绝；只扣5却记录MIN_VALUE；重复取消、无会员/空增量错误与最低门槛清级策略不符。见 `.runtime/member-experience-before.log`、`member-experience-before.xml`。

最终十五项全过，零失败/错误/跳过，16.661秒，随机库残留0，`.runtime/member-experience-after.log`、`member-experience-after.xml`、`.runtime/jss-mysql/member-level-service-test.xml`。六线程各先建立旧快照和MyBatis一级缓存，再屏障后调用生产事务；FOR UPDATE当前读使独立事件累计总额依次100/110/120/130/140/150，只一次跨级。同级人工调级只一组记录，重复null/0取消不重复写。long中间值防溢出；不足扣减按实际移除值记录，等级配置复制不再覆盖实际变动量，最后SQL失败/外层回滚仍通过。

最低门槛采用已向用户说明的默认策略：存在启用配置但经验低于全部门槛时清为无等级0；尚未收到相反偏好，并非用户已确认策略。全部配置停用/不存在时保留原行为，不据此宣称该策略已获产品确认。历史记录不重写，通知TODO仍未实现。当前十五项尚不保证相同bizType/bizId重投幂等、并发配置变更/分配/删除或完整订单奖励链。

常规 `mvn -o -Pjss-mall -pl yudao-server -am test -Dtest=MemberLevelServiceImplTest,MemberUserServiceImplTest,MemberUserServiceImplStatusTest,JssMemberLevelMySqlServiceIT -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`：30项中14执行通过（12H2、2Mockito状态测试），16跳过（15条件MySQL、1既有Disabled），零失败/错误；服务器及30模块依赖测试编译通过，`.runtime/member-experience-regression.log`。五Java定向Spotless apply/check和Python编译通过，格式日志编码已归一并保留raw副本，无新增全量verify/HTTP/Redis/H5证据。无迁移修改，V011部署前置保留，整合验收版本不前移。

## 2026-10-27 会员经验事件幂等与V012

在并发/边界修复e978b48771之后，二十一项专项新增事件重投检查：16通过、5失败，18.119秒，同一奖励10重投200变成210、六相同支付事件变成150、退款重投扣掉后来到账经验，旧重复记录可继续加经验，无有效业务编号也被接收；见 `.runtime/member-experience-idempotency-before.log`及XML。

最终 `python script/jss/verify_member_level_services.py` 二十二项实际MySQL全部执行通过，零失败/错误/跳过，19.034秒，`.runtime/member-experience-idempotency-final.log`、`member-experience-idempotency-final.xml`、`.runtime/jss-mysql/member-level-service-test.xml`，随机库残留0。原V001四表加V012事件表，生产三个等级Service、真实会员Service和五Mapper/租户/事务，无业务替身。覆盖相同事件六次并发只一次奖励及跨级、六名不同会员并发成功、不同会员/租户/类型/大小写键、不同载荷拒绝、ADMIN交互操作不合并、实际扣0或不足扣减的重复撤销不侵占后续到账、最后SQL失败/外层回滚可用同键重试、同一外层事务两次相同事件正常提交，以及历史重复/逻辑删除记录被封存。此前十五项并发/Long/边界/事务继续通过。

`python script/jss/verify_migrations.py` 十七项实际Flyway生命周期全部通过，`.runtime/member-experience-migrations.log`；原十三项公告/处理人/历史校验和保护继续通过，新增V012空初始化、受管V011升级/封存历史、唯一事件键、重复migrate/validate保留新旧事件。九条历史经验记录包含重复/已逻辑删除/ADMIN/空编号/双租户/不同类型/大小写键/Long会员，生成六个旧事件键且原记录逐字段快照不变；加入一个新事件后重复迁移仍为七个。三类迁移随机库和Service库残留0。V001–V011未改写，不修改会员余额或旧记录，不通过repair遮掩校验和。

常规37项中14执行通过（12H2、2Mockito），23跳过（22条件MySQL、1既有Disabled），零失败/错误，服务器及依赖测试编译成功，`.runtime/member-experience-idempotency-regression.log`。七Java定向Spotless apply/check、两Python编译和差异检查通过；无新增完整verify/HTTP/Redis/H5。当前代码部署前须先备份+migrate/validate V012；开发库/运行包未升级。订单/签到/售后完整链、锁顺序/吞吐、通知TODO和最低门槛偏好仍需后续确认/验收。
## 2026-10-27 会员资料权限、分类关联和显式清空

在后端目录设置 `$env:JSS_MAVEN_COMMAND=(Join-Path (Get-Location) '.runtime/maven-integration.cmd')`，再执行 `python script/jss/verify_member_profile_services.py`。脚本只在已归属MySQL8.4.9:13316创建 `jss_member_profile_verify_<12位随机值>`，从不可变V001提取四表，校验报告时效/八项计数并保存实际XML，finally核验实例归属后删除随机库。生产User Service经真实事务advice调用原User/Tag/Group Mapper及租户SQL，没有业务替身；未实例化未调用的短信/社交/MQ/OAuth客户端，测试使用启用会员，不覆盖禁用撤权或HTTP/RBAC。

有效修复前八项两通过/六失败、零错误/跳过，13.925秒，`.runtime/member-profile-before.log`和XML：普通资料可直接改等级，外租户标签/分组及停用分组可新分配，null字段不能清除，重复/非法标签未归一校验。初次工具编译错误及首次生产补丁的Lombok链式setter返回类型错误均已修正，不计业务证据。

最终八项全过17.261秒、零失败/错误/跳过，`.runtime/member-profile-final.log`、`.runtime/member-profile-final.xml`及 `.runtime/jss-mysql/member-profile-service-test.xml`。覆盖资料忽略等级且保留积分/经验，双租户目标及手机/邮箱唯一边界，新增关联租户/启停校验、失效原关联保留/移除，大Long标签去重/空值/255字限制，省略保留与显式null/0清空，JSON伪造内部标志无效且不序列化，地区超int拒绝及实际Service/外层事务回滚。随机库清理后残留0。

串行回归 `verify_member_level_services.py` 二十二项全过23.921秒、`verify_member_segment_services.py` 七项全过17.104秒，零失败/错误/跳过。服务器30模块依赖常规专项65项中27项执行通过（12等级H2、7分组H2、6标签H2、2状态Mockito），38项既有/条件跳过（37项MySQL和1项禁用User测试），零失败/错误，`.runtime/member-profile-regression.log`。两状态Mockito仅核对锁查询及撤权调用约定，不是Redis证据。六个变更Java定向Spotless通过（VO匹配同时规范化同后缀App VO换行，无内容差异）、新Python编译及差异检查通过；无新全量verify、迁移或真实HTTP/H5结果。

会员管理源页面及详情十一标签尚未迁入，不登记为页面完成。真实CRUD/RBAC/Redis撤权、分类变更竞争、关联会员UI、积分调整和完整订单/签到链继续待验；V012部署前置与整合验收版本保持不变。

## 2026-10-27 手工积分不足、上限与并发调账

使用同一Maven wrapper环境运行 `python script/jss/verify_member_point_services.py`。脚本只在已归属MySQL8.4.9:13316创建 `jss_member_point_verify_<12位随机值>`，从不可变V001提取会员/积分流水两表，装配生产Point/User Service、两个Mapper、租户SQL与实际事务advice，没有业务替身或未调用的OAuth/短信/社交/MQ客户端；它不是HTTP/RBAC/Redis验收。报告时效/十项计数/零跳过检查、口令脱敏和finally归属清理沿用专项约定。

有效修复前十项六通过/四失败、零错误/跳过，17.023秒，`.runtime/member-point-before.log/XML`：手工扣减不足与正数溢出静默成功，null参数空指针，并发逼近int上限的失败是数据库异常。首次修复后十项全过17.678秒；最终格式化后的十项全过11.379秒，零失败/错误/跳过，`.runtime/member-point-final.log`、`.runtime/jss-mysql/member-point-service-test.xml`。覆盖ADMIN不足/MIN扣减、MAX及自动奖励预检溢出、空增量/缺失/外租户会员、同管理员两次独立操作、六并发加10由90到150且六流水余额连续、六并发扣30从90仅三笔成功、临近上限仅三笔成功及其余业务错误、后置流水SQL失败/外层回滚/重试、零操作和流水租户隔离。三个竞争用例先以六个独立事务预读旧RR快照/MyBatis缓存，再同时调用实际Service。

保留原自动业务预检扣减不足时跳过的规则，新增实际用例核对SIGN及退款赠分撤销类型无写入；不据此认定完整退款策略或订单抵扣正确。ADMIN用会员行锁覆盖校验/余额/流水；自动类型保留原查询/条件更新方式，自动奖励并发上限、积分事件幂等、完整订单/签到链与HTTP手工调账仍待验。V012只保证经验事件，不覆盖积分事件；没有新增迁移、改开发库或替换运行jar。

最终复验前发现原验证句柄已不存在、无最终报告且隔离MySQL停止；归属工具重启实例后清理一个已守卫前缀的中断随机库，再执行最终专项。原首轮十项通过证据保留，不把中断轮次当结果。Redis程序重新读取仍被拒绝，真实商城/工作台HTTP/H5待恢复后串行执行。

最终常规命令按交接wrapper运行 `-o -Pjss-mall -pl yudao-server -am test -Dtest=MemberPointRecordServiceImplTest,MemberLevelServiceImplTest,MemberGroupServiceImplTest,MemberTagServiceImplTest,MemberUserServiceImplTest,MemberUserServiceImplStatusTest,JssMemberPointMySqlServiceIT,JssMemberProfileMySqlServiceIT,JssMemberLevelMySqlServiceIT,JssMemberSegmentMySqlServiceIT -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`。84项中36项执行通过（34项H2含原九项积分实际Service/spy、两项状态Mockito），48项既有/条件跳过（47MySQL、一禁用User测试），零失败/错误；服务器30模块依赖及测试编译成功，`.runtime/member-point-regression.log`。三变更Java定向Spotless、新Python编译和差异检查通过，随机库残留0。未运行新全量verify、Flyway或真实HTTP/H5；当前专项不前移整合验收版本。

## 2026-10-27 管理积分流水日期筛选

继续使用 `python script/jss/verify_member_point_services.py`，实际专项由十增至十一。管理PointPage VO增加带原日期格式的createTime范围，原Point Mapper管理查询增加between；会员App原有日期/增减筛选不改。没有新表或迁移。

新增用例在同一归属随机库内用实际Service创建流水，固定首日00:00:00/末日23:59:59及范围外一秒、不同标题/会员/租户夹具，实际Service组合会员昵称/编号、标题、业务类型、日期并翻到第二页，再检查昵称无匹配时空结果。有效SQL修复前十一项十通过/一失败12.046秒，日期范围应返回2却返回3，`.runtime/member-point-date-before-valid.log/XML`。最初测试夹具误把继承BaseDO链式setter结果当子类插入，编译错误已修正，不计业务证据；旧条件跳过XML未作为有效复现。

最终十一项实际MySQL全过13.419秒、零失败/错误/跳过，`.runtime/member-point-date-final-verified.log`、`.runtime/member-point-date-final.xml`，随机库残留0。服务器30模块依赖常规20项中九项积分H2/spy执行通过、十一项实际MySQL条件跳过，零失败/错误，`.runtime/member-point-date-regression.log`；测试编译成功。三变更Java定向Spotless（另匹配App同后缀VO仅换行，无内容差异）、Python编译与差异检查通过，无新增HTTP/RBAC/全量verify或Flyway结果。会员页面仍待迁入，Redis读取权限与V012部署前置不变。

## 2026-10-27 后台调价与交易/订单项/支付单一致性

宿主真实验收准备（18:44）：JssHttpFixtures在原随机库/数据源守卫内增加9007199254740993/9007199254740995两个专用会员，供会员列表/资料清空、独立写入及跨页发券真实H5使用，不变更原登录身份。定向Spotless和服务器依赖测试编译通过10.932秒，`.runtime/member-browser-fixture-compile.log`；SQL/HTTP/浏览器尚未执行。前端scripts/test-integration-host.mjs只允许非CodexSandbox账号，串行复用原verify_workspace_http.py的workspace/mall --browser，并复制新鲜JUnit/JSON报告；保持原13316随机库/16379归属Redis及finally清理，不用开发3306/6379/48080。完整套件仍待宿主实跑，不能以编译登记通过。

独立核销码转换专项：TradeOrderPickUpCodeConvertTest直接调用生产MapStruct生成实现，修复前两项一失败/一错误0.126秒，前导零丢失且字母码NumberFormatException；改BaseVO为String后两项0.020秒全过。联合5请求VO和2Mockito价格测试共九项执行、零失败/错误/跳过，服务器30模块依赖测试编译成功；`.runtime/pick-up-code-before.log`和`pick-up-code-after.log`。两个Java定向Spotless和差异检查通过。只证明列表/详情转换保真，不含真实HTTP/MySQL核销、门店RBAC或Redis；无新迁移、数据库/运行jar变更。

同一Maven wrapper环境执行 `python script/jss/verify_order_price_services.py`。脚本仅在已归属MySQL8.4.9:13316的 `jss_order_price_verify_<12位随机值>` 中，提取不可变V008交易单/订单项及V004支付单三张原始表DDL。实例归属、报告时效/十二项/零跳过检查、日志口令脱敏与finally删库沿用专项约定；没有导入开发账号/授权或改开发库。

实际装配TradeOrderUpdateServiceImpl、PayOrderApiImpl、PayOrderServiceImpl和三Mapper/租户SQL、生产事务注解advice；没有Service/Mapper替身。只连接调价实际调用的依赖，不实例化未调用的支付客户端/Redis/MQ，未装配TradeOrderLogAspect或完整容器。因此不计真实支付通知、审计切面、HTTP/RBAC或外部渠道验收。另一连接提交支付状态只是竞态夹具，不冒充实际支付回调。

有效修复前十项五通过/五失败13.508秒，`.runtime/order-price-before.log/XML`：负数调价可重复/归零、六个旧快照事务全部成功而应只一笔、已支付状态被旧缓存绕过、已关闭未支付单仍可改、空增量触发NPE。首次修复后十项全过13.585秒；补充支付已完成但交易尚未收到通知及缺失关联两项后，最终十二项全过14.106秒、零失败/错误/跳过，`.runtime/order-price-final.log/XML`、`.runtime/jss-mysql/order-price-service-test.xml`，随机库残留0。

覆盖两订单正负分摊后三类总额一致、任何非零旧调价拒绝再改、六预读RR快照/一级缓存事务一成功五已调价错误、已支付/已关闭拒绝、租户不可见目标、支付状态另一连接已提交后不同价/同价请求均拒绝、通知未到窗口整体回滚、后置支付SQL和项SQL失败/外层回滚、缺失交易/支付关联、空/零/上下界业务错误。SQL触发器仅在归属随机库制造失败，不替换业务方法。没有验证所有运费/赠品/历史脏项/大单分摊、提交支付报价/支付拓展单与改价竞争、完整取消/退款锁顺序或日志落库。

常规 `-o -Pjss-mall -pl yudao-server -am test -Dtest=TradePriceServiceImplTest,AppTradeOrderSettlementReqVOTest,TradeOrderUpdateServiceTest,JssOrderPriceMySqlServiceIT -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`（使用交接wrapper）22项中七项执行通过（5请求VO、2Mockito价格计算），15跳过（12条件MySQL、3既有禁用订单Service），零失败/错误；服务器依赖及测试编译成功，`.runtime/order-price-regression.log`。原PayOrderServiceTest依赖内嵌Redis，当前二进制读取权限未恢复，未执行且不将其记为条件通过。五Java定向Spotless、新Python编译及差异检查通过；无新全量verify/Flyway/HTTP或部署结果。
## 2026-10-27 运费模板规则与金额边界

通过本机 `.runtime/maven-integration.cmd` 串行执行 `-o -Pjss-mall -pl yudao-server -am test -Dtest=TradeDeliveryPriceBoundaryTest,TradeDeliveryPriceCalculatorTest,DeliveryExpressTemplateValidationTest,DeliveryExpressTemplateConvertTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`。最终33项全部实际执行通过、零失败/错误/跳过：新增计算边界10、实际Hibernate Validator三项、生产MapStruct两项及原计算器18项；30模块服务器依赖/测试编译成功，46.646秒，`.runtime/freight-rules-after.log`。输入地址/模板/配置提供者是Mockito，生产价格计算器实际执行；没有新MySQL/HTTP/Redis/H5证据。

有效修复前15项五通过、九失败、一错误，`.runtime/freight-rules-before.log`及`freight-before-{calculator,validation,convert}.xml`：续费零步长、金额乘法正数回绕、非有限或负数规则、未知模式、项/订单应付溢出未正确拒绝，缺失详情转换空指针。新增计算测试最初误用了既有测试文件名，执行前原文件暂被覆盖；现已恢复原文件字节，新增用例独立命名Boundary，最终同时回归原18项。修复前XML中的Calculator类名指新增十项，不指原18项。

正常件数/重量/体积、向上取整、零续费固定运费、包邮门槛及零重量分摊守恒继续通过。请求层拒绝空计费行、非法模式、负数及零步长正续费；运行层对历史规则再次校验并在long中计算运费、项应付及跨模板总额边界。未扩大为所有SKU原价/折扣/营销链的整数安全保证；包邮早返回、区域匹配层级、历史脏数据、真实Service/HTTP/RBAC和运费前端仍待验。没有迁移、开发库或运行jar变化，整合验收版本不前移。

后续地区缺失修复：TradeDeliveryPriceBoundaryTest增加三项，修复前13项11通过、两失败、零错误/跳过9.286秒，`.runtime/freight-region-before.log/XML`。原多模板循环跳过缺失模板、未达包邮门槛且无计费规则直接返回，两者均静默漏算；现统一抛原找不到模板业务错误。包邮区域单独匹配且达到两门槛仍成功。相同服务器依赖测试命令最终36项全过（13边界、18原计算、三Validator、两MapStruct）、零失败/错误/跳过，53.653秒，`.runtime/freight-region-after.log`。Mockito输入边界保持，不冒充数据库报价/HTTP结果。

## 2026-10-27 运费模板实际MySQL事务

设置交接Maven wrapper后运行`python script/jss/verify_freight_template_services.py`，仅在已归属MySQL8.4.9:13316建立jss_freight_verify随机三表库，表取自原V008。八项实际生产Service/Mapper、租户SQL、生产MapStruct和事务advice全部通过，零失败/错误/跳过13.936秒，`.runtime/freight-service-first.log`及`.runtime/jss-mysql/freight-template-service-test.log/XML`；finally核对归属并删除随机库，残留0。无业务替身，未实例化HTTP/Redis或方法校验切面。

覆盖超过JS安全整数根/子行编号、列表往返、保留/新增/删除计费行及更新冗余模式、null/空包邮清除、同租户重名拒绝/外租户同名成功、外租户详情/列表/匹配与删除拒绝、名称/模式/日期/排序分页、最后包邮INSERT真实触发器失败时创建/更新全回滚、外层回滚和三表逻辑删除、精确地区/仅包邮匹配、不将父地区自动扩成下级，以及伪造另一模板子行主键被数据库拒绝且全事务无变化。最后一项验证主键约束与回滚，未声称已提供业务友好的子行ID错误或并发修改互斥。

常规服务器依赖测试`-Dtest=TradeDeliveryPriceBoundaryTest,TradeDeliveryPriceCalculatorTest,DeliveryExpressTemplateValidationTest,DeliveryExpressTemplateConvertTest,JssFreightTemplateMySqlServiceIT`（其余参数同前wrapper命令）：44项中36执行通过、八MySQL条件跳过，零失败/错误；服务器依赖及测试编译成功27.682秒，`.runtime/freight-service-regression.log`。与前述实际八项分开登记。新Java定向Spotless检查、Python编译及差异检查通过。没有新Flyway、HTTP/RBAC、Redis、完整verify或开发库/运行jar变化；并发模板更新/重名创建、商品引用删除策略、完整报价/下单/退款链和H5仍待验。

## 2026-10-27 运费模板旧快照并发修改/删除

同一`verify_freight_template_services.py`扩为十一项。有效修复前十项八通过、两失败，零错误/跳过12.612秒，`.runtime/freight-concurrent-before.log/XML`：六事务在RR快照与MyBatis缓存中预读旧详情后同步保存全替换规则，最终有效计费行应一条却为六条；另一连接删除根/子行后，旧事务保存未拒绝。修复前的XML已另存，不以新条件跳过报告覆盖。

模板update/delete先锁根当前读，修改再按编号当前读锁两类旧子行做diff。首次十项全过12.889秒；最终新增保留计费ID的六并发编辑，十一项全过13.079秒、零失败/错误/跳过，`.runtime/freight-concurrent-final.log/XML`与`.runtime/jss-mysql/freight-template-service-test.xml`。全替换和保留ID两种竞争均要求六次保存都成功，最终只有一套有效计费/包邮行，根名称/排序/模式与该套区域/金额一致；不要求固定线程获胜。删除后旧事务返回原不存在错误且无有效孤立子行。原八项事务/租户/Long/查询继续实际执行，随机库残留0。

常规服务器依赖专项同上：47项中36执行通过、十一条件MySQL跳过，零失败/错误，测试编译成功27.447秒，`.runtime/freight-concurrent-regression.log`。五Java逐类Spotless apply/check、Python编译及差异检查通过，无新Flyway/完整verify/HTTP/Redis证据。最后完整保存生效的语义保留，没有版本号或字段合并；重名创建/更名竞争、普通读多查询快照、旧子行ID友好拒绝、商品引用与全局死锁/吞吐仍待验。

## 2026-10-27 支付应用精简列表的方法鉴权与投影

使用交接wrapper串行执行`.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-module-pay -am test -Dtest=PayAppLookupControllerTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，七项实际执行、零失败/错误/跳过10.518秒，reactor33.667秒，日志`.runtime/pay-app-lookup-test.log`。必须选择jss-mall profile；最初未选profile时reactor不含pay而报错，没有将那次启动错误记为业务复现。

格式化后`.runtime/maven-integration.cmd -B -ntp -Pjss-mall -pl yudao-server -am test -Dtest=PayAppLookupControllerTest,PayWalletControllerTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`八项实际执行全部通过，七新方法鉴权10.152秒、原钱包Controller一项0.127秒，全部服务器依赖/测试编译46.288秒，日志`.runtime/pay-app-lookup-regression.log`和pay模块surefire两份XML。三个改动Java分别用-N spotless:apply/check与单类spotlessFiles检查通过，`.runtime/pay-lookup-format/check-*.log`；没有全仓格式化。

新七项实例化生产PayAppController的真实Spring方法鉴权代理、生产SecurityFrameworkServiceImpl和JSON Long序列化模块，覆盖六query权限各自与工作台上限同时满足时通过、空列表、匿名/旧merchant权限/create-only拒绝、native权限和上限落在OR不同分支时拒绝、空上限拒绝native通配授权、精简读取不提升完整/get和/list权限，以及已停用应用名称仍保留、超JS安全范围Long输出字符串、序列化仅id/name而无appKey/回调URL/备注等字段。PermissionCommonApi和AppService数据为Mockito替身，不是数据库RBAC、HTTP FilterChain、实际租户SQL、Redis或浏览器验收；没有部署/迁移/开发库或运行jar替换。原PayAppServiceTest仍为上游Disabled，未解禁也未计入执行成功数。

## 2026-10-27 示例订单与提现的独立权限及V013

修复前使用交接wrapper `-o -B -ntp -Pjss-mall -pl yudao-module-pay -am test -Dtest=PayDemoPermissionControllerTest#queryOnlyDoesNotAuthorizeWrites -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，一项实际失败9.725秒：只有query能力的方法层调用没有拒绝创建，日志/XML `.runtime/pay-demo-permissions-before.log/xml`。这是方法授权复现，非匿名HTTP或真实资金调用。补六个PreAuthorize后新七项实际执行通过11.211秒，`.runtime/pay-demo-permissions-after.log`。

三Java定向Spotless apply/check后，使用同wrapper `-o -B -ntp -Pjss-mall -pl yudao-server -am test -Dtest=PayDemoPermissionControllerTest,PayAppLookupControllerTest,PayWalletControllerTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，十五项全部执行、零失败/错误/跳过，七新项0.673秒、查询七项10.92秒、钱包一项0.227秒，服务器依赖/测试编译约61秒，`.runtime/pay-demo-permissions-regression.log`及pay模块三XML。真实Controller代理、生产SecurityFrameworkService、方法/JSR250权限运行；RBAC存储和资金Service是Mockito，覆盖六动作逐一同时需要native/ceiling、query-only拒绝四写动作、单写不含其他能力、空上限拒绝native通配、native无上限仍需授权、管理员actor=20及三个PermitAll通知仍委托原Service。未调用实际支付、退款、转账提供商或认证HTTP过滤链。

`python script/jss/verify_pay_demo_permissions.py`在已归属MySQL8.4.9:13316随机三表库实际执行六项：精确六权限/隐藏按钮、重复应用不重写、原菜单/角色授权/套餐逐字保持、停用自定义节点不重启用、软删除历史保留且只生成一个有效替代；最后随机库残留0，`.runtime/jss-mysql/pay-demo-permissions-schema.json`。原生DDL来自框架基座，未触碰开发库或执行角色授权。

设置JSS_MAVEN_COMMAND为本机忽略目录的maven-flyway-v013-offline.cmd（封装原wrapper并追加-o）后，`python -u script/jss/verify_migrations.py`完整二十项真实Flyway生命周期全过，`.runtime/pay-demo-flyway-lifecycle.log`。新增fresh V013六定义无grant、受管V012升级V013保留停用自定义权限/Long菜单/原角色及套餐与经验历史、重复V013 migrate/validate保持全部元数据；原V010–V012升级、校验和拒绝及初始化守卫继续执行。三随机库最后均已删除并核对无匹配schema。没有开发迁移/发布或真实示例HTTP/H5结果。

## 2026-10-28 支付收银台提交权限与V014

管理端 `/pay/order/submit` 新增独立 `pay:order:submit`，原钱包归属与业务提交链保留。真实方法鉴权代理/生产权限服务、业务与RBAC存储替身回归16项全部执行通过，零失败/错误/跳过，`.runtime/pay-cashier-permissions-final.log`。覆盖匿名、query-only、native/工作台上限任一缺失均不能触达支付或钱包Service，授权交集满足后才委托原Service。不代表认证HTTP或真实渠道付款。

V014只新增隐藏按钮元数据，不给角色/套餐授权，不重启用现有节点。使用既有本机Maven offline wrapper运行 `python -u script/jss/verify_migrations.py`，真实MySQL8.4.9:13316上22项Flyway生命周期全部通过，`.runtime/pay-cashier-flyway-final.log`；含fresh V014、真实V013→V014保留停用自定义节点及原grant、重复migrate/validate，原V010–V013回归与初始化/校验和守卫仍执行。首轮新增夹具编号与V013自增编号相撞，改用独立Long编号后复验通过；未修改迁移以绕过冲突。脚本finally已清理三个随机库；未操作开发库或运行jar。两个Java定向Spotless apply/check通过，`.runtime/pay-cashier-format-check.log`。

## 2026-10-28 真实HTTP/H5重跑中的夹具和菜单断言

通过前端宿主入口 `node scripts/test-integration-host.mjs all` 串行执行原verifier。baefec05f9将两个Long会员的邮箱设为各自ID构造的唯一值，保留既有唯一索引；真实Spring已实际启动。07aeed2593只在测试profile启用访问日志，并将扩展后的21项浏览器进程限时改为1080秒，单场景仍90秒。

0d8860fab0修正旧菜单测试：已有e5169f14c2会让停用按钮/父菜单同时限制native与workspace；测试现在要求两种Token均403、恢复均0。停用workspace入口仍仅拒绝本应用会话，native用户查询保持成功。生产鉴权代码未修改。

实际随机库 `jss_http_verify_7c4848851c10` 的新鲜JUnit为10项、1失败、0错误/跳过，445.35秒；九个真实HTTP场景全过，失败是H5动态项。该轮H5为19过/2失败：会员等级选择层被调级对话框挡住，导致90秒超时并使其后清理未完成，下一发券场景读取了上一场景昵称。截图和报告由前端保留，正在修实际UI；不能将九个HTTP通过登记为整套组合成功。前端上一轮曾20过/1失败。

本轮没有部署、修改开发库/角色授权或更新整合版本。最新细节见 `.runtime/jss-http/last-run.json` 所指logDirectory；根目录同名浏览器日志是历史文件。Maven与verifier严格串行，随机库由finally清理。

## 2026-10-28 商城定向真实组合通过

主目录通过 `node scripts/test-integration-host.mjs mall` 的宿主运行全部退出0：13个实际HTTP场景加浏览器动态项，共14项JUnit零失败/错误/跳过；3项真实H5全部通过、54.802秒、零跳过/重试。证据位于前端 `test-results/integration-host/2026-10-27T18-20-01-675Z`（mall-junit.xml、mall-browser.json、state.json），实际使用随机库jss_http_verify_0f1c5e357ff7与归属Redis，finally清理已完成。

H5覆盖商品列表/购物车结算下单及取消恢复库存、真实领券/优惠结算/取消返券/余额付款/按实付退款、余额支付后取消售后并重新申请退款。领取、价格、库存、券状态、钱包流水和通知任务仍走原实际API断言。此前两项通过/领券一项失败的报告保留；前端修正实际按钮目标、区分页脚与SKU弹层，并用独立商品深链建立每次结算的页面栈，未减少业务断言或强制点击。

该定向运行使用已合入145管理源页的前端ba8a552。既有工作台9 HTTP/21 H5也已实际全过，但先前组合未包含最新营销源码；仍需在当前版本执行完整all并取新鲜两套成功报告，不能以本次mall单独成功更新整合版本或宣布全量完成。支付为隔离库内的实际余额/测试通道，不是外部提供商、生产资金或原生设备验收。


## 2026-11-04 main 同步 JDK 25 上游

将 `upstream/master-jdk25` 的 `947f986e0fc605538caa668b243910fd3320c3d5` 合入本地 `main`（原 HEAD `dc9a7e4cad`，包含尚未推送的 `docs/uniapp/uniapp.md` 提交）。GitHub 与 Gitee 的 JDK 25 分支头一致，本轮合入 77 条上游提交，项目版本随上游更新为 `2026.10-jdk25-SNAPSHOT`。

196 个初始冲突文件中，186 个经统一三方 Java 格式后确认本地仅有格式变化，8 个完成规范化三方合并；手工合并权限上下文测试，保留本地三个异步隔离回归并加入上游线程池/子线程测试，通义模型示例测试跟随上游多模态接口配置。保留本地私有文件策略、订单调价与券库存校验、通知归属及禁用时序存储模式。原 `uniapp.md` blob `f0d467e0917938f52589164fe0f246c4b674ca23` 字节不变。

验证过程中修复三处整合问题：财务账套的两个直接锁定读测试显式开启事务；报表模板测试改从 classpath 加载固定 SQL 夹具（原文来自上游历史提交 `152036a7ab`，原升级脚本此前已删除）；本地 `IotDisabledTimeSeriesDAO` 补齐上游新增的 `deleteProductPropertyTable` 接口，仍抛出存储禁用错误，并纳入原拒绝操作回归。没有放宽生产事务校验或执行业务数据库升级。

JDK 25.0.2 验证命令与结果：

- 先执行 `mvn -B -ntp -f yudao-dependencies/pom.xml install -DskipTests` 安装新版本 BOM，解决根项目单独 `-N` 检查时新 BOM 尚未进入本地缓存的问题。
- 服务端 45 模块范围先运行 `mvn -B -ntp -Pjss-mall -pl yudao-server -am spotless:apply spotless:check test`，使用本机 Mockito 5.23.0 javaagent。首轮在 FMS 的旧测试条件处失败；修复后用相同 reactor 加 `-rf :yudao-module-fms -Dspotless.skip=true` 继续完成回归。按测试类取最终结果去重：610 类、6418 项，6182 通过、236 既有/条件跳过、0 失败/错误。权限上下文 10 项、文件服务 45 项、财务账套 13 项、财务模板 10 项、禁用存储模式 3 项均通过。日志 `/tmp/server-jdk25-tests.log`、`/tmp/server-jdk25-tests-resume.log`。
- `mvn -B -ntp -Pjss-mall -pl yudao-server -am install -DskipTests -Dspotless.skip=true` 打包并安装。首轮暴露上述 IoT 接口缺失，补齐后从 `:yudao-module-iot-biz` 恢复，全部 45 模块分段成功，生成服务端 jar；此阶段跳过测试。日志 `/tmp/server-jdk25-package.log`、`/tmp/server-jdk25-package-resume.log`。
- 报表模块独立 `mvn -f yudao-module-report/pom.xml test -Dspotless.skip=true`（同一 Mockito agent）：113 项全部通过，含新增 UReport2；日志 `/tmp/server-jdk25-report-tests.log`。
- AI1 独立定向运行 `Ai1LlmModelFactoryTest,Ai1HomeServiceImplTest,Ai1SessionMessageServiceImplTest`：44 项全部通过；日志 `/tmp/server-jdk25-ai1-tests.log`。
- IoT 网关独立 `mvn -f yudao-module-iot/yudao-module-iot-gateway/pom.xml test -Dspotless.skip=true`（同一 Mockito agent）：300 项中 214 通过、86 既有禁用跳过，0 失败/错误；日志 `/tmp/server-jdk25-gateway-tests.log`。
- 在 `sql/tools` 执行 `PYTHONDONTWRITEBYTECODE=1 uv run --with simple-ddl-parser python -m unittest test_convertor -v`：19 项全部通过；日志 `/tmp/server-jdk25-sql-tests.log`。
- 撤回全局格式化对本轮无关文件的修改，最终仅对合并涉及的 Java 文件执行 `mvn -B -ntp -N spotless:check -DspotlessFiles=...`；差异和未解决冲突检查通过。未将以上结果记为真实业务 HTTP、MySQL/Redis、外部 AI/支付服务或部署验收。


## 2026-11-04 小程序补充业务权限上限回归

`mvn -o -B -ntp -pl yudao-module-gym -am test -Dtest=GymMobileWorkspacePermissionTest,GymOrganizationServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，沿用本机 Mockito 5.23.0 javaagent。原610项业务回归通过；新增看板夹具首次未声明全门店范围而失败，补正夹具后单独运行 GymMobileWorkspacePermissionTest 四项全过。文件三个参数场景验证原生/上限授权交集，原生凭证兼容；看板空上限不查聚合。Spotless apply/check通过。使用H2及RBAC/存储替身，无新真实HTTP/Redis结果。


## 2026-11-04 短信验证码单次消费

`mvn -o -B -ntp -pl yudao-module-member -am test -Dtest=SmsCodeServiceImplTest,MemberRegistrationBoundaryTest,MemberUserRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，本机Mockito javaagent同前。11+8+2共21项全过、零失败/错误/跳过，/tmp/server-fix-sms-final.log。生产Mapper/H2屏障控制两个未使用快照，仅一请求消费成功；另验证过期条件更新和外层事务回滚后的可重试性。未发送真实短信或运行认证HTTP/Redis链。


## 2026-11-04 赠券回收竞争与回滚

`mvn -o -B -ntp -Pjss-mall -pl yudao-module-mall/yudao-module-trade -am test -Dtest=CouponServiceImplTest,CouponTemplateServiceImplTest,TradeCouponPriceCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，本机Mockito agent同前。10+8+1共19项全过、零失败/错误/跳过，/tmp/server-fix-coupon-final.log。五个新用例包含未使用/过期两种回收、已使用保护、用券先提交后的条件删除拒绝、两旧快照回收只减一次及后置计数异常的事务回滚。执行实际H2 Mapper和Spring事务，供应商/RBAC输入为替身；不替代真实退款HTTP与MySQL验收。


## 2026-11-04 V030真实迁移及六项审查问题联合回归

在归属MySQL9.7.1:13316执行 `python script/jss/verify_native_schema_repairs.py`，12项全过，/tmp/server-fix-native-schema-final.log、忽略目录.runtime/jss-mysql/native-schema-repairs.json。仅复制V019/V026/V027中的三表原DDL到随机库，测试baseline29后经真实Flyway应用V030；修复前缺列、每个旧字段/大Long/审计保留、四列类型与安全默认值、新值往返和重复迁移通过。`python script/jss/verify_migrations.py` 26项全过，/tmp/server-fix-verify_migrations-final.log，包含完整fresh/升级/校验和保护。原V017专项曾错误调用全量升级后断言17，现固定演练到17，新增V029→最新版本验证。POSIX专用Unix socket实连通过；随机库残留0，测试实例关闭，已有MySQL未操作。

最终联合命令：`mvn -o -B -ntp -Pjss-mall -pl yudao-server -am test -Dtest=Gym*Test,Jss*Test,SmsCodeServiceImplTest,MemberRegistrationBoundaryTest,MemberUserRegistrationTest,CouponServiceImplTest,CouponTemplateServiceImplTest,TradeCouponPriceCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，沿用显式Mockito5.23.0 javaagent。新鲜报告73类、1141项全部通过，0失败/错误/跳过，/tmp/server-review-fixes-regression.log。`mvn -o -B -ntp -Pjss-mall -pl yudao-server -am package -DskipTests -Dspotless.skip=true`全45模块成功；该命令跳过测试，/tmp/server-review-fixes-package.log。

587个实体使用实际MyBatis元数据与JSS/Gym迁移列比对已无缺列；三个旧迁移文件与HEAD字节相同。Python AST、根Spotless检查和差异检查通过。以上不登记为真实认证HTTP/Redis、业务库升级或提供商验收，也未部署运行jar。


## 2026-11-04 独立演进前 Modbus 参数校验补丁

从上游 JDK8 分支修复 `f0b321cd3ca72f5f3bf96cb4531527f87c4e38b4` 移植业务错误码和九项回归，保留当前 JDK25/jakarta 实现。先补错误码、保持旧 Service 运行同一测试，9项中6项失败（实际 IllegalArgumentException，预期 ServiceException），日志 `/tmp/server-independent-modbus-baseline.log`；修复后执行 `mvn -o -B -ntp -Pjss-mall -pl yudao-module-iot/yudao-module-iot-biz -am test '-Dtest=Iot*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.skip=true`，显式 Mockito5.23.0 javaagent同前，48类472项、467通过/5项既有跳过、零失败/错误，其中新增9项全过，日志 `/tmp/server-independent-iot-final.log`。根 Spotless apply/check、git diff --check通过。本次为服务单元/模块回归，没有真实设备或业务HTTP验收。


## 2026-11-04 最终上游与唯一 main 审计

已解开浅克隆并完整获取历史，`git merge-base --is-ancestor 947f986e0fc605538caa668b243910fd3320c3d5 main`成功，`main..upstream/master-jdk25`为0。远程实际分支名为master-jdk25，master-sdk25不存在。11条origin分支中main保留，8条非main已是main祖先，另2条为旧JDK8系列并存入本地archive refs及已验证完整Git bundle；详见upstream-audit-2026-11-04.json逐分支SHA与处置。

Modbus修复后 `mvn -o -B -ntp -Pjss-mall -pl yudao-server -am package -DskipTests -Dspotless.skip=true` 全45模块成功，日志 `/tmp/server-independent-package.log`；测试证据见上一节。Maven工作流YAML经本机Ruby/Psych解析验证，push/PR均指向main、contents只读。未将本地构建记为GitHub Actions已执行；最终远程变更使用逐ref SHA保护及atomic push，完成后另行查询Git与GitHub分支接口核验。


## 2026-11-04 MoneyUtils 旧分支补丁与远程清理复核

移植旧分支 `2cc5c86cd2009d93947d8776526d2f7676682c87` 的小数百分比修复。新MoneyUtilsTest七项先运行旧代码，三项实际失败：1204→1200、1→0、198→197；修复后common模块完整71项全部通过，零失败/错误/跳过。命令 `mvn -o -B -ntp -pl yudao-framework/yudao-common -am test -Dspotless.skip=true`，日志 `/tmp/server-independent-money-baseline.log`、`/tmp/server-independent-common-final.log`；根Spotless apply/check及差异检查通过。

首次原子推送至696675359272ce468ff7c5a1e3518b6c7b4d19d8同时删除十条分支成功，Git ls-remote --symref、GitHub branches与repository API确认远程分支仅main、默认main；本地旧分支清理后branch --list仅main，archive refs及bundle仍保留。GitHub Maven CI已实际触发，首次运行41825188209；这里只记录触发，不将进行中的CI记为通过。fork关联仍为true，未执行脱离。后续补丁沿main正常快进。

## 2026-11-05：开发种子关联与授权

独立 MySQL 8.4.10 上运行 `script/gym/verify-migrations.py`：266 项检查通过，随机测试库已清理。验证双门店部门 ID、两账号员工绑定、三名员工任职、两名潜客门店关系、重复执行连时间戳都不变、业务键冲突回滚、普通 seed 不恢复撤权、显式同步仅恢复演示角色权限。正式历史 SQL 校验和不变。

## 2026-11-05：全新数据库及重建

MySQL 8.4.10 上 `script/gym/verify-bootstrap.py` 通过：随机带连字符数据库从不存在状态初始化到 695 张表，两套 Flyway 历史均有效，3 个开发账号、2 家门店、3 名员工、2 名潜客，无旧云配置/令牌/日志/交易；重复普通初始化拒绝且保留标记，显式重建先生成权限 600 的完整备份，再重建为同样的初始数据。临时数据库已清理。

## 2026-11-05：dashboard 全业务 HTTP 与浏览器

当前默认 JAR + 从空库建立的 MySQL/Redis：`verify-dashboard-api.py` 17 组全部通过，共 686 项断言；覆盖会员、薪资、卡/课程、审批模板/开卡与改价/预约预占取消签到扣次、合同模板与实例、工资、售卡核销设置、收款分账、营销、设备、文件、展示/二维码、交易拒绝边界和两类报表。真实渠道未配置，测试断言未发送/未绑定/无假交易。证据目录 `/tmp/jss-dashboard-http-9ejlc4p8`，只含隔离开发数据。

`mvn -B -ntp -T 2 -pl yudao-module-gym -am test -Dtest='Gym*Test' -Dsurefire.failIfNoSpecifiedTests=false`：1066 测试通过，无失败、错误或跳过。dashboard 的 35 页面逐页真实浏览器读取、工作台快捷入口、multipart 上传/授权下载字节一致均通过；41 前端测试和定向类型检查通过。OpenAPI 232 个管理业务操作，前端解析 127 个无缺失，376 字段名无差异。

## 2026-11-05：本地重建交付状态

已将原 118 表开发库完整备份到 `.runtime/backups/ruoyi-vue-pro-before-flyway-20261105-203708-615397.sql`（600 权限），通过已验证 bootstrap --recreate 重建为 695 表、Gym 35 版及 JSS 30 版迁移。当前默认 JAR 运行于 48080，使用 Redis database 10；dashboard 5666 对该实例的 35 页面、快捷入口、multipart 上传/授权下载验证通过。旧数据库已替换，备份保留。

`seed-catalog.py` 在独立环境与本地新库各执行两次，第二次 created=[]，开卡待办 ID 保持 1。基础组织、会员与目录样例可使用；未写成功支付、硬件执行、签名、扣次或收入。
