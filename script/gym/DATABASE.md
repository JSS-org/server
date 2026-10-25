# 数据库迁移与开发 seed 操作手册

数据库采用 **Flyway 版本迁移 + 独立开发 seed**。日常升级只需执行 `db.sh migrate`，Flyway 会按版本执行尚未应用的 SQL，并记录版本与校验和。本文所有命令均在 server 仓库根目录执行。

## 1. 配置连接

需要 Java 25、Maven、Python 3、MySQL 客户端；旧库接管还需要 `mysqldump`。首次执行 Maven 需要下载依赖。

```bash
export GYM_DB_HOST=127.0.0.1
export GYM_DB_PORT=3306
export GYM_DB_NAME=ruoyi-vue-pro
export GYM_DB_USER='你的数据库账号'
export GYM_DB_PASSWORD='你的数据库密码'
```

`GYM_DB_NAME`、`GYM_DB_USER`、`GYM_DB_PASSWORD` 必须显式设置；仅本地明确使用免密账号时，可将密码设为 `''`。这是数据库凭据，与后台登录账号无关。不要将真实密码或包含密码的环境文件提交到仓库。

这些变量只供数据库脚本使用，应用连接仍由应用配置管理。Flyway 使用独立的 `sql/gym/pom.xml`，启动 Java 应用不会自动迁移或导入 seed。

## 2. 日常升级

已由本工具管理的数据库，拉取包含新迁移文件的代码后执行：

```bash
bash script/gym/db.sh info      # 查看版本和待执行迁移
bash script/gym/db.sh migrate   # 按顺序应用新版本；无新版本时不重复执行
bash script/gym/db.sh validate  # 检查迁移记录与本地文件是否一致
```

`migrate` 本身也会先校验已有迁移；单独运行 `validate` 便于检查结果。历史表为 `flyway_gym_schema_history`。日常升级不需要执行 `init`、`adopt-legacy` 或开发 seed。现有本地开发库已完成接管，使用此流程即可。

迁移成功后再发布或启动对应版本的应用。**日常 migrate 不会自动备份**，部署前应按目标环境的备份流程准备可恢复的备份。

## 3. 首次建立迁移管理

以下两种情况二选一，均只执行一次。目标数据库必须已存在，并已初始化框架基座 `sql/mysql/ruoyi-vue-pro.sql`；gym 工具不负责创建数据库或接管全部框架表。

| 目标库情况 | 操作 |
| --- | --- |
| 已有框架表，尚无任何 gym 表和本工具迁移历史 | `bash script/gym/db.sh init` |
| 已有完整旧 gym 表，尚无本工具迁移历史 | 先 `check-legacy`，再 `adopt-legacy`，见下方 |
| 已有 `flyway_gym_schema_history` | 使用日常 `info` / `migrate` / `validate` |

旧库接管：

```bash
bash script/gym/db.sh check-legacy
export GYM_DB_BACKUP_DIR='/你的持久备份目录'
bash script/gym/db.sh adopt-legacy
bash script/gym/db.sh validate
```

`check-legacy` 只读核对旧 V001 的 59 张表结构，包括列、默认值、索引、约束、引擎及排序规则。`adopt-legacy` 再次核对，完整备份成功后记录基线 1，跳过建表的 V001，执行后续必要定义和回填。新库 `init` 则记录基线 0 后执行全部迁移。

接管备份默认放在 `/tmp/gym-db-backups`，文件权限为 600；需要长期保留时请指定持久目录。旧库缺表、存在额外 gym 表或结构漂移时会停止；先审查差异并修复，不要强行 baseline。`legacy-schema.json` 只对应历史 V001，后续新增迁移不能改它来掩盖差异。

## 4. 新增数据库变更

正式迁移放在 `sql/gym/migration/mysql/`，例如：

```text
V001__gym_schema.sql
V002__gym_reference_data.sql
V003__gym_legacy_backfills.sql
V017__add_member_external_reference.sql  # 示例；新增时以实际下一个未使用版本为准
```

