# gym 后端切片（§4.1 认证 5 + 文件 3）

> 当前新增首批门店/员工及两个页面菜单，见 [首批 API 交接](../../docs/gym-first-batch-api.md)、`gym-organization.http`、`verify-migrations.py`。以下认证/文件内容为此前切片记录；本次元数据补充 originalName/byteSize，ID 改为字符串。

> 范围：`admin/target/plan/10-backend-mock-api-inventory.md` **§4.1 认证、用户与文件** 全部 8 行。
> 契约：`admin/target/plan/08-gym-api-contract.md`（`/admin-api/gym/*` + `gym:*` 权限码）、`09-permissions.md`。
> 权责：**仅后端**（本仓库 yudao-server）；前端（`GitHub/admin`）由前端负责人处理，本切片不改动前端。

## 1. 接口对照（旧 Mock → yudao）

| # | §4.1 旧 Mock | 本仓库实现 | 权限码 | 实现位置 |
| --- | --- | --- | --- | --- |
| 1 | `POST /api/auth/login` | `POST /admin-api/system/auth/login` | — | yudao 原生 `AuthController` |
| 2 | `POST /api/auth/logout` | `POST /admin-api/system/auth/logout` | token | yudao 原生 |
| 3 | `POST /api/auth/refresh` | `POST /admin-api/system/auth/refresh-token` | token | yudao 原生 |
| 4 | `GET /api/auth/codes` | `GET /admin-api/system/auth/get-permission-info` | token | yudao 原生 |
| 5 | `GET /api/user/info` | `GET /admin-api/system/user/profile/get` | token | yudao 原生 |
| 6 | `POST /api/admin/v1/files` | `POST /admin-api/gym/files`（`/upload` 等价） | `gym:files:upload` | **本模块新增** |
| 7 | `GET /api/admin/v1/files/{id}` | `GET /admin-api/gym/files/{id}` | `gym:files:read` | **本模块新增** |
| 8 | `GET /api/admin/v1/files/{id}/content` | `GET /admin-api/gym/files/{id}/content` | `gym:files:read` | **本模块新增** |

- 前 5 个由 yudao 基座提供，本切片的工作是**打通 + 落 gym 权限数据**（详见 §4、§5）。
- 后 3 个是新写的 Java：`yudao-module-gym`（见 §3 代码地图）。

## 2. 交付物

```
yudao-module-gym/                       # 新建 maven 模块（已注册进根 pom 与 yudao-server）
├── pom.xml
└── src/
    ├── main/java/cn/iocoder/yudao/module/gym/
    │   ├── controller/admin/file/GymFileController.java      # 3 个文件接口
    │   ├── controller/admin/file/vo/GymFileUploadReqVO.java  # multipart 入参
    │   ├── controller/admin/file/vo/GymFileRespVO.java       # 出参（不含存储 url）
    │   ├── service/file/GymFileService.java                  # 接口
    │   ├── service/file/GymFileServiceImpl.java              # 校验 + 越权 + 落库
    │   ├── service/file/dto/GymFileContentDTO.java           # 内容流返回
    │   ├── dal/dataobject/file/GymFileDO.java                # TenantBaseDO，自动租户隔离
    │   ├── dal/mysql/file/GymFileMapper.java                 # BaseMapperX + 扩展示例注释
    │   ├── enums/ErrorCodeConstants.java                     # 错误码区间 1_070_000_xxx
    │   ├── enums/file/GymFileCategoryEnum.java               # 5 类别 + 大小上限 + 类型白名单
    │   ├── enums/file/GymFileMimeTypeEnum.java               # 支持的 MIME（7 种）
    │   ├── framework/file/core/utils/GymFileTypeUtils.java   # 魔数嗅探 + 附件输出
    │   └── package-info.java
    └── test/java/cn/iocoder/yudao/module/gym/
        ├── framework/file/core/utils/GymFileTypeUtilsTest.java   # 5 用例
        └── service/file/GymFileServiceImplTest.java             # 10 用例

yudao-module-infra/                     # 跨模块 API 扩展（供 gym 复用文件存储）
├── api/file/FileApi.java               # + getFileContentByUrl(url)
├── api/file/FileApiImpl.java           # + 实现
├── service/file/FileService(.Impl)     # + getFileByUrl(url)
└── dal/mysql/file/FileMapper.java      # + selectByUrl(url)

script/gym/
├── README.md                      # 本文件
├── gym-auth.http                  # IDEA：认证 5 接口
├── gym-files.http                 # IDEA：文件 3 接口（含反例）
├── seed.sh                        # 执行 sql/ 下全部脚本（幂等）
├── verify-auth-api.sh             # 认证接口验证（26 条断言）
├── verify-file-api.sh             # 文件接口验证（29 条断言）
└── sql/
    ├── 01-gym-permission-seed.sql # 51 权限码 + 角色 + 演示账号 + 门店部门
    └── 02-gym-file.sql            # gym_file 建表

配置改动：
- yudao-server/src/main/resources/application-local.yaml   # 数据库账号（本机 root 为免密 socket，JDBC 连不上）
- yudao-server/src/main/resources/application.yaml         # multipart 上限 16MB → 200MB（video 类别需要）
- pom.xml / yudao-server/pom.xml                           # 注册 gym 模块
```

## 3. 文件接口契约

### 3.1 上传 `POST /admin-api/gym/files`（等价 `/upload`）

