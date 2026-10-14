# 结构关系

> 目的：一眼看清「代码放在哪、谁调用谁、数据怎么流、权限怎么判」。结构发生变化时同步更新本文件（规范见 [`README.md`](README.md)）。
> 对应规划：`GitHub/admin/target/plan/03-architecture.md`（技术路线）、`08-gym-api-contract.md`（接口契约）、`09-permissions.md`（权限码）。

## 1. 全局分层

yudao 是**模块化单体**：一个 Spring Boot 进程（`yudao-server`），按业务模块拆 maven module，模块间只通过 `api` 包（跨模块契约）调用。

```
┌──────────────────────────────────────────────────────────────────────┐
│ yudao-server（空壳容器：启动类 + 配置，扫描 cn.iocoder.yudao.module）    │
│   application.yaml / application-local.yaml                          │
└───────────────┬──────────────────────────────┬───────────────────────┘
                │ 依赖                          │ 依赖
        ┌───────▼────────┐            ┌────────▼─────────┐
        │ yudao-module-  │            │ yudao-module-    │
        │ system（基座）  │            │ infra（基座）     │
        │ 账号/角色/菜单   │            │ 文件/配置/代码生成 │
        │ 部门/租户/字典   │            │ 定时任务/日志      │
        └───────▲────────┘            └────────▲─────────┘
                │ api 包                        │ api 包
        ┌───────┴───────────────────────────────┴─────────┐
        │ yudao-module-gym（本次新建 · 自研业务模块）          │
        │ 已实现：私有文件（上传/元数据/内容流）                │
        │ 规划中：门店/员工/会员/卡/课程/预约/合同/审批/设备等    │
        └───────────────────────┬─────────────────────────┘
                                │ 依赖
        ┌───────────────────────▼─────────────────────────┐
        │ yudao-framework（技术组件 starter）               │
        │ common / web / security / mybatis / redis /      │
        │ biz-tenant（多租户）/ test / excel …              │
        └─────────────────────────────────────────────────┘
```

依赖方向规则（新增模块/类时照此判断）：

| 允许 | 说明 |
| --- | --- |
| `gym → system.api` / `infra.api` | 跨模块**只能**调 `api` 包下的接口（如 `FileApi`、`PermissionApi`），不碰对方的 `service`/`dal` |
| `gym → framework starter` | 技术能力（`CommonResult`、`BaseMapperX`、`TenantBaseDO`、`@PreAuthorize`） |
| `system/infra → framework` | 基座模块同样只依赖框架 |
| ❌ `system → gym` | 基座不反向依赖业务模块，避免循环依赖 |

## 2. 请求链路

### 2.1 认证链路（§4.1 前 5 个接口，yudao 原生实现）

```
前端 ── POST /admin-api/system/auth/login ──► TenantContextWebFilter（解析 tenant-id 头）
                                              └─► TokenAuthenticationFilter（放行白名单）
                                                   └─► AuthController#login
                                                        └─► AdminAuthServiceImpl#login
                                                             ├─ AdminUserService 校验账号密码（BCrypt）
                                                             ├─ 签发 accessToken + refreshToken（Redis）
                                                             └─ 写 system_login_log

前端 ── GET /admin-api/system/auth/get-permission-info（Bearer token）──► AuthController#getPermissionInfo
        ├─ AdminUserService.getUser                     → 用户信息
        ├─ PermissionService.getUserRoleIdListByUserId  → 角色
        └─ MenuService.getMenuList(roleMenuIds)         → permissions（含 gym:* 按钮权限码）
```

鉴权拦截：`@PreAuthorize("@ss.hasPermission('...')")` → `SecurityFrameworkUtils` → `PermissionApi.hasAnyPermissions(userId, codes)`
（本模块新增的 gym 接口同样走这条链，权限码来自 `system_menu.permission`）。

### 2.2 文件链路（§4.1 后 3 个接口，本次新增 Java）

