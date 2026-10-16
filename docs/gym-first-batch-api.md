# 门店与员工首批 API 交接

> 2026-10-16：数据库现由Flyway迁移与独立开发seed管理，操作以 [当前运行手册](../script/gym/README.md) 为准。下文旧SQL路径与初始化命令仅供历史追溯，已归档至sql/gym/legacy。

日期：2026-10-13。依据 dashboard 的 `targets/handoff/backend-priorities.md` P0/P1、`targets/plan/08-gym-api-contract.md`、`09-permissions.md` 及 `apps/web-jss` 对应页面实际调用实现。只修改 server。此批源码已实现；尚未部署至 48080，不代表前端业务验收通过。

## 实施优先级

1. 本批：菜单与权限入口、门店档案/媒体、员工档案/已有 system 账号关联、门店及租户隔离。
2. 下一批：员工工资历史/模板配置（会影响有 payroll 权限用户的员工编辑），随后会员/卡种及私教权益、预约与签课。
3. 后续：合同/审批、工资计算、设备、支付及第三方真实执行闭环，按交接文档分批推进。

## 路径、权限与响应

所有路径在 `/admin-api/gym` 下；请求带 `Authorization: Bearer …` 和 `tenant-id`。返回芋道 `CommonResult`：`{code:0,data:…,msg:""}`；错误按 `code` 判断。

| 方法 | 路径 | 权限 | data |
| --- | --- | --- | --- |
| GET | `/stores` | `gym:stores:read` | `{list,total}` |
| GET | `/stores/{id}` | `gym:stores:read` | 门店详情，包含媒体 |
| POST | `/stores` | `gym:stores:write` + 全部门店数据范围 | 新建资料 |
| PATCH | `/stores/{id}` | `gym:stores:write` + 该门店范围 | 更新后的资料 |
| PUT | `/stores/{id}/media` | `gym:stores:write` + 该门店范围 | `true` |
| GET | `/employees` | `gym:employees:read` | `{list,total}` |
| GET | `/employees/{id}` | `gym:employees:read` | 员工详情 |
| POST | `/employees` | `gym:employees:write` + 全部任职门店范围 | 新建资料 |
| PATCH | `/employees/{id}` | `gym:employees:write` + 原/新全部任职门店范围 | 更新后的资料 |

分页 `pageNo>=1`、`pageSize=1..200`，默认 1/10，按 ID 倒序，支持 `keyword`、`status=active|inactive`、`storeId`。门店关键词检索编码/名称，员工关键词检索工号/姓名/手机号。非法分页和枚举拒绝。`total` 在服务端授权筛选后计算；跨店 `storeId` 返回范围错误。前端关联选择器可每页 100 条持续拉取。

业务 ID、关联 ID 全部返回字符串；请求兼容数字或数字字符串。面积按平方米、最多 2 位小数，坐标最多 7 位，日期为 `YYYY-MM-DD`，营业时间 `HH:mm`。面积返回 JSON 数字，不涉及支付金额；营业时间支持跨午夜/相同时间，不推断预约规则。

PATCH 省略或 `null` 字段保持原值；可选文本用 `""` 清空，features/media 用空数组清空。businessHours/storeAssignments 整体替换。合并后按完整对象校验。暂无删除、批量操作、导出、创建请求幂等或账号解绑接口。

## 门店字段与部门关系

```json
{
  "code":"STORE-A", "name":"联调门店 A", "description":"简介",
  "areaSqm":"500.00", "features":["私教","力量训练"], "address":"测试地址",
  "longitude":121.4737,"latitude":31.2304,
  "servicePhone":"021-00000000","serviceWechat":"store-service",
  "businessHours":{"opens":"06:00","closes":"23:00"},"status":"active"
}
```

新增必填 code/name/areaSqm/address/businessHours；status 默认 active，features 默认 `[]`，description 默认空文本。编码在租户内唯一，name 最长 30 字，地址最长 500 字，面积不可负。

`gym_store.id = system_dept.id`，所以前端 storeId、文件归属及 system 部门数据权限共享编号。新增可传 `deptId` 绑定已有启用部门；不传则通过 DeptService 新建部门，可传 `parentDeptId` 指定总部部门，缺省为根部门。需组织成总部树时新增应传总部部门 ID。新增/绑定仅允许全部数据范围，避免新部门扩张门店角色权限。部门与经营档案同一事务。

经营名称和经营状态独立于 system 部门名称/状态；修改经营资料不改 RBAC 树、部门状态或账号授权。绑定后不能借 PATCH 更换部门/父部门；system 部门调整仍走原 system 管理入口。

详情返回相同字段及 `id`、`media`；分页不加载媒体，media 返回空数组，详情才返回真实媒体清单。

```json
{"items":[{"fileId":"123","caption":"环境照片","isCover":true,"sortOrder":0}]}
```

媒体整体替换最多 50 项，不能重复文件；文件必须属于当前租户/同一门店、类别 image 或 video；至多一个封面且须图片。验证全部通过后才替换；门店行锁与事务保护并发写入。返回媒体含字符串 id/fileId、name/originalName、mimeType、byteSize、category、caption/isCover/sortOrder，不返回存储 URL。