`multipart/form-data`：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 文件内容 |
| `category` | 是 | `approval` / `body_report` / `contract` / `image` / `video` |
| `storeId` | 否 | 门店编号（`system_dept.id`）；不传则取上传人所属部门 |

类别规则（`GymFileCategoryEnum`）：

| 类别 | 中文 | 大小上限 | 允许类型（以魔数为准） |
| --- | --- | --- | --- |
| `approval` | 审批附件 | 20MB | PDF / PNG / JPEG / GIF / WEBP |
| `body_report` | 体测报告 | 20MB | PDF / PNG / JPEG |
| `contract` | 合同文件 | 20MB | PDF |
| `image` | 图片素材 | 10MB | PNG / JPEG / GIF / WEBP |
| `video` | 视频素材 | 200MB | MP4 / MOV |

响应 `CommonResult<GymFileRespVO>`：`id / storeId / category / name / mimeType / size / uploaderId / createTime`。
**不返回** infra 存储 url（该 url 指向 infra 的 `@PermitAll` 下载地址，泄露即绕过越权校验）。

### 3.2 元数据 `GET /admin-api/gym/files/{id}`

返回同上 VO；文件不存在 → `code=1070000001`；跨门店 → `code=1070000007`。

### 3.3 内容流 `GET /admin-api/gym/files/{id}/content`

返回二进制内容（`Content-Type` 用嗅探结果，图片/视频 `inline`、其余 `attachment`，支持中文文件名 RFC 5987 编码）。
鉴权与越权同样生效；文件记录或内容缺失返回 **HTTP 404**（body 分别保留 `code=1070000001` / `1070000010`），跨店或操作权限拒绝返回 **HTTP 403**，非法 ID 返回 HTTP 400，存储异常返回 HTTP 500。错误体仍为 `CommonResult` JSON，Blob 客户端应按 HTTP 非成功状态拒绝读取。

### 3.4 错误码

| code | 含义 | 旧 Mock 对应 |
| --- | --- | --- |
| `1070000001` | 文件不存在 | HTTP 404 |
| `1070000002` | 文件类别不正确 | HTTP 400 |
| `1070000003` | 类型与内容不匹配 / 类别不允许该类型 | HTTP 415 |
| `1070000004` | 超出类别大小上限 | HTTP 400/413 |
| `1070000005` | 文件内容为空 | HTTP 400 |
| `1070000006` | 门店（部门）不存在 | HTTP 400 |
| `1070000007` | 无权访问该门店的文件 | 旧 Mock 越权返回 404 |
| `1070000008` | 无法确定门店（账号无部门且未传 storeId） | — |
| `1070000009` | 文件保存失败 | HTTP 500 |
| `1070000010` | 文件内容不存在（已被清理） | HTTP 404「模拟文件已重置」 |

> yudao 统一 HTTP 200 + body `code`（`08-gym-api-contract.md` §1 的响应包）；旧 Mock 用 HTTP 状态码表达。
> 唯一例外：内容流的 HTTP 状态仍为 200/真实状态，便于前端 blob 处理。

## 4. 权限与数据范围

| 角色 | 数据范围（`system_role.data_scope`） | 文件可见性 |
| --- | --- | --- |
| `gym_hq_admin` 健身总部管理员 | 1 = 全部 | 全部门店文件 |
| `gym_store_manager` 门店店长 | 4 = 本部门及以下 | 仅本门店文件（16 个仅总部权限码已收窄） |

实现：`GymFileServiceImpl#validateStorePermission` 调 `PermissionApi.getDeptDataPermission(userId)`
（`all=true` 放行，否则 `storeId` 必须命中 `deptIds`），越权抛 `1070000007`。

## 5. 使用步骤

```bash
# 1) 编译（含 gym 模块）
mvn -T 1C -DskipTests -pl yudao-server -am package

# 2) 启动
java -jar yudao-server/target/yudao-server.jar

# 3) 初始化数据库（建表 + 权限码 + 演示账号，幂等）
bash script/gym/seed.sh

# 4) 验证
bash script/gym/verify-auth-api.sh    # 26 条断言
bash script/gym/verify-file-api.sh    # 29 条断言
```

### 查看效果

| 方式 | 入口 |
| --- | --- |
| 接口文档（推荐） | <http://127.0.0.1:48080/doc.html> → 搜索 `gym`；调试时「全局参数」加 `tenant-id: 1` |
| OpenAPI JSON | <http://127.0.0.1:48080/v3/api-docs> |
| IDEA 一键执行 | `script/gym/gym-auth.http`、`script/gym/gym-files.http` |
| 命令行 | 上面两个 verify 脚本 |

演示账号：`gymadmin / Gym@123456`（总部，51 权限码）、`gymstore / Gym@123456`（门店，35 权限码）。

## 6. 如何新增一个 gym 接口（照着这张清单做）

以「门店 `stores` 资源」为例（R29/R37）：

1. **权限码**：在 `09-permissions.md` §2 找到/新增码（如 `gym:stores:write`），
   在 `script/gym/sql/01-gym-permission-seed.sql` 的权限表里加一行，重跑 `seed.sh`（幂等）。
2. **建表**：新增 `script/gym/sql/03-gym-store.sql`（照 `02-gym-file.sql` 抄，注意 `tenant_id` + `deleted`），
   `seed.sh` 会自动按文件名顺序执行。
