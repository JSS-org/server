# 旧种子数据导入开发库

> 2026-10-16：数据库现由Flyway迁移与独立开发seed管理，操作以 [当前运行手册](../script/gym/README.md) 为准。下文旧SQL路径与初始化命令仅供历史追溯，已归档至sql/gym/legacy。

2026-10-14，按用户提供的 `/Users/hh/Documents/GitHub/database/` 种子和确认范围执行：先导入已实现模块，其余保留待导入清单。源目录保持只读，未执行其中的 PostgreSQL 迁移，未改前端。

## 导入结果

目标：`127.0.0.1:3306/ruoyi-vue-pro`，租户 `1`。当前后端为 MySQL / system 用户与部门模型，源种子为 PostgreSQL / UUID / 独立 admin_accounts 模型，因此进行了显式字段和 ID 转换。

| 数据 | 结果 |
| --- | --- |
| 门店 | SH001 → 126（上海一店）；SH002 → 127（上海二店） |
| 员工 | E001 → 1（店长1，账号 255）；E002 → 2（店长2，账号 256） |
| 任职 | 2 条；主门店分别为 126、127，负责人标记及入职日期保留 |
| system 账号 | hq → 254（总部部门 124）；store1 → 255（门店 126）；store2 → 256（门店 127） |
| system 角色 | gym_dev_hq → 173（全部门店）；gym_dev_store → 174（本部门及以下） |
| 账号角色 | 3 条 |
| 角色菜单 | 12 条，包含两角色的根目录授权；业务权限按源授权筛选首批能力 |
| 部门 | 新增 2 个，归属现有健身总部 124；门店 ID 与部门 ID 相同 |
| 业务表 | 补建 gym_store / gym_employee / gym_employee_store / gym_store_media |
| 页面 | 门店/员工 read 按钮增量转换为对应业务页面，保留原 ID 与授权 |

首批总部角色拥有 `gym:stores:read/write`、`gym:employees:read/write`、`gym:files:read/upload`。门店角色仅有 `gym:stores:read`、`gym:employees:read`、`gym:files:read/upload`，遵循旧种子的实际授权，不额外授予写权限、工资或账号管理权限。

新账号沿用源开发密码 `123456`，旧 scrypt 派生值已验证并转换为项目使用的 BCrypt；三个实际入库密码均通过 Spring Security BCryptPasswordEncoder 校验。未修改现有账号密码/部门/权限，未复制旧 scrypt 字段到 system_users。源 admin_accounts 没有手机字段，新 system 用户不补造手机号，员工档案保留原手机，避免与现有演示用户重复。

数据 creator/updater 为 `gym-legacy-seed`。新增共 28 行（部门 2、用户 3、角色 2、角色菜单 12、用户角色 3、门店 2、员工 2、任职 2）；再次导入后各表记录数保持一致。

## 待导入清单

源 10 个种子文件共 310 条 INSERT（309 条 VALUES + 1 条 SELECT），涉及 65 张旧模型表。以下保持原文件，不建立旧模型影子表，也不作为已实现 API 的业务数据。

| 来源 | 待导入内容与数量 | 原因 |
| --- | --- | --- |
| 001-permissions.sql | 未映射的 51 个权限及相关角色授权 | 仅复用首批 6 个 gym 权限；账号/RBAC 走 system，未实现业务不提前授予 |
| 002-development.sql | 卡模板 2、模板门店 1、会员 8、会员门店 8、会员卡 6 | 尚无对应 gym 业务表/API |
| 003-training-development.sql | 教练资料 2、课程产品 4、课包 2、排期 4、预约 2、签到 2、签课 2、体测 2 | 私教及会员权益模型尚未实现；不能导入旧操课模型作为当前可用业务 |
| 004-governance-development.sql | 总部员工 HQ001 1；文件 3；合同模板 1、审批模板 1、合同 2、合同事件 2、审批实例 2、任务 2、步骤 1 | HQ001 无任职门店，当前员工契约需主门店；文件仅有 Mock PDF 元数据、无真实内容，不能写入私有文件服务；合同/审批未实现 |
| 005-devices-development.sql | 设备 4、柜格 6、器械 2 | 对应业务表与设备集成未实现 |
| 006-sales-development.sql | 售卡设置 2、售卡商品 2、订阅设置 2、核销设置 2、订单 8、订单项 8、交易 8、业绩分配 8、订阅授权 2、订阅周期 2、核销 2、用卡记录 2 | 对应业务表/API 和真实支付未实现，不将种子状态当成支付成功证据 |
| 007-marketing-development.sql | 优惠券模板 1、积分规则 3、积分产品 1、积分账户 1、积分流水 1 | 当前合同排除项，不默认扩展后端范围 |
| 008-content-development.sql | 客户端设置 1、活动 1、品牌 1、加盟咨询 1、评价 2、反馈 2、内容块 1、活动门店 2、报名 1、加盟模型 3 | 对应业务表/API 未实现 |
| 009-settlement-development.sql | 收款账户 1、分账规则 1、规则项 1 | 对应业务表/API 与渠道未实现 |
| 010-payroll-development.sql | 工资模板 1、工资规则 3、员工工资配置 2 | 工资业务表/API 未实现 |

旧 UPDATE（hq 账号绑定 HQ001、订单关联卡/课包）随相关模型暂缓。hq 已作为总部 system 账号导入，不凭空给 HQ001 分配门店。

## 文件与复现

- 转换后的 MySQL 种子：`script/gym/development/01-legacy-organization.sql`。放在独立 development 目录，常规 `seed.sh` 不会自动加载这些账号/业务数据。
- 专用验证：`script/gym/verify-legacy-seeds.py`。使用随机临时 MySQL 库，验证双轮幂等、主门店/账号一致和同名非种子账号拒绝及回滚，完成后删除测试库。
- 正式导入前备份：`/tmp/gym-development-before-seeds-1dtpy3up.sql`，文件权限 600，包含此次涉及的现有 system 表。备份位于本机临时目录，应由开发者按需要另行保管。

```bash
# 已执行；连接账号按实际环境替换，不把密码写在命令行中。
mysql -h127.0.0.1 -u yudao -p ruoyi-vue-pro < script/gym/sql/03-gym-store-employee.sql
mysql -h127.0.0.1 -u yudao -p ruoyi-vue-pro < script/gym/sql/04-gym-first-batch-menus.sql
mysql -h127.0.0.1 -u yudao -p ruoyi-vue-pro < script/gym/development/01-legacy-organization.sql
python3 script/gym/verify-legacy-seeds.py
```

前置条件：现有健身总部、gym 根目录和六个首批权限。转换 SQL 带事务、自然键幂等及同名非种子账号/角色/档案守卫；失败时客户端必须立即停止（不要使用 mysql --force），让事务回滚。不覆盖人工记录，不自动解绑/重分配账号。

验证结果：临时库验证通过并清理；开发库两轮执行记录数一致，门店/员工/账号/角色及关联逐项核对通过；三账号 BCrypt 校验通过。当前 48080 无运行服务，本次未验证 HTTP 登录、API 或浏览器。启动最新版后端后重新登录查看新菜单；若运行进程已有菜单缓存，需按项目正常方式重新加载。
