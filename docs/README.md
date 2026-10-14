# doc · 后端实现记录

> 本目录记录**本仓库（yudao-server 后端）**每一次改动的「改了什么 / 为什么改 / 结构关系如何」，供评审与回溯。
> 规划类文档在前端仓库 `GitHub/admin/target/plan/`（合同、范围、08 接口契约、09 权限码、10 旧 Mock 盘点），本目录**只记录实现与落地证据**，两者的对应关系写在每条记录里。

## 目录

| 文件 | 作用 | 何时看 |
| --- | --- | --- |
| `README.md`（本文件） | 记录规范、阅读顺序、模板 | 首次进入 / 准备提交改动前 |
| [`CHANGELOG.md`](CHANGELOG.md) | **变更流水账**（按时间升序，一次改动一条） | 审查每次改了什么、意图是什么 |
| [`architecture.md`](architecture.md) | **结构关系**：模块依赖、请求链路、数据模型、权限链 | 想知道「谁调用谁、数据怎么流」时 |
| [`gym-first-batch-api.md`](gym-first-batch-api.md) | 门店/员工首批 REST 契约、权限、迁移与边界 | 本批联调时 |
| [`testing.md`](testing.md) | 验证手段清单：单测 / 端到端脚本 / 手工复现命令 | 复现验证结论时 |

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
1. doc/CHANGELOG.md         ← 先看时间线：这次交付到哪一步了
2. doc/architecture.md      ← 再看结构：这些代码在整体里处于什么位置
3. script/gym/README.md     ← 最后看运维视角：怎么起、怎么验、接口契约
4. admin/target/plan/08,09,10 ← 需要对齐需求/契约/权限码时回查规划文档
```

## 验证入口速查

| 目的 | 命令 |
| --- | --- |
| 编译 | `mvn -T 1C -DskipTests -pl yudao-server -am package` |
| 单测（gym 模块） | `mvn -T 1C -pl yudao-module-gym -am test -Dtest='GymFile*Test' -Dsurefire.failIfNoSpecifiedTests=false` |
| 初始化数据库（幂等） | `bash script/gym/seed.sh` |
| 认证接口验证 | `bash script/gym/verify-auth-api.sh` |
| 文件接口验证 | `bash script/gym/verify-file-api.sh` |
| 接口文档 | <http://127.0.0.1:48080/doc.html>（调试需带 `tenant-id: 1`） |
