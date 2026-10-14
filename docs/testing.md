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