3. **DO**：`dal/dataobject/store/GymStoreDO.java`，继承 `TenantBaseDO`（多租户）或 `BaseDO`（全局表）。
4. **Mapper**：`dal/mysql/store/GymStoreMapper.java`，`extends BaseMapperX<GymStoreDO>`，
   分页写法见 `GymFileMapper` 注释里的示例。
5. **VO**：`controller/admin/store/vo/` 下建 `GymStorePageReqVO`（继承 `PageParam`）、`GymStoreSaveReqVO`、`GymStoreRespVO`。
6. **Service**：`service/store/GymStoreService(.Impl)`；门店越权校验直接复用
   `GymFileServiceImpl#validateStorePermission` 的写法（建议后续抽成 `GymScopeUtils`）。
7. **Controller**：`controller/admin/store/GymStoreController.java`，按 `08-gym-api-contract.md` §2 标准 CRUD：
   `GET /gym/stores/page`、`GET /gym/stores/get?id=`、`POST /gym/stores/create`、
   `PUT /gym/stores/update`、`DELETE /gym/stores/delete?id=`；每个方法加
   `@PreAuthorize("@ss.hasPermission('gym:stores:read')")`。
8. **测试**：`src/test/java/.../service/store/GymStoreServiceImplTest.java`，继承 `BaseMockitoUnitTest`，
   参照 `GymFileServiceImplTest`（静态工具用 `MockedStatic`，断言异常用 `AssertUtils.assertServiceException`）。
9. **验证脚本**：加 `script/gym/verify-store-api.sh`（照 `verify-file-api.sh` 抄），并在本 README §1 表格登记接口。

## 7. 已知差异与待决（相对旧 Mock / 前端契约）

| 项 | 旧 Mock | 现状 | 处理建议 |
| --- | --- | --- | --- |
| 多租户请求头 | 无 | 必须带 `tenant-id`，否则 `code=400 请求的租户标识未传递` | 单租户部署可关 `yudao.tenant.enable`；否则前端补请求头 |
| HTTP 状态码 | 201/415/404 | HTTP 200 + body `code` | 前端按 body `code` 判断 |
| refresh 轮换 | 旧 refresh 立即吊销 | 有效期内不变、可重复使用 | 需一次性轮换要改造 `OAuth2RefreshTokenService` |
| 文件存储 | 内存，重启即失效 | infra 文件服务（本地磁盘/DB/S3 按配置） | 无需处理，属改进 |
| 越权语义 | 404 | `1070000007`（403 语义） | 前端按 code 提示 |
| `homePath` / `authorizedStores` | `/api/user/info` 返回 | 原生接口不返回 | 前端适配层推导，或后续加 gym 专属 profile 接口 |
| 请求体加密 | — | `yudao.api-encrypt.enable=false` | 上线按需开启 |

## 8. 验证记录（本机实测）

```
$ mvn -T 1C -pl yudao-module-gym -am test -Dtest='GymFile*Test'
 Tests run: 15, Failures: 0, Errors: 0, Skipped: 0        # GymFileTypeUtilsTest 5 + GymFileServiceImplTest 10

$ bash script/gym/verify-auth-api.sh
 结果：通过 26 / 失败 0

$ bash script/gym/verify-file-api.sh
 结果：通过 29 / 失败 0
```

文件接口验证覆盖：上传（图片/合同/视频）、元数据、内容流逐字节一致、Content-Type、
PDF 伪装 `.png` 被拒、类别白名单、11MB 超限、非法类别、跨门店读元数据与内容被拒、
门店账号读本店（正例）、总部跨门店读（正例）、未登录 401、文件不存在。

## 开发种子转换（2026-10-14）

旧 PostgreSQL 种子已按已实现模块转换并导入本机 MySQL 开发库，见 [导入报告及待导入清单](../../docs/development-seed-import.md)。显式导入文件为 `development/01-legacy-organization.sql`，不会由常规 `seed.sh` 自动加载。隔离验证用 `python3 script/gym/verify-legacy-seeds.py`；包含新建/清理独立随机验证库，不修改业务库。

## 本轮卡权益、私教、审批模板增量

在既有01–10基础上顺序执行11–17；业务库只增量建表、回填成员关系和添加已实现菜单，不清理既有数据。审批模板菜单仅授权示范总部角色；不扩展导入开发角色。

启动新版服务后，使用安全环境提供GYM_TEST_USERNAME/GYM_TEST_PASSWORD，执行verify-card-course-api.py及verify-approval-template-api.py。脚本保留带HTTP联调名称资料供浏览器核查，关闭其测试会话但不删除业务资料。前者46断言、后者22断言已通过；有权益全链路须等待审批记录真实开卡接入，不以当前脚本代替该项。verify-migrations.py在随机隔离库二轮执行所有脚本并删除临时库，当前21表/14页面/26示范角色页面授权。

## 审批业务联调（SQL18–19）

verify-approval-flow-api.py从环境GYM_TEST_PASSWORD与GYM_APPROVER_PASSWORD读取密码，默认admin和既有gymstore账号，可用GYM_APPROVER_USERNAME切换。脚本为该账号所属部门补业务门店和关联员工（若缺少），不改账号部门/角色/权限；每轮独立教练用于可重复课程验证。保留带HTTP名称业务资料，额外通卡新店结束时停用，脚本关闭自己创建的登录会话。实际52断言通过；首次store2缺审批权限被拒绝，未扩展该账号权限。