```
上传：POST /admin-api/gym/files（multipart）
      GymFileController#uploadFile
        └─ GymFileServiceImpl#uploadFile
             ① 类别校验      GymFileCategoryEnum.find
             ② 大小校验      MultipartFile.getSize()（不读内容）
             ③ 魔数嗅探      GymFileTypeUtils.detect                     ← 不信任后缀 / Content-Type
             ④ 声明比对 + 类别白名单
             ⑤ 门店解析+越权  AdminUserApi / DeptApi / PermissionApi
             ⑥ 内容存储      FileApi.createFile ──► infra 存储（本地磁盘 / DB / S3）
             ⑦ 归属落库      GymFileMapper.insert ──► gym_file

下载：GET /admin-api/gym/files/{id}/content
      GymFileController#getFileContent
        └─ GymFileServiceImpl#getFileContent
             ├─ getFile：查 gym_file + 门店越权校验
             ├─ FileApi.getFileContentByUrl ──► infra：selectByUrl → getFileContent(configId, path)
             └─ GymFileTypeUtils.writeAttachment（Content-Type = 嗅探结果，中文名 RFC 5987）
```

## 3. 数据模型

```
system_dept（门店 = 部门）            system_users（员工账号）
   id ◄───────────────┐                  id ◄──────────┐
                      │ store_id                  uploader_id
                 ┌────┴─────────────────────────────────┴────┐
                 │ gym_file（本次新增）                       │
                 │  id / store_id / category / name           │
                 │  url ──────────────► infra_file.url        │
                 │  mime_type / size / uploader_id            │
                 │  creator / create_time / deleted / tenant_id│
                 └────────────────────────────────────────────┘
infra_file（既有，非租户表）        infra_file_content（DB 存储模式时存字节）
  id / config_id / path / url / name / type / size
```

设计取舍（为什么拆两张表）：

| 关注点 | 归属 | 原因 |
| --- | --- | --- |
| 字节内容、存储配置（本地/S3/FTP/DB） | `infra_file` | 复用 infra 的存储抽象，将来换对象存储不用动业务代码 |
| 业务归属：门店、类别、原始文件名、上传人 | `gym_file` | 门店越权与类别策略是 gym 的业务规则，塞进通用文件表会污染基座 |

## 4. 权限与数据范围

gym 的每个接口都有**两道独立校验**：

| 道 | 机制 | 数据来源 | 失败表现 |
| --- | --- | --- | --- |
| ① 功能权限 | `@PreAuthorize("@ss.hasPermission('gym:<实体>:<动作>')")` | `system_menu.permission` × `system_role_menu` × `system_user_role` | `code=403` |
| ② 数据权限（门店） | `PermissionApi.getDeptDataPermission(userId)`：`all=true` 放行，否则 `storeId ∈ deptIds` | `system_role.data_scope`（1=全部、4=本部门及以下） | gym 自定义码（文件为 `1070000007`） |

权限码命名与注册（`admin/target/plan/09-permissions.md` §1/§5）：

```
后端码：gym:<实体>:<动作>          例 gym:files:upload / gym:stores:write / gym:approvals:decide
前端码：<实体>:<动作>              例 files:upload（由前端适配层 permissions.ts 剥离 gym: 前缀）
落地：script/gym/sql/01-gym-permission-seed.sql 的权限表 → 重跑 seed.sh
```

## 5. 代码地图

```
yudao-module-gym/src/main/java/cn/iocoder/yudao/module/gym/
├── controller/admin/<资源>/            # 管理端接口，前缀 /gym/<资源>
│   ├── GymXxxController.java          #   @Tag/@Operation + @PreAuthorize + CommonResult
│   └── vo/                            #   ReqVO（校验注解）/ RespVO（不含敏感字段）
├── service/<资源>/
│   ├── GymXxxService.java             # 接口：入参 VO，出参 DO/DTO
│   ├── GymXxxServiceImpl.java         # 实现：校验链 + 越权 + 事务
│   └── dto/                           # 服务层内部 DTO
├── dal/
│   ├── dataobject/<资源>/GymXxxDO.java # 表映射；租户表继承 TenantBaseDO，全局表继承 BaseDO
│   └── mysql/<资源>/GymXxxMapper.java  # extends BaseMapperX；查询条件用 LambdaQueryWrapperX
├── enums/                             # ErrorCodeConstants（1_070_000_xxx）+ 业务枚举
└── framework/                         # 模块内技术扩展（如文件类型嗅探工具）
```

命名/职责约定：