1. 同步最新代码，选择未使用的版本号；文件名使用 `V版本__说明.sql`，版本与说明之间是两个下划线。
2. 将 DDL、必要基础定义或业务回填写入新版本文件；涉及应用模型时同步修改后端和相关测试。
3. 在隔离测试库验证迁移，再检查升级后的应用行为，提交 SQL 与对应代码。
4. 部署时运行 `db.sh migrate`，由 Flyway 识别待执行版本。

**已应用的迁移文件不可修改、删除、重命名或重新格式化。** 修正已上线变更时追加新版本；多人合并出现重复版本，发布前协调尚未应用的文件。不要直接修改历史表或把旧 SQL 全量重放。

菜单与权限定义属于正式迁移；给已有角色授权属于独立的权限管理操作，不能借迁移恢复已撤销权限。演示账号、样例组织及样例授权放入开发 seed。

`sql/gym/legacy/` 是旧 47 个 SQL 和旧组织导入的冻结归档，仅供追溯及历史隔离验证，不作为部署入口。

## 5. 仅开发环境导入 seed

需要演示数据时，在完成当前全部迁移后显式执行：

```bash
export GYM_DB_ENV=development
export GYM_SEED_TENANT_ID=1
bash script/gym/seed.sh development
```

seed 位于 `sql/gym/seed/development/demo.sql`，不在 Flyway 扫描目录中；生产发布不运行此命令。它创建示范部门、gymadmin/gymstore 账号及角色，首次创建的演示账号密码为 `Gym@123456`，不修改 admin。

重复执行保留已有密码、角色授权和用户绑定，不恢复人工撤销的权限。同名非种子身份冲突时拒绝并回滚。环境声明、租户或迁移状态不满足要求时也会停止。

旧的 `seed.sh` 无参数、`--schema-only`、`--menus-only` 模式已停用。建表和必要菜单变更统一通过版本迁移管理。

## 6. 验证与失败处理

数据库变更运行真实 Flyway/MySQL 隔离回归；测试账号需要创建、删除测试库的权限：

```bash
export GYM_TEST_DB_HOST=127.0.0.1
export GYM_TEST_DB_PORT=3306
export GYM_TEST_DB_USER='你的测试数据库账号'
export GYM_TEST_DB_PASSWORD='你的测试数据库密码'
PYTHONDONTWRITEBYTECODE=1 python3 script/gym/verify-migrations.py
```

该验证器创建并清理随机测试库，不使用 `GYM_DB_NAME` 指定的业务库。数据库 CI 使用同一入口，验证记录见 [testing.md](../../docs/testing.md)。

| 现象 | 处理方式 |
| --- | --- |
| 提示未接管 | 根据实际库状态选择首次 `init` 或 `adopt-legacy` |
| 校验和不匹配 | 对照版本库恢复已应用文件的原始内容；修正需求追加新版本 |
| 接管结构不匹配 | 审查结构差异，修复到历史基线后重新检查 |
| SQL 执行失败 | 停止发布，检查 `info`、错误日志及数据库实际状态，再制定恢复方案 |
| seed 被拒绝 | 检查目标环境、显式租户、迁移完成情况及同名身份冲突 |

MySQL DDL 可能隐式提交，失败不代表全部回滚。工具禁用 clean、自动 baseline 和乱序迁移，也不提供 repair；不要自行删除历史记录来绕过失败。恢复应结合实际已执行内容和备份，经审查后处理。

相关入口：[脚本目录说明](README.md)、[贡献指南](../../CONTRIBUTING.md)。

### V011 移动签课二维码

新增gym_mobile_lesson_qr（当前65张gym表），保存短时凭据哈希、签发人、预约/排期时间绑定、过期和使用信息；不为历史预约生成凭据。升级前备份并按既有migrate/validate流程执行，本批没有迁移业务库。应用另需部署签名密钥，见README的移动二维码签课配置。下一未使用迁移版本V017（V012–V016见下）。