## 合同模板增量（SQL20–21）

verify-contract-template-api.py使用GYM_TEST_PASSWORD和GYM_APPROVER_PASSWORD提供凭证，默认admin和gymstore，真实multipart上传测试PDF并验证共享引用权限、版本冻结与下载字节。已通过26断言，保留测试PDF和模板资料；前端共用上传方法存在JSON化FormData阻塞，具体见docs/gym-full-api-progress.md，不把HTTP成功当成浏览器上传成功。

## 合同实例与提醒（SQL22–23）

verify-contract-flow-api.py使用环境GYM_TEST_PASSWORD和GYM_APPROVER_PASSWORD提供admin/gymstore凭证，需要已建立HTTPAPPROVER示范员工（verify-approval-flow-api.py可初始化该业务资料，不改角色）。脚本真实上传PDF，发卡自动生成实例、审核签约授权和8并发提醒；未调用实际电子签/短信。36断言已通过，保留合同/文件/失败提醒资料及/tmp/gym-contract-flow-fixture.json（无凭证）。


## 工资表

按顺序应用SQL24/25。GET/POST /gym/payrolls，GET/PATCH /gym/payrolls/{id}，POST /gym/payrolls/{id}/calculations；分别要求gym:payroll:read/calculate/confirm。整月唯一已发布配置，本人所有任职门店实际业绩归唯一发薪店；确认冻结且保护薪资配置覆盖期间。内部财务事件待后续已验证支付/退款写入，无公开任意造流水接口。

开发验证：`GYM_TEST_PASSWORD='<开发密码>' python3 script/gym/verify-payroll-api.py`（24断言），浏览器确认测试工资后加 `--verify-confirmed`（10断言）。保留明确标记的停用开发门店，fixture写/tmp/gym-payroll-flow-fixture.json，无凭证。


## 经营报表

SQL26发布home、reports/performance、reports/members三页。三个GET均需gym:reports:read及授权门店范围；默认报表全部历史，日期/from/to/月显式过滤；看板默认days=7，可选30，最大366。报表范围左闭右开，日期为北京时间。真实只读验证：`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-report-api.py`，51断言。会员卡售出和实际支付链路仍待补齐。


## 售卡核销设置

顺序应用SQL27/28。GET/PUT /gym/stores/{id}/online-sales-settings、subscription-settings、verification-settings均需gym:sales:settings与门店范围。PUT完整替换，重复相同内容不增加revision。在线售卖时段成对UTC Instant；核销HH:mm按门店北京时间同日范围；包月卡为已发布28至31天期限卡。单店设置revision当前不作为前端写入条件，最后成功PUT生效。`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-sales-settings-api.py`：38断言，fixture /tmp/gym-sales-settings-fixture.json，无凭证。配置不代表第三方渠道就绪或产生付款。


## 收款与分账接口

按顺序应用SQL29/30。payment-accounts列表/详情/创建/编辑/POST{id}/bindings要求gym:settlement:manage；stores/{id}/settlement-rules列表/新增、settlement-rules/{id}详情/编辑/启用同权限。settlements列表/详情要求gym:settlement:read。现有示范角色仅总部获三页菜单，不扩展原门店角色。

创建账户仅accountDetails.lastFour四位、credentialRef安全引用；不能提交完整账号或原始证书/密钥。引用格式vault://、secret://、kms://，可省略，编辑显式null清除。绑定未配置如实返回unconfigured及原因；不能由客户端改bound。规则启用要求真实bound，非回溯新版本关闭旧区间；真实渠道和结算记录生产者未接入，禁止用测试数据冒充到账。

开发HTTP验证：`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-settlement-api.py`。创建标记的停用门店与草稿，fixture /tmp/gym-settlement-fixture.json，无凭证、不划付。


## 活动与加盟

应用SQL31/32。activities列表/详情/POST/PATCH及{活动id}/registrations的GET/POST/DELETE{报名id}需gym:activities:manage；DELETE为取消状态，不物理删除。名单按门店过滤，共享活动修改须全覆盖，名额在全部门店共享。franchise-inquiries后台列表/详情/创建/处理需gym:franchise:manage和总部ALL范围。

匿名 POST /app-api/gym/franchise-inquiries：tenant-id、Idempotency-Key必需，body为contactName/phone/preferredCity/message；响应仅id/received，无公开详情。每租户/实际TCP来源60秒5次，不能靠变化正文或伪造转发头绕过。代理下同传输来源共享配额，终端分流由可信部署入口安排。幂等键8至128位字母数字及_.:-，相同键不同正文拒绝。HTTP验证脚本verify-marketing-api.py，fixture /tmp/gym-marketing-fixture.json；不发外部通知。


## 设备与器械

应用SQL33/34。devices列表/详情/commands列表要求gym:devices:read；设备创建编辑和PUT{id}/compartments要求gym:devices:write；POST{id}/commands要求gym:devices:command及Idempotency-Key。柜格完整替换，但历史编号保留；占用/预留不允许后台清除。无厂商适配时返回并保存failed，online默认false且lastSeenAt为空，不能直接PATCH在线信息。

equipment列表/详情及{id}/tutorials列表要求gym:equipment:read；器械登记编辑、教学创建/启停要求gym:equipment:write。教学fileId必需，限定同店受控图片或MP4，不把文字空教学当成媒体上传成功。开发验证：`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' GYM_TUTORIAL_FILE_ID='<已有媒体ID>' python3 script/gym/verify-device-api.py`。fixture /tmp/gym-device-fixture.json；未发送硬件指令，媒体内容可读性与元数据验证单列。