已有文件响应补充 originalName/byteSize，同时保留 name/size；id/storeId/uploaderId 改为字符串，以匹配当前前端。二进制鉴权和存储逻辑沿用已有实现。本次未验证既有 infra 公开下载路径的完整防绕过能力，不能据此声称文件安全约束已经全部验收。

## 员工字段与账号关系

```json
{
  "employeeNo":"E001","name":"联调员工","phone":"13800000000",
  "jobTitle":"教练","hiredOn":"2026-10-13","status":"active",
  "storeAssignments":[
    {"storeId":"101","isPrimary":true,"isManager":false,"joinedOn":"2026-10-13"}
  ]
}
```

工号/姓名/手机号/入职日期/任职关系必填；status 默认 active。手机号为大陆 11 位手机号码；工号租户内唯一且创建后不可修改。任职门店 1..30 个，不重复，恰好一个主门店，任职日期不得早于入职日期。isManager 仅业务标记，不授予 system 角色或数据范围。

可选 userId 关联已有启用 system 账号；新建关联或更换账号额外需要 `system:user:update`，账号所属部门必须等于主门店，账号在租户内只能关联一个员工。不会创建密码、修改账号部门或授权角色。当前前端表单没有 userId 编辑字段，可通过本 API 由已有权限的管理端集成/工具关联；前端相关界面本次未改动。档案停用不自动停用 system 账号。

列表/详情返回 userId/username、storeIds、storeAssignments。员工有任一授权门店时可读取，但只返回该用户授权范围内的任职关系；写入共享档案必须覆盖所有原任职门店及全部新门店，避免修改另一门店的共享资料或移除其关系。范围来自 system 角色 data_scope，员工多店任职本身不扩张登录账号权限；多店账号范围仍通过 system 数据权限配置。

工资历史、工资模板及计算未实现；提交 salaryAssignment 明确返回 `1070002007` 并回滚，不忽略工资变更。已有演示角色带 payroll 权限，前端员工 loadDetail 会请求 `/employee-salary-assignments`，目前仍缺少此接口；首批联调应使用仅授予本批权限的角色，或等待工资切片交付。未伪造空工资历史。

## 错误码

| code | 意义 |
| --- | --- |
| 1070001001 | 门店不存在（含其他租户不可见） |
| 1070001002 | 无权访问门店范围/无权新建部门 |
| 1070001003 | 编码或部门重复 |
| 1070001004 | 绑定部门无效/修改部门关系 |
| 1070001005 | 媒体重复、归属/类型/封面错误 |
| 1070002001 | 员工不存在（含其他租户不可见） |
| 1070002002 | 工号或关联账号重复 |
| 1070002003 | 工号不可修改 |
| 1070002004 | 任职重复/主门店/日期错误 |
| 1070002005 | 账号无效或主部门不匹配 |
| 1070002006 | 缺少账号关联权限 |
| 1070002007 | 工资配置尚未接入 |

请求字段校验错误沿用框架 400，操作权限拒绝沿用 403。新部门创建时的父部门/同级名称校验仍使用 system 部门错误码。

## 迁移、菜单与验证

新脚本 `03-gym-store-employee.sql` 创建四个业务表，`04-gym-first-batch-menus.sql` 将已有 gym-seed 的门店/员工 read 按钮改为业务页面（保留 ID/原授权），缺少页面时增量补充。根路径 gym，组件对应当前前端，写权限仍分离。

菜单为租户共享；自动补充页面授权仅作用于租户 1 的既有 gym_hq_admin/gym_store_manager。其他角色/租户通过 system 后台按需授权，不自动授权所有开发账号。历史 01 的其他权限名称尚未全面对齐新清单，本批只交付门店/员工/文件，不表示 35 个业务页面已支持。

```bash
# 已初始化认证/文件的环境，仅执行本次新增的两个 SQL，使用自己的数据库连接方式。
# 全新环境可以使用已有 seed.sh；它会执行 01 的演示账号和历史权限初始化。
# 本次没有对业务数据库执行迁移或写入。

mvn -DskipTests -pl yudao-server -am package
python3 script/gym/verify-migrations.py
```

SQL 幂等适用于当前全新表结构，不替代未来 ALTER 版本迁移；没有覆盖人工菜单或删除业务数据。迁移后重启新版后端，重新登录验证权限缓存及菜单下发。联调用 `script/gym/gym-organization.http`，OpenAPI 查看 `/v3/api-docs` 或 `/doc.html`。

回退先部署旧代码，停用本批新增/转换页面并按变更前菜单备份恢复 read 按钮；保留业务表和数据。不要通过 DROP 业务表来回退。共享菜单恢复和租户授权应由运维按备份精确执行。

验证：34 个 gym 测试通过（17 个组织数据库/权限测试 + 17 个既有文件测试），MySQL 临时库两轮迁移验证通过；主服务打包结果见本次 CHANGELOG。测试使用真实 MyBatis/租户拦截器/事务/方法权限代理，跨模块 system 依赖为 mock。未启动真实服务、未执行真实前端 HTTP/浏览器回归，未验收请求过滤链、生产 MySQL 并发或第三方服务。
