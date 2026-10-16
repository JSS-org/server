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
| [`development-seed-import.md`](development-seed-import.md) | 旧种子转换、开发库导入结果及待导入清单 | 查看联调数据时 |
| [`gym-full-api-progress.md`](gym-full-api-progress.md) | 全量 API 任务状态与未完成范围 | 跟踪当前目标时 |
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
| 格式化全部后端 Java 源码/测试（根目录执行） | `mvn -N spotless:apply` |
| 仅检查 Java 格式 | `mvn -N spotless:check` |
| 编译 | `mvn -T 1C -DskipTests -pl yudao-server -am package` |
| 单测（gym 模块） | `mvn -T 1C -pl yudao-module-gym -am test -Dtest='GymFile*Test' -Dsurefire.failIfNoSpecifiedTests=false` |
| 初始化数据库（幂等） | `bash script/gym/seed.sh` |
| 认证接口验证 | `bash script/gym/verify-auth-api.sh` |
| 文件接口验证 | `bash script/gym/verify-file-api.sh` |
| 接口文档 | <http://127.0.0.1:48080/doc.html>（调试需带 `tenant-id: 1`） |


最新增量见CHANGELOG的2026-10-15 SELF改价审批条目：SQL46、本人申请范围、审批人内部账号校验、OpenAPI嵌套模型及376字段审计。298项相关测试和39项SELF HTTP通过；前端SELF发起表单store选项限制单独标记，整体review仍未结束。


2026-10-15 18:20交付入口：[gym-full-api-progress.md最终审计](gym-full-api-progress.md)逐项列出35业务页、后端闭环、301测试及review修复。SQL47已获用户明确授权并应用；SELF课程产品入口和黑名单浏览器闭环已验证。现行管理端后端工作完成，第三方按授权暂缓、前端自身及旧外部存储限制仍明示，不能等同全客户端/厂商合同验收。


## Java 格式化（2026-10-16）

根 `pom.xml` 使用构建插件 Spotless 3.10.4，格式引擎 google-java-format 1.36.1，AOSP 四空格、UTF-8/LF，关闭长字符串重写与 Javadoc 重排，按 AOSP 整理 import（引擎同时清理未使用 import）。这是构建工具，不加入应用运行依赖。

在 server 根目录运行 `mvn -N spotless:apply` 自动修正，`mvn -N spotless:check` 只检查。统一包含所有 `**/src/main/java/**/*.java` 与 `**/src/test/java/**/*.java`，也覆盖尚未启用的后端模块及 sql/dm 下的 Java 源码补丁，排除 `**/target/**`。插件只在根项目执行，不向子模块重复继承；根项目参与的 `mvn verify` 会自动检查，若仅用 `-pl` 选择子模块且未包含根项目，应单独运行上述检查命令。前端、SQL脚本、Python脚本不作为格式化输入。

本轮检查7,723个Java文件，最终修改7,504个，219个已规范；相关301测试和当前启用服务端依赖链的test-compile通过，未启用模块只做格式化/语法解析及差异核对，未宣称全部功能编译/测试。权限审查脚本另做最小修改以兼容换行注解，150方法权限表达式前后比对一致。详细证据见CHANGELOG最新条目。