## 本地独立文件存储

构建后用 `bash script/gym/run-local.sh --logging.file.name=/tmp/gym-server.log` 启动。默认gym.file.storage-config-id=4（现有示例数据库存储），可通过GYM_FILE_STORAGE_CONFIG_ID覆盖；全局infra master22不改，其他模块及旧S3文件不变。普通java启动不带此属性时仍沿用全局默认，当前示例云账号会403。新选项只由后端配置决定，不接受请求体存储ID。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-gym-storage-api.py`：18断言，上传真实PNG、受控字节下载、教学关联及启停。fixture /tmp/gym-storage-fixture.json与/tmp/gym-storage-real.png，无凭证。合同完整42断言在此模式已通过。原前端FormData头问题仍需前端团队处理，现有源码未改。


### 客户端内容与二维码（SQL35/36）

GET/PUT `/gym/client-settings` 使用gym:content:manage；platform为miniapp/app，storeId省略为总部独立草稿。PUT完整替换，支持明确清空，revision由后端维护，相同规范化内容不增版本。正文保留授权gym-file图片与受控文件预览链接，清洗危险标签/事件/CSS；封面/区块/正文同店校验，下载地址仅App平台HTTP(S)无用户凭据。总部默认不自动混入门店草稿。

GET列表/详情 `/gym/qr-codes` 要求gym:stores:read，POST要求gym:stores:write。课程场景必须关联同店真实排课；重复同一门店/平台/场景复用记录，不改创建时间。真实渠道尚未配置，返回failed、failureReason和空providerCodeFileId，TODO-INTEGRATION-CLIENT-CODE留待补齐。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-content-api.py`需先有verify-gym-storage-api.py生成的真实文件fixture。完整脚本44断言已重跑通过，另有独立MySQL配置并发6断言。保留明确测试配置和失败场景，无外部通知/发布。截图及前端图片异步回填问题见docs/testing.md。


### 订单、包月、核销记录读接口（SQL37/38）

GET `/gym/self-service-card-orders`及`/{id}`、`/gym/subscriptions`及`/{id}`、`/{id}/cycles`、`/gym/verifications`，均gym:sales:read。pageNo/pageSize、storeId/memberId/status/channel及北京时间date/month/from/to；核销支持type。自主办卡仅miniapp/app与card_sale，关键词匹配订单号/会员名，其他两者匹配会员名。协议周期时间按periodStart过滤，失败/未扣款chargedAt可为空。

七表迁移包含不可变商品快照及支付/退款/协议/周期/核销来源字段；管理端响应隐藏渠道身份和幂等键，无公开创建成功状态接口。可信业务写入器尚未实现，不把空表当交易链路验收。

`GYM_TEST_PASSWORD='<admin开发密码>' GYM_APPROVER_PASSWORD='<gymstore开发密码>' python3 script/gym/verify-sales-records-api.py`真实38断言通过，不写测试交易。当前列表均0；正向详情与金额/脱敏/租户/周期边界由H2业务测试覆盖，真实有数据详情/审批待后续写入链路联调。


### 内部交易服务（SQL39，无新增管理端写接口）

GymCommerceService.prepareCardOrder供后续绑定本人身份的购买流程调用，服务端在线报价/商品版本/合同与审批策略固定，幂等摘要保护重放，零元订单无支付流水。acceptPayment/acceptRefund调用GymPaymentReceiptVerifier重新核验渠道终态，写入结果与正额财务事件原子提交，检查CNY/租户/订单/时间/精度与分笔额度。

默认核验器未配置必定1070020003，不伪造支付/退款结果。没有通过公开管理API提交paid/refunded状态的方法；不提供匿名可指定tenantId/memberId的回调。生产适配器和外部购买/回调入口尚未接入，权益发放撤销/审批/自动履约继续补齐，不能据内部单测认定闭环交付。169完整Gym测试通过，运行库交易与财务数据仍0。


### 订单开卡审批与内部履约

POST `/gym/approvals` 的card_opening现在接受sourceType=orders/sourceId，不能同时传会员/卡模板/改价字段。服务端解析订单，足额已付且未退款才能进入来源；最后一步再次检查，履约按订单项/单位幂等，不重复发卡。直接会员+卡模板的原开卡方式保留。

GymCommerceService的内部批准履约与非审批履约方法使用下单冻结模板和规则；非审批方法不能越过approvalRequired。合同必需时生成真实实例原件，否则整步审批和发卡回滚，已入账支付不变。电子签仍未配置/unsigned。非审批方法尚未接支付后自动触发或持久重试。订单详情新增memberCardIds/fulfilledAt/approvalRequired/contractRequired，既有前端可忽略新增字段。

完整176项Gym测试，交易只读及订单来源拒绝HTTP42断言、原合同链路HTTP42断言回归通过。没有真实有数据orders来源前端验证，未配置真实支付；退款权益规则已询问待答。


### 退款权益规则与人工决定（SQL40）

用户已确认全额退款停卡，部分退款人工处理。可信refund处理与停卡/任务变更同事务；部分退款每笔建pending，full refund关闭尚pending任务。卡来源限定本订单项单位，历史次数/合同/交易不改；预留预约不自动跨店取消，停卡已阻止继续用卡。

