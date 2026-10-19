# JSS 前后端对接清单（历史快照，已归档）

> 本文保留前端迁移时的待确认快照。2026-10-15当前后端交付以 [gym-full-api-progress.md最终审计](../gym-full-api-progress.md) 为准：现行35业务页所需API、debug、前端验证和review已落地；第三方真实联调按用户授权暂缓，前端独立限制明确记录。下文“待确认/未实现”为迁移时历史背景，不是当前完成度。原 menus.json / api-inventory.json 位于独立 dashboard 仓库 `apps/web-jss/integration/`，不在本目录。

## 已核实的基础

2026-10-13，只读访问 `http://127.0.0.1:48080/v3/api-docs` 及租户查询接口成功。OpenAPI 共 347 个路径；有 `/admin-api/system/auth/login`、`get-permission-info` 和 `/admin-api/infra/file/upload`，没有 gym 业务接口。此 app 复用上述框架能力。前端迁移未改后端源码、未创建业务数据、未自动初始化菜单或数据库。

## 需要后端团队处理

| 项目 | 后端协作内容 | 前端当前处理 |
| --- | --- | --- |
| 健身业务 API | 门店、员工档案、会员/卡、私教、预约/签课、合同/审批、设备、工资、活动、售卡/报表、加盟和分账等真实接口 | 页面已迁入，`gymApi` 集中承载待确认契约；无 Mock 回退 |
| 业务菜单和按钮权限 | 用现有 `system_menu` / 角色授权配置 gym 页面与 `gym:` 权限码 | 仅注册后端授予且已迁入的业务组件；工作台显示当前可用菜单 |
| 账号及门店数据范围 | 员工档案关联 `system_user`；总部/门店范围及负责会员限制由后端校验 | 复用 system 用户/角色/数据权限 UI；旧 `gym/accounts`、`gym/roles` 不迁入 |
| 私教预约与改价 | 明确私教产品/排期模型、可预约时段、教练及权益归属、扣课幂等和改价审批 | 页面限定 `kind=private`；不提供操课管理。当前字段沿用业务模型，需双方确认 |
| 受控文件 | 确认合同/体测/训练资料的文件 ID、元数据和权限校验；可复用 infra 存储，但读取须满足业务隔离 | 保留业务文件抽象及鉴权下载，不能把返回 URL 的基础上传直接冒充完整业务文件接口 |
| 第三方及执行结果 | 设备控制、电子签、团购验券、包月/支付/分账和提醒渠道；提供可查询的真实结果、失败原因、幂等保障 | 页面只展示服务返回结果，未配置渠道不作为已完成或成功 |

## 待确认的通用业务契约

前端目前提出 `/admin-api/gym` 命名空间，保留原业务操作语义，以便逐模块和后端对齐。 **这些是待确认的前端契约，不代表后端已经存在相同路径。** 后端如采用 `/page`、`/get`、`/create`、`/update` 等命名，仅需在 `src/api/gym.ts` 中适配，页面不必随之改动。

| 调用 | 当前提出的 HTTP 契约 |
| --- | --- |
| `gymApi.list(resource, params)` | `GET /admin-api/gym/{resource}`，查询参数 `pageNo/pageSize` 及业务筛选 |
| `gymApi.get(resource/id)` | `GET /admin-api/gym/{resource}/{id}`，返回业务详情 |
| `gymApi.post(resource, data, key)` | `POST /admin-api/gym/{resource}`，新增或业务动作；需要幂等的操作发送 `Idempotency-Key` |
| `gymApi.patch(resource/id, data)` | `PATCH /admin-api/gym/{resource}/{id}`，只提交变更字段 |
| `gymApi.put(path, data)` | `PUT /admin-api/gym/{path}`，保存整段设置或关联配置 |

响应遵循芋道 `{ code: 0, data, msg }`；分页 `data` 为 `{ list, total, ...可选汇总字段 }`，在前端统一转换为列表状态。关联选择器会分页拉取全部授权范围记录，当前每页 100 条。业务 ID 在前端契约中使用字符串；请确认后端序列化及 ID 字段类型，尤其是关联的 system 用户 ID。不要仅用前端筛选代替门店隔离；所有读取、导出、写入和第三方动作均应由服务端授权。

### 文件契约

- `POST /admin-api/gym/files`：multipart `file/category/storeId`；返回至少 `id`。
- `GET /admin-api/gym/files/{id}`：返回 `id/originalName/mimeType/byteSize` 元数据。
- `GET /admin-api/gym/files/{id}/content`：经过业务权限校验后返回文件内容。
- 页面中图片/视频、合同模板、体测报告、审批附件和富文本 `gym-file:` 引用均使用上述抽象。实际实现可由后端提供适配或双方改用经确认的受控 URL 模型。

### 菜单与权限配置

参考 dashboard 的 `apps/web-jss/integration/menus.json`，在 `gym` 一级目录下配置业务菜单，使最终路由为 `/gym/stores`、`/gym/members` 等。例如门店页面 `component=gym/stores/index`、`componentName=GymStores`、`permission=gym:stores:read`。不要额外增加路径层级，否则首页快捷入口也需要同步。`/workspace`、个人中心、个人站内信和文件预览为登录后的辅助路由，不赋予额外业务权限。文件内容访问仍由后端验证。读写按钮权限按同目录的 `api-inventory.json` 配置，前端 `canGym('members:write')` 检查的是 `gym:members:write`。

API 清单收录显式业务调用；通用编辑器的动态资源 CRUD、字段中的关联资源和业务返回详情结构还需结合对应 `data.ts` / `use-page.ts` 核对。付款、扣课、审批与设备执行必须由后端实现业务校验，不能仅根据一个通用 CRUD 接口推断它们已支持。

## 联调顺序及验收边界

1. 基础登录 → 用户权限 → 当前授权菜单 → 工作台。
2. 门店列表/详情 → 员工与 system 用户关联 → 会员档案。
3. 卡种与私教 → 预约/签课 → 合同/审批及受控文件。
4. 工资、设备、售卡/包月/验券、报表、活动和支付/分账等按合同需求分批验收。

现阶段可以验证前端构建、状态管理、组件交互和基础接口连接。菜单、业务 API、数据权限及第三方能力未落地前，相应 R 需求保持“待联调”，不得记录为已验收。