### V012 工资显式发布

在gym_payroll新增可空published_at（datetime(6)）和published_by；在gym_payroll_item新增(tenant_id,employee_id,payroll_id,deleted)索引。表数不变，仍65。历史confirmed记录保持冻结、金额不变且发布时间为空，不向员工自动公开。发布属于明确后台操作，不执行历史回填。新增列需在应用部署前完成migrate/validate；创建索引的耗时与业务数据量有关，按既有备份和维护窗口流程操作。下一未使用版本V017（V013–V016见下）。

### V013 活动会员取消截止

为gym_activity追加可空cancel_deadline datetime(6)，无新表、索引、状态回填或角色授权。旧活动NULL保留，会员默认开始前取消；原多店关系、不限名额及报名历史不变。应用部署前migrate/validate；隔离迁移回归覆盖列精度及旧活动不被改写，业务数据库未迁移。下一未使用版本V017（V014–V016见下）。

### 匿名反馈与附件（V014）

应用部署前执行migrate/validate至V014。新增gym_feedback和gym_feedback_attachment，当前共67张gym表；附件(tenant_id,file_id)唯一，禁止一图重复关联多条反馈。保留平台受限会员归属，门店只读明确匿名VO；card_context默认NULL且allow_card_context默认false，不回填历史身份摘要。

迁移仅注册gym:feedbacks:read、gym:feedbacks:reply两条按钮权限，不自动授予任何角色，不新增前端路由。管理员需按职责授予功能权限和整店数据范围后才能读取/回复。工作人员图片只能经反馈专用辅助下载入口获取重新编码的安全副本；通用member_文件读取限制保持。需要运行环境提供标准java.desktop/ImageIO图片编解码（可在headless服务器运行）；最大10 MiB、8192单边/2000万像素限制不替代生产并发容量评估。

真实隔离MySQL/Flyway54检查通过，包括权限定义、默认不同意、附件唯一与旧库接管，临时库全部清理。本任务未迁移业务库；下一未使用版本V017（V015–V016见下）。

### 器械教学公开场景码（V015）

部署前migrate至V015并validate。gym_equipment新增public_scene varchar(32) NULL及(tenant_id,public_scene)唯一索引，表数仍67；原器械及教程不自动公开，不回填场景码，不新增权限/账号或角色授权。真实隔离MySQL/Flyway57检查通过，全部临时库清理；业务库未迁移。下一未使用版本V017（V016见下）。

管理员有gym:equipment:write及整店范围时，可调用POST /admin-api/gym/equipment/{id}/public-scenes（无body）明确公开教程并获取scene；DELETE同路径撤销，重复调用安全。场景码本身为32位小写十六进制本地路由值，不是微信官方码图片；重新发布后须更新印刷/展示的旧码。停用器械或门店暂停访问，启用后原码恢复；要废止旧码须明确DELETE。素材复用原后台同店image/video上传，仅支持普通图片及MP4，私有/有工作用途绑定的文件不能公开。前端与真实设备渠道未改动。

### 销售报表索引（V016）

部署前migrate至V016并validate。为gym_financial_event增加idx_financial_store_time（租户/门店/删除标志/事件时间）和idx_financial_order_payment（租户/订单/门店/商品/员工/事件类型/删除标志/时间），支撑时间范围汇总与原付款时间读取。无新表或数据回填，历史金额/归属不修改，仍67张gym表；下一版本V017。索引构建成本取决于已有流水量，按既有备份和维护流程安排。

销售报表复用gym:reports:read和整店范围，不自动授予权限；支持卡与私教的已确认财务事件，不发起渠道操作。实际生产查询计划、海量流水性能需部署环境验证。本任务只运行隔离数据库回归，未迁移业务数据库，前端未改动。