GET `/gym/self-service-card-orders/{id}/refund-reviews`需sales:read。POST `/{id}/refund-reviews/{reviewId}/decisions`需sales:read和approvals:decide及门店范围；JSON为decision=retain/stop_selected、note必填、memberCardIds数组（retain为空，stop_selected必需非空且仅本订单卡）。同操作者、决定、说明和卡列表重复幂等；原决定不可覆盖。动作只处理权益，不请求渠道退款、不改款项。订单详情包含refundReviews。

181完整Gym测试通过，SQL40两次幂等、新构建及HTTP45断言通过。真实支付退款尚未配置；正向权益处理在H2测试，现有前端没有人工处理按钮，源码未改。


### 自动履约持久任务（SQL41）

非审批订单准备时建任务，足额付款后pending；零价直接pending但无支付/收入。后台定时扫描默认每30秒，独立线程gym-fulfillment-*，不依赖Quartz、不占默认任务池；使用租户和原发起后台账号的当前权限。过期/停用租户跳过，不修改有效期。服务重启后待办仍在，120秒过期租约可接管，8次自动尝试及退避上限1小时，超过预算等待有权人工重试。

配置gym.commerce.fulfillment.enabled=false仅停定时驱动；interval-ms默认30000且不小于1000，initial-delay-ms默认30000且不为负。调试可在run-local.sh后附`--logging.level.cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentScheduler=DEBUG`读取扫描数量，不输出凭据或交易明细。

GET `/gym/self-service-card-orders/{id}/fulfillment-task`与订单详情fulfillmentTask可查询；POST `/{id}/fulfillment-retries`需sales:read和approvals:decide，不接受body成功状态或账号ID。只允许失败、已足额付款/零元待履约且未取消/退款/履约、无需强制审批的订单；人工重试增加8次预算并保留累计次数。现有前端无重试按钮，源码未改。

人工发起开卡审批会暂停自动任务，已领取执行器也必须遵守暂停；请求失败一起回滚，批准后完成。拒绝的申请不触发自动发卡。默认厂商凭证核验仍未配置，生产没有伪造成功数据。


### 会员卡报表（SQL42/43）

GET `/gym/reports/member-cards`需gym:reports:read，沿用公共page/pageSize、storeId和日期筛选。结果id/name/version/soldCount/netAmount/holderCount/usageCount，汇总保持分页无关。先执行42-gym-item-financial-events.sql、43-gym-card-report-menu.sql；全量迁移验证当前56表、35业务页面、61示范页面授权。

净收来自可信付款减退款的订单项分摊，历史未分摊现金显示未归属卡种；销量是足额支付件数，退款不减毛销量，零价履约后计件不造现金。持卡人数为当前有效权益人数，课消为指定日期历史确认单位；按模板去重，不跨模板全局去重。人工开卡不会伪造销售金额。

提供既有测试账号密码环境变量GYM_TEST_PASSWORD、GYM_APPROVER_PASSWORD后运行`python3 script/gym/verify-card-report-api.py`，当前51断言通过，无业务数据新增。正向支付退款仅H2验证，真实渠道仍未配置。前端未修改。


### 连续包月来源签约审批

POST `/gym/approvals`支持businessType=contract、sourceType=subscriptions、sourceId=协议ID，使用已发布contract审批模板。门店、会员、金额从协议解析；不得提交memberId/cardTemplateId/requestedPrice替换。服务端复制发布card合同模板的原件并自动加到附件（总量不超过10），最终批准时复核协议未授权未解约及条款未变。合同批准不等于签署，更不等于代扣授权；没有付款或发卡副作用。没有新迁移/菜单，前端不变。

配置既有测试账号环境变量后verify-sales-records-api.py当前53断言、verify-contract-flow-api.py42断言通过；包月正向仅H2。运行库暂无协议，浏览器仅空态，不制造授权成功数据。厂商授权/周期/解约仍需后续对接。


### 合同自动未签提醒

默认gym.contract.reminders.enabled=true，interval-ms及initial-delay-ms均300000，channel=app；interval-ms最小1000、initial-delay-ms非负，渠道仅app/miniapp/sms。设enabled=false停扫描。按合同冻结的reminderDays及北京时间dueAt日期计算提前窗口，窗口内包括逾期每天同渠道最多一次，当前未配置渠道只记录failed/未发送。

沿用SQL22表，无新增迁移；人工reminders接口的Idempotency-Key不得以auto:开头。系统审计身份0不代表登录或提升权限。过期/停用租户、已签/取消合同、停用会员门店跳过。多个实例靠合同锁及持久唯一键去重。

本地可用run-local.sh附`--gym.contract.reminders.interval-ms=5000 --gym.contract.reminders.initial-delay-ms=1000 --logging.level.cn.iocoder.yudao.module.gym.service.contract.GymContractReminderScheduler=DEBUG`验证重复扫描。已有verify-contract-flow夹具且自动扫描运行后，设置两个既有测试账号密码环境变量运行`python3 script/gym/verify-automatic-reminders-api.py`，该脚本只读记录并验证人工占键拒绝，不发送通知。真实渠道仍TODO。


### 负责会员关系与SELF（SQL44）

