# 贡献指南

本仓库负责 Java 后端。开始修改前先阅读 [实现记录规范](docs/README.md)、[架构说明](docs/architecture.md) 和相关 API 契约；gym 脚本入口见 [script/gym/README.md](script/gym/README.md)。前端由独立项目维护，菜单分类、权限及数据接口需求在后端实现；涉及前端契约调整时明确记录并协调。

## 开发环境与工作流程

使用 JDK 25、Maven 3.9、MySQL 和 Redis；数据库管理脚本另需 Python 3、MySQL 客户端和 `mysqldump`。版本及配置以根 `pom.xml` 和应用配置为准。

1. 先查看 `git status`，保留已有工作，再同步目标分支的最新变更。
2. 按需求创建主题分支，例如 `codex/member-external-reference`；保持改动聚焦，避免夹带无关重构。
3. 完成实现、相关测试、格式化及文档，再提交评审。

应用配置位于 `yudao-server/src/main/resources/`。本地连接信息按环境配置，真实凭据、私钥、数据库备份及临时日志不提交到仓库。数据库脚本的 `GYM_DB_*` 不会自动配置 Java 应用。

## 后端实现约定

沿用现有 Controller → Service → Mapper / DO 分层及统一响应、错误码和参数校验方式。API 变更同时核对请求/响应模型、OpenAPI 注解与调用方契约。

涉及 gym 数据时，保持租户、门店和 SELF 负责关系的服务端校验；写操作检查状态流转、事务及重复提交行为。文件下载、审批和跨店查询同样执行权限及数据范围校验。新增菜单/权限定义与角色授权分开处理，避免扩大已有角色访问范围。

第三方尚未接入的能力明确标记 TODO 和缺少的资料，不将模拟成功结果视为真实接入。共享框架改动应说明对其他模块的影响。

## 数据库变更

完整操作说明见 [数据库迁移与开发 seed 手册](script/gym/DATABASE.md)。日常升级在显式设置连接变量后执行：

```bash
bash script/gym/db.sh migrate
bash script/gym/db.sh validate
```

正式变更追加到 `sql/gym/migration/mysql/V版本__说明.sql`，已应用文件保持不变。演示数据只放在 `sql/gym/seed/development/`，仅开发环境显式运行 `seed.sh development`。旧 `sql/gym/legacy/` 保持冻结，不再全量重放；应用启动不自动建 gym 表或执行 seed。

首次新库初始化与完整旧库接管按手册选择 `init` 或 `adopt-legacy`，已管理库无需重复。数据库部署前准备备份，执行失败时核查实际状态，不能假设 MySQL DDL 全部回滚。

## 格式化与验证

以下命令在仓库根目录执行。Java 使用仓库已有的 Spotless 和 google-java-format，AOSP 四空格；无需安装 IDE 专用格式化依赖。

```bash
# 自动格式化并检查 Java 源码与测试
mvn -B -ntp -N spotless:apply spotless:check

# 打包当前服务端及其依赖；此命令跳过测试
mvn -T 1C -DskipTests -pl yudao-server -am package

# gym 测试及其所需模块
mvn -B -ntp -pl yudao-module-gym -am test \
  -Dtest='Gym*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

按改动选择有意义的验证：业务规则及越权问题增加相应用例；共享框架修改补跑受影响模块测试；数据库迁移运行手册中的隔离 MySQL 回归。Spotless 只处理 Java，不格式化 SQL、Python 或前端；尤其不能批量重排已应用迁移文件。纯文档改动检查链接、命令与差异即可。

需要本地接口联调时，先完成迁移、打包并准备应用依赖，再运行：

```bash
bash script/gym/run-local.sh
```

该脚本使用 local profile 运行已构建的 jar。按需启动前端验证接口及页面行为，保留前端代码；测试脚本可能创建资料或变更授权，执行前阅读其说明并选择专用测试环境。不要停止其他人启动的服务。更多验证方式与历史证据见 [docs/testing.md](docs/testing.md)。

## 文档与评审

遵守 [docs/README.md 的记录规范](docs/README.md)：每次改动在 `docs/CHANGELOG.md` 末尾追加带时间范围的条目，写全“意图、改了什么、结构关系、验证证据、影响面 / 风险、未做 / 后续”六段；历史条目保持不变。结构变化同步更新 `docs/architecture.md`，可复现验证证据记录到 `docs/testing.md`，操作入口变化同步脚本手册。

提交评审前检查：

- 描述具体问题、修改后的行为及涉及的 API / 数据库兼容性。
- 给出实际运行的验证命令和结果，区分跳过测试的构建与测试通过。
- 说明迁移顺序、数据回填、权限变化、恢复方式和未接入的外部依赖。
- 检查 `git diff --check` 和最终差异，确认没有凭据、临时产物或无关文件。

评审说明应让未读过讨论的维护者能理解最终实现；只列出实际完成的工作和仍存在的限制。
