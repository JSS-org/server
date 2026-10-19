# docs · 后端文档索引

> 本目录记录**本仓库（yudao-server 后端）**每一次改动的「改了什么 / 为什么改 / 结构关系如何」，供评审与回溯。
> 现行前端契约位于独立 dashboard 仓库的 `apps/web-jss/integration/`，规划与交接记录见该仓库 `targets/`。本目录记录后端实现、业务边界与验证证据；历史快照不代表当前完成度。

## 现行实现与业务说明

日常数据库操作见 [迁移与开发 seed 手册](../script/gym/DATABASE.md)，参与后端开发见 [CONTRIBUTING.md](../CONTRIBUTING.md)。

| 文件 | 作用 | 何时看 |
| --- | --- | --- |
| [`architecture.md`](architecture.md) | **结构关系**：模块依赖、请求链路、数据模型、权限链 | 想知道「谁调用谁、数据怎么流」时 |
| [`gym-full-api-progress.md`](gym-full-api-progress.md) | 全量 API 任务状态与未完成范围 | 跟踪当前目标时 |
| [`group-buy-verification.md`](group-buy-verification.md) | 团购验券业务、美团历史查询 POST、配置和后续平台范围 | 对接团购平台时 |

## 验证与变更记录

| 文件 | 作用 | 何时看 |
| --- | --- | --- |
| [`testing.md`](testing.md) | 验证手段清单：单测 / 端到端脚本 / 手工复现命令 | 复现验证结论时 |
| [`CHANGELOG.md`](CHANGELOG.md) | **变更流水账**（按时间升序，一次改动一条） | 审查每次改了什么、意图是什么 |

## 历史与专项参考

| 文件 | 作用 | 使用边界 |
| --- | --- | --- |
| [`gym-first-batch-api.md`](gym-first-batch-api.md) | 门店/员工首批 REST 契约、权限、迁移与边界 | 首批实现记录，不是全量接口清单 |
| [`development-seed-import.md`](development-seed-import.md) | 旧种子转换、开发库导入结果及待导入清单 | 日常迁移以脚本目录手册为准 |
| [`archive/backend-contract.md`](archive/backend-contract.md) | 前端迁移早期的待确认契约快照 | 已归档，现行契约以 dashboard 和当前实现为准 |

维护方式：新增业务说明放在 docs 根目录并登记索引；已失效的契约快照放入 archive；CHANGELOG 保留原顺序，仅追加。现有实现与验证文档保持路径，避免破坏历史引用。

## 记录规范（每次改动后必须做）

1. **追加一条到 `CHANGELOG.md`**，按**时间升序**放在文件末尾（历史在上，新的在下），条目标题格式：
   `## [YYYY-MM-DD HH:MM ~ HH:MM] <一句话主题>`
2. 每条必须写全这 6 段，缺一不可：

   | 段落 | 内容要求 |
   | --- | --- |
   | **意图** | 为什么做这件事（对应规划文档的哪一条 / 解决什么阻塞） |
   | **改了什么** | 文件清单表格：路径 + 类型（新增/修改）+ 一句话说明；关键处给行号 |
   | **结构关系** | 新增/修改的类与既有类怎么连接（调用链、依赖方向、数据流向）；结构有变化时同步更新 `architecture.md` |
   | **验证证据** | 可复现的命令与结果数字（单测数、断言数、curl 输出摘要） |
   | **影响面 / 风险** | 会影响谁（前端、其他模块、运维）、有哪些兼容性或安全影响 |
   | **未做 / 后续** | 明确本次没做什么，避免把「没做」误读成「做了」 |

3. **不写结论性空话**：所有「已完成 / 已验证」必须附命令与输出；第三方未接入的能力必须写「未接入」。
4. **结构变化才动 `architecture.md`**：纯 bugfix、文案、注释不需要改它。
5. `CHANGELOG.md` 只追加，不修改历史条目；如发现历史记录有误，新增一条「勘误」说明，保持可追溯。

## 阅读顺序建议

```
1. docs/README.md          ← 先找现行业务说明和验证入口
2. docs/architecture.md    ← 再看结构：这些代码在整体里处于什么位置
3. script/gym/README.md     ← 最后看运维视角：怎么起、怎么验、接口契约
4. docs/CHANGELOG.md        ← 需要追溯改动与证据时看时间线
```

## 验证入口速查

| 目的 | 命令 |
| --- | --- |
| 格式化全部后端 Java 源码/测试（根目录执行） | `mvn -N spotless:apply` |
| 仅检查 Java 格式 | `mvn -N spotless:check` |
| 编译 | `mvn -T 1C -DskipTests -pl yudao-server -am package` |
| 单测（gym 模块） | `mvn -T 1C -pl yudao-module-gym -am test -Dtest='GymFile*Test' -Dsurefire.failIfNoSpecifiedTests=false` |
| 新gym环境初始化（先导入框架基座） | `bash script/gym/db.sh init` |
| 完整旧gym库接管（先校验/备份） | `bash script/gym/db.sh check-legacy`、`bash script/gym/db.sh adopt-legacy` |
| 日常数据库升级与校验 | `bash script/gym/db.sh migrate`、`bash script/gym/db.sh validate` |
| 显式开发seed | `GYM_DB_ENV=development GYM_SEED_TENANT_ID=1 bash script/gym/seed.sh development` |
| 当前数据库管理说明 | [Flyway与seed运行手册](../script/gym/README.md)（所有命令需显式GYM_DB_*） |
| 认证接口验证 | `bash script/gym/verify-auth-api.sh` |
| 文件接口验证 | `bash script/gym/verify-file-api.sh` |
| 接口文档 | <http://127.0.0.1:48080/doc.html>（调试需带 `tenant-id: 1`） |


阶段交付与第三方未接入范围见 [gym-full-api-progress.md](gym-full-api-progress.md)；后续增量见 [CHANGELOG.md](CHANGELOG.md)。团购本次仅搭建美团历史查询请求，不能据此判断真实核销已经接通。


## Java 格式化（2026-10-16）

根 `pom.xml` 使用构建插件 Spotless 3.10.4，格式引擎 google-java-format 1.36.1，AOSP 四空格、UTF-8/LF，关闭长字符串重写与 Javadoc 重排，按 AOSP 整理 import（引擎同时清理未使用 import）。这是构建工具，不加入应用运行依赖。

在 server 根目录运行 `mvn -N spotless:apply` 自动修正，`mvn -N spotless:check` 只检查。统一包含所有 `**/src/main/java/**/*.java` 与 `**/src/test/java/**/*.java`，也覆盖尚未启用的后端模块及 sql/dm 下的 Java 源码补丁，排除 `**/target/**`。插件只在根项目执行，不向子模块重复继承；根项目参与的 `mvn verify` 会自动检查，若仅用 `-pl` 选择子模块且未包含根项目，应单独运行上述检查命令。前端、SQL脚本、Python脚本不作为格式化输入。

历史格式化检查的文件数、测试数及边界见 CHANGELOG 的 2026-10-16 对应条目；本次变更的实际验证单独记录，不沿用历史数字。
