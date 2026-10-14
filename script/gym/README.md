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