先执行44-gym-member-coaches.sql，现57业务/支持表，35业务页面。GET/PUT `/gym/members/{id}/stores/{storeId}/coaches`需gym:members:write和原有门店范围；PUT JSON employeeIds数组最多20、不可重复，空数组撤销本店全部负责员工。须同店在职、关联启用同租户后台账号，会员须属该店。撤销active=false保留关系审计；接口未接新增前端按钮。

SELF(dataScope=5)仍需每个业务操作自身权限，不能维护负责关系或读整店。显式关系配合当前后台账号、任职、会员门店和状态决定可见会员；会员/体测/合同列表详情、卡包及预约/签到/签课内部业务已接入。通用课程/排期仍按部门范围，文件内容SELF授权尚待补齐。

用户已明确允许创建专用本地gymselfqa账号和gym_self_qa角色进行联调。设置GYM_TEST_PASSWORD及GYM_COACH_PASSWORD后运行`python3 script/gym/verify-member-coach-api.py`，当前47断言通过，保留user257/role175/employee9，负责member20/store125、contract6、measurement4；fixture路径/tmp/gym-member-coach-fixture.json不含凭证。只修改本脚本标识的专用角色/账号，遇其他角色混入或标识冲突会中止，不重置密码。已有管理员、门店账号和角色不变。

233完整Gym测试、既有会员HTTP28及卡/私教HTTP46通过。QA账号未另授预约写入权限，SELF预约正向仅H2。浏览器验证后恢复admin。重新构建时应停止旧Java进程再覆盖运行Jar，随后用run-local.sh启动；自动提醒现已恢复默认5分钟。


### SELF附件下载验证

GymMemberFileAccess按实际合同/体测绑定及当前负责会员授权，files:read配合contracts:read或measurements:read。未关联文件、同店其他会员、含不可见会员绑定的共享文件、纯SELF的NULL门店历史文件不开放；撤销关系后每次请求立即复核。体测修改可保留本会员本店已有报告，禁止借上传者相同重绑他人报告。黑名单列表也使用同一会员范围。

用户已另行明确允许专用gym_self_qa增加gym:files:read进行下载联调。提供GYM_TEST_PASSWORD及GYM_COACH_PASSWORD运行verify-member-coach-api.py后，再运行`python3 script/gym/verify-member-file-access-api.py`。本轮分别47及24断言通过，验证合同PDF和体测原件真实字节、同店他人403、原token撤销后403及恢复原负责集合。后者只在缺少报告时以管理员上传测试PDF并绑定已有QA体测，不增加SELF上传权，保留测试资料。

完整Gym测试240项及构建通过。本节取代上文“文件内容SELF授权尚待补齐”的下载部分；上传及课程选择仍另行审计。前端没有改动。


### 负责会员体测上传及路由覆盖审计

SELF上传仅新增body_report例外：同时有files:upload、measurements:write且目标店仍有有效负责关系。存储保存当前上传者，上传响应不授后续读取权；须由有权体测动作绑定到负责会员。其他类别/其他店/撤销后仍拒绝，管理员与部门范围兼容。无需新字段或SQL，旧multipart file/category/storeId不变。242项完整Gym测试及构建通过，专用QA上传权限未扩，SELF上传正向仅测试环境验证；浏览器上传仍有既有UPLOAD-JSON-001。

`python3 script/gym/verify-api-coverage.py`只读比较现行前端源码与运行OpenAPI，可选--dashboard-root（默认相邻dashboard仓库）、--openapi-url。当前147个管理端业务操作、127项提取/通用编辑器方法路径要求，missing为空。对动态通用编辑器采用显式资源表，相关占位在报告保留；此脚本只检查路由，不是业务验收，也不会创建数据。


### 训练档案（SQL45）及本人授课排期

执行45-gym-training-plans.sql；仍35业务菜单，58业务/支持表，无新角色授权。档案权限复用gym:measurements:read/write。

| 方法和路径 | 行为 |
| --- | --- |
| GET /gym/training-plans | pageNo/pageSize、storeId/memberId/status/keyword和既有时间筛选 |
| GET /gym/training-plans/{id} | 按会员门店授权的详情及审计 |
| POST /gym/training-plans | memberId/storeId/title，content或fileId至少一项；status默认active |
| PATCH /gym/training-plans/{id} | 修改title/content/fileId/status，会员门店不可变；fileId:null移除，省略保留 |

status仅active/archived；title非空最多120，content纯文本最多20000，附件和内容不能同时为空。错误1070021001不存在、1070021002非法计划/身份变更、1070021003附件非法，原门店无权1070001002。归档不硬删，不提供历史抹除操作。返回ID均字符串，含memberName/storeName、creator/updater/createTime/updateTime，不含存储URL。

文件沿用POST /gym/files multipart，category新增training_plan（20MiB，PDF/PNG/JPEG）；需files:upload，SELF另须档案写及当前负责门店。实际训练计划绑定后，files:read+档案read按具体负责会员可读；未绑定、同店他人及共享到不可见会员的附件拒绝，撤销即时生效。SELF关联附件限定本人上传或同会员同店已有绑定，禁止重绑他人附件。前端没有新增训练计划页面/按钮，原JSON上传问题仍留待前端修复。

用户已确认SELF预约只选本人授课。课程产品、排期、教练选项及读取详情使用本人employee+负责store范围与原部门范围并集；预约创建再次校验，不可猜另一教练ID绕过。创建产品/修改排期仍需原门店管理范围，整个门店不因负责关系而开放。负责关系现在还检查北京时间入职/门店任职日期已到。

