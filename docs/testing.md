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