| 层 | 只做 | 不做 |
| --- | --- | --- |
| Controller | 入参校验、权限注解、DO↔VO 转换、`success(...)` | 业务判断、直接调 Mapper |
| Service | 业务规则、越权校验、事务、跨模块 `api` 调用 | 直接读写 `HttpServletRequest`、拼 SQL |
| Mapper | 单表 CRUD 与条件查询 | 业务规则、跨表 JOIN（复杂查询用 `selectJoinPage`） |

## 6. 扩展点（新增一个 gym 资源时接在哪）

| 扩展动作 | 位置 | 是否要改既有代码 |
| --- | --- | --- |
| 新增权限码 | `script/gym/sql/01-gym-permission-seed.sql` 权限表 + 重跑 `seed.sh` | 否（数据） |
| 新增文件类别 / 大小上限 | `GymFileCategoryEnum` 加一个枚举项 | 否 |
| 新增支持的 MIME | `GymFileMimeTypeEnum` + `GymFileTypeUtils.detect` 补一条魔数规则 | 否 |
| 新增业务资源（门店/会员/卡…） | 按 `controller/service/dal` 三层新建包（清单见 `script/gym/README.md` §6） | 否 |
| 新增错误码 | `ErrorCodeConstants`（区间 `1_070_000_xxx` 内递增） | 否 |
| 加跨模块能力（如需 system 的新查询） | 对方模块 `api` 包加方法 + `api` 实现类 | 是（对方模块，需评审） |
| 加表 | `script/gym/sql/NN-xxx.sql`（`seed.sh` 自动按序执行） | 否 |

## 7. 与规划文档的对应

| 规划文档 | 本仓库落地位置 |
| --- | --- |
| `08-gym-api-contract.md` §1 通用约定（前缀/响应包/分页/数据范围/权限） | `yudao-module-gym` 全部接口；`CommonResult`；`PageParam`；门店 = `system_dept` |
| `08-gym-api-contract.md` §2 标准 CRUD 路径 | 后续资源按 `/gym/{res}/page|get|create|update|delete` 实现 |
| `08-gym-api-contract.md` §3.1 `files` 资源 | 已实现（`/gym/files` 三接口） |
| `08-gym-api-contract.md` §4 认证映射 | 使用 yudao 原生 `/system/auth/*`、`/system/user/profile/get` |
| `09-permissions.md` §2 权限码注册表 | `script/gym/sql/01-gym-permission-seed.sql`（51 个码） |
| `09-permissions.md` §4 角色与数据范围 | `system_role`（`gym_hq_admin` = 1，`gym_store_manager` = 4） |
| `10-backend-mock-api-inventory.md` §4.1 | 8 行全部落地（5 原生 + 3 新写），对照表见 `script/gym/README.md` §1 |

## 2026-10-13 首批组织业务扩展

`GymStoreController` / `GymEmployeeController` 以 REST 路径接当前 JSS 前端，复用 `CommonResult` / `PageResult`。调用链：Controller 的操作权限 → 业务 Service → `GymDataScope`（system PermissionApi 的部门范围）→ MyBatis Mapper（TenantBaseDO 自动租户及逻辑删除）→ 新业务表。

- `gym_store.id` 与 `system_dept.id` 一一对应。新增可绑定已有部门或通过 DeptService 在同一事务新建部门；经营名称/状态独立，不修改 system RBAC 配置。
- `gym_store_media` 关联已有 `gym_file`；校验同租户/同店/图片视频类型，事务整体替换，不返回 infra URL。
- `gym_employee` 保存业务档案，可关联 `system_users.id`，`gym_employee_store` 保存多店任职、主门店及负责人标记。任职不会自动授予账号角色/数据范围。
- 任职关联参与员工分页 SQL 的 EXISTS 条件，结果及 total 均限制在授权范围。读共享档案时仅输出授权任职关系，写共享档案要求覆盖全部原/新任职门店。
- PATCH 和媒体替换先锁定主记录，再合并/校验/写入。员工档案及任职关系同一事务；数据库唯一索引兜底并发工号/账号重复。
- `GymPatchUtils` 合并非 null 字段；合并后完整校验，嵌套数组整体替换。工资变更暂不支持并明确拒绝。

路径/字段/错误码/初始化及回退详见 [首批 API 交接](gym-first-batch-api.md)。方法权限和 DB 隔离已由测试覆盖，真实 HTTP 过滤链和前端联调未验收。