验证：完整Gym248项及构建通过；隔离迁移58表/35页面/61授权双轮通过。verify-training-plan-api.py需管理员/既有门店账号密码环境变量，30断言；verify-self-course-selection-api.py需管理员/已授权SELF账号密码环境变量，29断言。两者保留标记QA资料，不修改账号/角色权限；后者精确恢复负责员工集合。SELF创建预约正向仅H2，真实QA未额外授写权。


SELF选课验证首次创建为29断言，再次运行校验并复用标记产品和未来排期为31断言，已实际通过；避免重复创建同一时段。遇标识或授课身份不匹配会中止，不覆盖其他业务资料。


### 私有存储命名空间与原始URL回归

yudao-server/application.yaml配置`yudao.file.private-path-prefixes: gym/`，全部gym文件原始infra URL直接403，登录管理员也必须走业务内容接口。路径解码后校验相对路径，防止别名/目录穿越。私有目录后端上传仅允许DBFileClient；私有直传预签名和外部URL登记拒绝。错误1001003005需专用DB存储，1001003006须业务后端上传。非gym公开目录兼容，当前专用存储配置4/全局master22不变。部署时须保留gym/私有前缀及正确数据库存储配置。

已存在合同、会员附件和训练夹具后，设置GYM_TEST_PASSWORD，以及可选GYM_TEST_DB_HOST/PORT/USER/PASSWORD/NAME（默认本地root/ruoyi-vue-pro），运行`python3 script/gym/verify-private-file-url-api.py`。脚本只读查询已知测试ID的原始URL、不打印地址或凭证，只请求同源本地数据库存储；当前18断言通过。验证匿名/管理员原始URL403、编码路径拒绝，业务鉴权PDF仍正常，以及私有预签名/登记拒绝，无业务写入。

本轮Gym248+infra文件44=292项相关回归通过；SELF附件24、存储18通过。历史18条S3附件的外部对象访问不受本地下载路由控制，需在原存储侧处理或拿原件重新导入；当前没有执行此类迁移/权限变更。


通用admin/app `/infra/file/upload`也禁止向gym/写入（目录恰为gym同样拒绝），须走业务后端上传。最新原始URL脚本为19断言，新增通用admin multipart上传负例；App分支单测覆盖。相关回归最新293项（Gym248+infra45）通过，替代上文292/18的最新口径；历史证据保留。


### SELF改价审批及字段审计

SQL46新增source_coach_id/索引并安全回填历史同店同租户快照；部署前执行，verify-migrations.py覆盖坏JSON/错租户/错店/既有元数据的幂等回填。

仅在用户明确批准测试角色审批read/create后运行authorize-self-price-qa.py（GYM_TEST_PASSWORD），校验专用角色/账号标记、dataScope和唯一角色绑定，合并两权限及目录祖先，保留已有权限。用户本轮已批准并完成，勿给其他角色复用。verify-member-coach-api.py策展权限集合同步保留这两项已授权权限，不增加decide或上传。

verify-self-price-approval-api.py需GYM_TEST_PASSWORD/GYM_APPROVER_PASSWORD/GYM_COACH_PASSWORD及原会员负责/本人选课夹具，仅修改HTTP-SELF-SELECTION产品（当前product9），39断言通过并保留资料。角色无审批决定权，既有gymstore审核；脚本精确恢复原负责集合并退出令牌。首次失败轮次的模板及管理员负例仍保留。

verify-frontend-fields.py只读dashboard目录和实时OpenAPI，可用--dashboard-root/--openapi-url指定。31静态页及4自定义页共376字段/路径当前通过，手工适配路径明示，不能代替完整业务或UI验证。前端SELF发起审批门店下拉受整店stores数据范围限制，留FRONTEND-SELF-APPROVAL-STORE-001，不扩大权限或改前端。最新相关测试298项、构建及39项SELF HTTP通过。


### 最终review补充（2026-10-15）

verify-coach-lifecycle-api.py使用GYM_TEST_PASSWORD/GYM_APPROVER_PASSWORD，独立创建HTTP-COACH-LIFECYCLE资料；27断言通过，停用/未来任职拒绝旧排期新预约，店长并发取消释放次数卡预占且不扣余额。H2另覆盖任职移除及私教包取消。完整相关回归301项（Gym256+infra45）通过，最终审批完整流程52项通过。

SQL47修正旧gym:blacklists:write与现行gym:blacklist:write不一致。只登记按钮并为持旧码/全范围的tenant1示范总部补映射，保留旧码；门店/SELF和已撤旧权者不补发。用户本轮已明确批准应用并保留，已在运行库执行，结果1/0/0。其他环境由其授权流程确认分配。完整迁移双轮与授权负例通过，58表/35业务页/61页面授权不变。

GYM_TEST_PASSWORD运行verify-controller-permissions.py只读源代码方法/类PreAuthorize及启用菜单，当前22控制器/150方法/45权限齐；当前注解布局静态扫描，不代替业务权限测试。不写权限或角色。最终route/field审计151管理操作、127调用和376字段无缺失。

上文SELF通用审批store选项限制仍在，但本人课程产品“申请改价”入口自动带入门店/来源；已实证浏览器提交→gymstore审批→product9价格150，无前端修改。admin黑名单新增/修改/撤销/刷新也已通过。完整交付映射与已授权暂缓项见docs/gym-full-api-progress.md文末。
