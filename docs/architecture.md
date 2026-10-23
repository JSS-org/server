# 结构关系

> 目的：一眼看清「代码放在哪、谁调用谁、数据怎么流、权限怎么判」。结构发生变化时同步更新本文件（规范见 [`README.md`](README.md)）。
> 对应规划：`GitHub/admin/target/plan/03-architecture.md`（技术路线）、`08-gym-api-contract.md`（接口契约）、`09-permissions.md`（权限码）。

## 团购平台服务零售接入（2026-10-19 增量）

`POST /admin-api/gym/group-buy/meituan/retail/**` → `GymMeituanRetailController`（查询gym:sales:read、核销/撤销gym:verifications:manage、退款gym:group-buy:refund）→ `GymMeituanRetailService` → `GymStoreService.requireStore`（租户与门店范围）→ 填充当前登录操作人 → `GymMeituanRetailClient` 对应的16个明确类型方法 → `GymMeituanTransport` → 美团固定白名单表单 POST。业务 DTO 和 MeituanCommonRequest 均通过 getter/setter 赋值；biz 只序列化一次，公共字段只在签名/传输边界通过 toParameters 转为 Map。

GymMeituanProperties 按租户/门店读取外部配置，默认businessId=58，传输层核对接口业务编号；旧餐饮历史入口标记deprecated并仅接受显式businessId=1，也复用共享传输。enabled和mutationsEnabled默认false，变更接口须额外启用。网络/响应异常时，核销/撤销/退款变更返回结果待确认，不自动重试；批量结果保留逐项状态。退款权限定义追加V004，不修改角色授权。

当前仅平台请求框架，不写gym_verification、会员权益或财务事件，现有本地记录GET契约不变。真实授权/联调、回调与本地履约、其他平台未接入。详细边界见 [团购业务说明](group-buy-verification.md)，完整目录与DTO见 [16项接口对照](meituan-service-retail-api.md)。

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
落地：sql/gym/migration/mysql/Vxxx__permissions.sql 追加版本 → db.sh migrate
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
| 新增权限码 | `sql/gym/migration/mysql/Vxxx__permissions.sql` 追加版本 + `db.sh migrate` | 否（数据） |
| 新增文件类别 / 大小上限 | `GymFileCategoryEnum` 加一个枚举项 | 否 |
| 新增支持的 MIME | `GymFileMimeTypeEnum` + `GymFileTypeUtils.detect` 补一条魔数规则 | 否 |
| 新增业务资源（门店/会员/卡…） | 按 `controller/service/dal` 三层新建包（清单见 `script/gym/README.md` §6） | 否 |
| 新增错误码 | `ErrorCodeConstants`（区间 `1_070_000_xxx` 内递增） | 否 |
| 加跨模块能力（如需 system 的新查询） | 对方模块 `api` 包加方法 + `api` 实现类 | 是（对方模块，需评审） |
| 加表 | `sql/gym/migration/mysql/Vxxx__说明.sql`，发布前 `db.sh migrate` | 否 |

## 7. 与规划文档的对应

| 规划文档 | 本仓库落地位置 |
| --- | --- |
| `08-gym-api-contract.md` §1 通用约定（前缀/响应包/分页/数据范围/权限） | `yudao-module-gym` 全部接口；`CommonResult`；`PageParam`；门店 = `system_dept` |
| `08-gym-api-contract.md` §2 标准 CRUD 路径 | 后续资源按 `/gym/{res}/page|get|create|update|delete` 实现 |
| `08-gym-api-contract.md` §3.1 `files` 资源 | 已实现（`/gym/files` 三接口） |
| `08-gym-api-contract.md` §4 认证映射 | 使用 yudao 原生 `/system/auth/*`、`/system/user/profile/get` |
| `09-permissions.md` §2 权限码注册表 | `sql/gym/migration/mysql/V002__gym_reference_data.sql` 及后续版本，历史01在legacy中冻结 |
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

## 2026-10-14 薪资与会员基础扩展

GymEmployeeService → GymSalaryService：员工事务内提交薪资配置；后者依赖真实员工/任职 Mapper，不反向依赖员工 Service，因此没有循环依赖。GymSalaryController 提供模板版本与薪资关联 REST API。gym_salary_template 以 family_id/version 标识历史版本；gym_salary_assignment 保存员工、发薪门店、模板及有效日期。模板版本分配锁定家族根，薪资关联写入锁定员工并对任职/历史使用当前锁定读取。范围校验继续复用 GymDataScope，模板写入要求总部，薪资历史按发薪门店过滤。

GymMemberController → GymMemberService → GymMemberMapper：gym_member 目前保存建档门店、唯一手机号和服务端会员号。GymDateRange 统一业务日期至 Asia/Shanghai 的 Instant 左闭右开区间。会员权益与多门店归属仍待后续模块扩展；完整状态见 gym-full-api-progress.md。

### 会员记录扩展

GymBlacklistController / GymMeasurementController → GymMemberRecordsService → gym_blacklist / gym_measurement。黑名单写入仅总部；记录读取经会员门店范围，实际阻断查询基于租户、会员、当前时间、禁止入场/购卡标志及撤销状态，供后续业务服务调用。体测保存校验会员门店一致及文件为本门店body_report类别；下载继续用既有受控文件接口。黑名单结束时间及撤销时间、体测时间保存为Instant，前端统一返回ISO时间。

## 卡权益、私教与审批模板增量

GymCardController→GymCardService→模板/会员卡/成员门店及卡门店Mapper；模板冻结且发卡仅内部可信业务来源。会员接口返回授权权益，并用真实权益表过滤临期。GymCourseController→GymCourseService→产品、排期、权益包、预约、签到、课消六表；预占与释放在事务内，唯一课消约束和当前锁定读取防止重复扣课。GymBusinessMutex在租户级串行化全部门店权益发放与新店创建/激活；新店权益扩展采用READ_COMMITTED、member→card锁顺序，与预约一致。

GymApprovalTemplateController→GymApprovalTemplateService→gym_approval_template，steps持久化有序员工ID，发布校验可用员工与账号；家族根锁串行分配版本。模板与审批选项为后续审批记录的真实基础，尚未实现记录状态机。发布版本不得修改内容或回退草稿。

## 审批记录和业务效果

GymApprovalController→GymApprovalService→gym_approval/gym_approval_task。模板、申请业务和审批账号在发起时固化，当前步骤账户与方法权限、店范围共同约束审核；模板或账号变更不转移已生成待办。申请提交键持久化唯一并校验内容摘要。最终步骤事务内调用GymCardService.issue或GymCourseService.applyApprovedPrice，失败回滚步骤状态和审计时间；卡发放来源唯一，重复审核不产生重复权益。改价使用申请原价防止覆盖其他已批准价格。共同租户事务锁将申请、审核与全店卡/新店关联串行化，当前正确性优先，吞吐量受该租户锁限制。合同、订单业务来源仍待接入。

## 合同模板与共享PDF范围

GymContractTemplateController→GymContractTemplateService→gym_contract_template/gym_file。总部保存与发布，文件引用冻结在版本内。GymFileServiceImpl读取增加有范围且有合同操作权限的已发布模板引用授权；GymContractTemplateMapper的TenantBaseDO查询保证共享引用不能跨租户。权限只针对已发布模板所引用的contract PDF，不用于上传或其他门店合同资料。实例合同后续应生成归属门店的独立受控文件，避免模板停用影响已发合同阅读。

## 合同实例及签约授权

GymCardService/GymCourseService→GymContractService内部hook（后者只依赖权益Mapper、Member/StoreService和FileApi，无反向Service依赖）。已配置相应类型发布模板时，发权益和生成合同/独立原件在同一业务事务；contract以source_type/source_id唯一。模板PDF独立复制至实例门店的gym_file，快照独立存储，模板停用不撤回实例文件权限。普通门店响应不暴露其他店原始权益快照。

GymApprovalService contract分支校验实例原件、审批范围，最终调用approveSigning保存授权；状态与电子签完成分离，无签名/回执即维持unsigned/unconfigured。GymContractReminder表以合同+opaque request key唯一，当前渠道未配置如实持久化失败，避免重放产生多条记录；没有外部发送行为。自动提醒和真实渠道仍待实现。


## 工资整月计算与冻结

GymPayrollService读取员工唯一整月已发布薪资配置，汇总本人跨店GymFinancialEvent支付/退款及confirmed GymLessonRecord，保存gym_payroll和gym_payroll_item模板/来源快照。支付事件目前仅有内部持久化模型，后续需由已验证支付链路写入，不以权益标价替代。门店锁→工资头→员工锁→薪资配置锁；计算先验证全部人员，再替换草稿；确认与薪资期间调整共享员工锁，确认后不可重算且配置覆盖不能缩短。READ_COMMITTED写事务避免等待锁后读旧配置/已删除项；详情使用只读REPEATABLE_READ保证头明细一致。来源汇总跨店，响应按来源门店权限遮蔽明细，SELF只返回本人薪资。


## 经营报表与看板

GymReportController的performance、members、dashboard共用GymReportService，来源为FinancialEvent、成功Checkin与授权MemberStore关系，使用REPEATABLE_READ获得一致统计。全部历史默认查询；显式日/月/起止按Asia/Shanghai左闭右开区间，最多366天；看板默认7天并补齐日期。金额从实际支付/退款事件归集，订单distinct，未指定员工归未归属组，分页不截断summary。会员在全店看板按memberId去重，单店按各店关系分别计数；当期待办用带租户条件的task/employee关联严格限定当前本人步骤。SELF不获得任何整店统计范围，员工薪资SELF由独立工资服务处理。尚无真实销售支付写入器，卡种售出/金额报表需后续订单项来源。


## 售卡、包月和核销条件

GymSalesSettingsController三类GET/PUT共同调用GymSalesSettingsService。gym_sales_setting以tenant/store/kind唯一，content保存严格VO序列化的完整业务配置，revision只在内容变化时增加。门店锁与READ_COMMITTED串行首次创建及替换，涉及卡模板时按ID锁定后校验适用范围/发布状态/月卡期限；失败不写入旧行。响应关联cardTemplateId及storeId为字符串。无记录仅读取关闭/空列表默认值。设置价格不修改卡模板；后续订单支付、包月执行及核销须读取配置并再次校验，而不是把enabled视为外部服务就绪。


## 收款账户与分账版本

GymSettlementController→GymSettlementService→gym_payment_account/gym_settlement_rule/gym_settlement。账户只保留lastFour与vault://、secret://、kms://引用，merchant/provider/归属不可PATCH；总部账户仅ALL范围可见。绑定目前未接支付厂商，保存unconfigured及明确未绑定原因，不设bound；已真实绑定的账户更换凭据会失效绑定，当前有效分账引用则禁止替换引用，须新增账户与规则版本。

规则以tenant/store/version唯一，门店锁串行分配版本和激活，接收账户按ID排序加锁。草稿必须精确合计1、账户不重复且渠道一致；启用须bound及凭据引用。启用内容冻结，新的非回溯版本生效时系统关闭旧重叠适用区间，旧比例/周期/账户快照保持。所有写事务READ_COMMITTED，避免等待锁后继续读旧状态。分账记录只读取持久化快照，未提供公开创建、伪造到账或设置settledAt的接口；可信支付/渠道回执生产者按用户授权待后续接入。列表日期过滤dueAt。


## 多店活动与加盟意向

活动及报名依赖独立gym_activity_store关系进行门店查询过滤，报名唯一tenant/activity/member，活动行锁保证共享容量不超售。取消保留历史，已有报名的日程与门店冻结；局部读取屏蔽跨店报名和不可访问封面。管理端接口用gym:activities:manage；无匿名代报名入口。

匿名意向提交使用protection starter及GymInquiryRateLimiterKeyResolver，按租户和实际连接来源限流，每60秒5次，不信任任意转发头或把正文放入限流键。Idempotency-Key与固定字段摘要保证持久化幂等；只返回id/received，后台完整联系方式须gym:franchise:manage及ALL总部范围。咨询处理状态不能回退。无短信或外部通知动作。


## 设备指令与器械教学

GymDeviceController按devices:read/write/command和equipment:read/write分离权限，所有父实体归属均检查门店与租户。设备唯一tenant/provider/deviceUid，门店、类型、厂商和编号冻结；online/lastSeenAt不接收后台编辑，须可信心跳生产者更新。

柜格按device/规范化编号唯一，移除只取消configured，恢复复用原ID以保留历史指令关联；占用或预留状态来自可信设备集成，后台不能重置。柜格PUT与命令请求共享设备行锁。命令以tenant/device/requester/key持久化唯一并校验内容摘要，当前无厂商接入统一failed及明确未发送原因，mock标识同样不写成功。不存在公开强写在线/开门成功接口。

器械按tenant/store/code唯一，教学只引用同店受控image或MP4 video文件，状态可启停。教程媒体访问继续走现有私有文件鉴权，不泄露infra URL、不放宽文件范围。厂商心跳/身份核验和指令回执生产者仍待资料补齐。


## gym可选独立存储

FileApi/FileService增加显式configId重载，四参数API仍选master；显式配置不存在时失败，不回退。GymFileServiceImpl上传和GymContractService复制原件从后端属性gym.file.storage-config-id选择目标，不接受客户端传存储ID。本地run-local.sh选现有DB存储4，已验证全局master仍22。内容/文件名/类型校验复用infra原路径，读取依infra_file配置和gym_file鉴权，新文件存取与既有全局调用互不改变；不迁移旧S3对象。


## 客户端内容与场景二维码

GymContentController→GymContentService→gym_client_setting/gym_qr_code。客户端配置按tenant/scopeKey(总部0或门店ID)/platform唯一，PUT替换完整草稿；SELECT FOR UPDATE及唯一键处理并发首次创建，内容规范化后相同PUT不增revision。GET默认值不落库、不隐式继承总部内容。内容管理权限与门店范围共同约束，省略storeId须总部ALL。

私有封面/区块/HTML图片均检查gym_file租户、类别/MIME和同店归属，总部配置允许引用当前租户文件。正文通过Jsoup标签、样式与URI白名单清洗；gym-file:id仅在确认图片可访问后保留，受控预览链接支持图片/MP4。外部图片、跨店隐蔽引用、带用户凭据的HTTP地址拒绝；不把blob或外部地址存成私有文件。

二维码按tenant/store/platform/sceneType/sceneKey唯一，门店行锁串行重复请求。课程场景只能引用同店实际排课；渠道、App路由未配置时落failed与failureReason，providerCodeFileId为空，不生成占位二维码。读写分别沿用现有前端stores:read/write权限，所有读取和写入检查门店范围。后续真实厂商接入须替换TODO-INTEGRATION-CLIENT-CODE并验证真实可用图片。


## 交易、包月与核销记录读模型

GymSalesRecordsController→GymSalesRecordsService→订单、订单项、支付、退款、包月协议/周期和核销共七表。统一gym:sales:read，所有父实体按租户/门店检查，子明细先检查父协议或订单；服务REPEATABLE_READ保证详情聚合一致。响应不暴露渠道支付号、协议号、券引用或请求幂等键。业务ID为字符串、金额为元两位小数、时间为Instant；订单项保留名称和版本快照。

自主办卡查询只接受miniapp/app且businessType=card_sale，后台/线下和课程订单不可借此读取。订单/协议列表分别按orderedAt/requestedAt筛选，核销按结果记录verifiedAt筛选，北京日期左闭右开；包月周期按periodStart北京时间零点匹配范围，非整日Instant边界向上取日期。尚未扣款周期chargedAt/orderId允许空，未授权协议signedAt/nextChargeAt允许空，不推算出成功扣款。

七表唯一来源和请求键供后续可信写入器幂等；当前仅读，无管理端修改付款/授权/验券状态接口。交易写入、权益/财务事件衔接、代扣授权及核销执行仍未实现，不能从空读模型推断闭环完成。


## 内部订单准备与支付退款财务写入

GymCommerceService只供后续已认证购买/厂商适配流程调用，未增加管理端成功写入接口。prepareCardOrder校验任职门店范围、会员归属/黑名单、已发布卡模板及当前启用/时段内在线报价，价格只取服务端配置。订单项固定名称/版本/数量/价格，gym_order_policy固定报价/模板/设置版本及合同审批规则；请求键+内容摘要保证重复请求保持原价，参数变化拒绝。零元订单进入pending_fulfillment，无支付或财务流水。

GymPaymentReceiptVerifier是独立服务端端口，只能返回商户凭据核验后的终态成功交易，需解析商户租户归属及订单号。默认Unconfigured实现抛1070020003；GymPaymentConfiguration仅在无真实适配器时注册该实现。厂商查询在租户业务互斥锁之前完成，先检查调用者门店权限，随后锁订单并再次验证凭证租户/来源/币种/金额/时间。没有接受客户端成功正文的API，也没有匿名回调。

acceptPayment/acceptRefund分别按渠道交易号幂等，金额不得超订单应付/对应原支付剩余额度；事务同时保存支付/退款、订单汇总与gym_financial_event，任何写失败全部回滚。退款财务事件是正额refund，由报表/工资按既有规则相减。分笔支付最终一笔完成前为partially_paid；退款保留原流水，不改历史收入。自助订单无默认销售员工归属，财务事件employeeId为空。

该服务尚未发放或撤销权益；付款成功只表示真实金额，不等于履约完成。orders审批、自动履约/合同要求、退款后的权益处理、公开购买与认证回调接入、包月/核销仍需后续完成。真实厂商缺资料按用户授权TODO-INTEGRATION-PAYMENT；H2成功凭证mock不进入生产。


## 订单开卡审批与购买快照履约

GymApprovalService的card_opening支持sourceType=orders/sourceId；拒绝同时提交memberId/cardTemplateId/requestedPrice，来源身份和金额由GymCommerceService.openingSnapshot解析。仅足额付款且零退款的订单或合法零元待履约订单可发起，门店必须一致；最后一步再次锁定并检查，付款后退款也不能继续发卡。

fulfillApprovedOrder/fulfillAutomaticOrder共用全局租户业务互斥与订单行锁，按订单项ID+单位序号作为卡权益来源，数量1..99，订单策略fulfilledAt和关联卡ID同事务提交。重复同一履约返回原结果，其他审批或不满足冻结approvalRequired的自动入口拒绝。自动方法当前仅供内部调用，尚无支付后自动任务触发。

GymCardService.issuePurchased仅接受后台持久化的购买模板快照且匹配租户，复用原发卡/会员门店/编号/合同hook；不因当前模板停用丢失已购版本。普通issue仍要求当前模板已发布。合同必需时GymContractService.requireForEntitlement要求真实适用模板及独立原件；失败回滚新卡、策略、订单和最后一步审批，先前已提交真实支付不回滚。合同实例仍unsigned/电子签unconfigured，未伪造签署。订单详情补充冻结审批/合同规则、fulfilledAt、关联memberCardIds，不暴露内部条款JSON。

退款后的权益规则已向用户询问，尚待答复；当前仅已验证的退款财务事实入账，不能宣称自动撤销/部分退款权益处理完成。


## 用户确认的退款权益规则

用户确认“全额退款停卡；部分退款人工处理”。GymCommerceService在已核验退款金额及财务事件同一事务内调用GymRefundReviewService.onRefund。累计退款等于已付款时，按订单项持久化关联卡停用，校验member/store及order-item来源单位，其他订单/手动发卡不受影响；历史使用、合同与财务记录不删除，不恢复已用次数。停卡立即使requireCard拒绝使用，既有预约保留历史/预留，由有权门店按原取消流程处理，不越权读取或操作其他门店预约。

每笔部分退款创建gym_order_refund_review pending任务，不自动改卡权益。订单详情及GET /self-service-card-orders/{id}/refund-reviews返回任务，POST /{id}/refund-reviews/{reviewId}/decisions要求sales:read与approvals:decide及门店范围，必须附人工说明；retain不改卡，stop_selected仅停用本订单选中卡。决定含操作者、时间、选中卡，重复相同决定幂等，不可覆盖；全额退款关闭尚pending的任务为closed_full_refund，已处理历史保留。人工动作不发起渠道退款或改财务金额。

本轮接用户答复优先完成退款规则，自动履约队列仍待下一步，未引入新菜单或修改现有前端。


## 持久化自动履约队列

GymCommerceService下单时为冻结approvalRequired=false的订单同事务创建gym_fulfillment_task，正价为waiting_payment，零元为pending；足额付款和任务激活同事务，避免收到成功回执却丢失发卡任务。退款取消尚未完成的任务；已成功任务保留历史，权益按用户确认的退款规则处理。

GymFulfillmentTimer私有拥有一个daemon ThreadPoolTaskScheduler，默认启动30秒后、每批完成间隔30秒扫描；不是Spring TaskScheduler bean，不替换或占用默认/Quartz执行线程。gym.commerce.fulfillment.enabled=false可停定时驱动，interval-ms>=1000，initial-delay-ms>=0。GymFulfillmentScheduler从TenantFrameworkService取得租户，使用TenantUtils隔离上下文，过期/禁用/不存在租户正常跳过；每租户每批最多50个到期任务。

QueueService用新事务和FOR UPDATE领取，processing携带UUID claimToken和120秒租约；超时允许接管，旧token不能覆盖新结果。履约本身仍在独立业务事务、租户互斥和订单行锁下幂等，权益/策略/订单/任务succeeded一起提交。失败原因安全落库，下一次1/2/4/...分钟退避、最多1小时，初始8次；耗尽不继续自动重试。有权人工重试增加8次预算，累计attemptCount保留。

Worker仅使用下单时服务端获取的原发起后台账号，重新读取账户状态、租户、sales:read和实际门店权限，不提升范围；失效时失败留痕，后续有权人工重试可记录当前操作者承接。执行前后的SecurityContext与TenantContext恢复，不接受客户端tenant/principal字段。当前内部下单仍是后台类型2身份，未提供会员购买认证/厂商回调入口。

GET /self-service-card-orders/{id}/fulfillment-task受sales:read；POST /{id}/fulfillment-retries还要求approvals:decide与订单范围。详情仅返回状态、次数、到期时间和安全失败原因，不返回principal/claimToken/lease。人工开卡申请与任务暂停同事务，waiting_approval阻止已领取执行器越过人工审批；请求失败回滚暂停。人工拒绝后仍待人工重新处理，不自动越过拒绝决定；批准后原子完成。


## 会员卡报表与逐订单项资金事实

GET /gym/reports/member-cards复用reports:read、租户和门店范围。GymCardReportMapper在SQL中显式限定父子tenant/store/order关系；ReportService合并可见发布/停用模板与历史订单/权益快照，以模板版本分组，分页前计算汇总。历史现金没有订单项分摊时使用unattributed分组保全总额。

GymCommerceService在财务事件同事务写gym_order_item_financial_event。GymMoneyAllocation以BigInteger整数分按权重做累计最高平均数分配，相邻累计结果之差为本笔金额；分笔收款/退款无负分摊且全额退款完全冲回，无浮点误差。订单项按id固定顺序处理，平手稳定；失败回滚整笔付款/退款及订单、任务、权益变更。

销量按足额付款完成日期统计商品数量，退款不逆向删除销量；零价须已履约。净收按实际事件日期、付款减退款。持卡人数按当前有效且已开始未到期的权益/剩余次数、在所选门店组按会员去重；不同模板行不可直接视作全局去重。课消按确认时间及历史卡来源计数，不受后来停卡/退款影响。人工开卡仅影响真实权益/课消，不产生销售现金。


## 连续包月来源签约审核

现有前端contract审批提交sourceType=subscriptions、协议sourceId。ApprovalService从服务端协议生成/复用sourceType=subscriptions的独立合同原件，并自动纳入附件；快照中的sourceId固定合同、subscriptionId固定协议。金额、周期日、商品、会员、门店、渠道和请求时间使用白名单快照，不带providerAgreementId。最终审批同时复核协议状态及冻结条款；普通合同来源批准同样复核，避免绕过。合同生成与审批同事务，失败不留下合同/附件数据库行。

只允许未授权且未解约的pending/pending_authorization/unconfigured/failed来源。审核仅允许签约，不产生平台授权、周期付款或发卡。仍用发布的card合同模板，公开POST contracts仍限定原来的member-cards/course-packs，包月来源由审批内部调用。没有真实厂商签约适配，合同保持unsigned/unconfigured。


## 冻结合同配置的自动未签提醒

GymContractReminderTimer私有线程gym-contract-reminder-*默认启动后5分钟、每5分钟驱动一次，不替换或占用应用默认调度器。Scheduler验证租户有效性，TenantUtils隔离上下文，按ID游标每批100行扫描未签合同，避免首批未到提醒期阻塞后续记录。每笔Worker独立事务锁合同，复核未签未取消、会员/门店active，从合同冻结templateSnapshot读取reminderDays；北京时间到期日提前N天00:00进入窗口，逾期未签仍在窗口。

沿用gym_contract_reminder按tenant/contract/requestKey唯一约束，auto:北京时间日期:channel键、合同锁和同事务事件使跨实例/重启当天同渠道最多一次。人工接口禁止auto:前缀，不能抢占计划。系统任务不伪造登录账号，requestedBy/actorUserId和creator/updater均显式0。签署/取消时间即使状态字段滞后也阻止自动提醒；最终签约批准亦增加这些时间校验。

通知厂商未配置，自动尝试落failed并明确未发送，不传输消息、不产生授权或签署。渠道可选app/miniapp/sms，默认app；未来真实适配必须验证会员绑定/授权和真实送达，当前不宣称推送接入。


## 负责会员关系与SELF范围

新增gym_member_coach(tenant,member,store,employee唯一)，active撤销保留原行和审计。GET/PUT /gym/members/{id}/stores/{storeId}/coaches由members:write及原门店数据权限双重保护；替换校验本门店会员、在职员工、任职及当前有效同租户后台账号。SELF只能受益，不能借关系维护接口自赋权限。

GymMemberCoachService组合部门范围与SELF负责关系，保留GymDataScope不变。SELF必须LoginUser.type=2、PermissionApi.self=true、用户启用且当前租户、员工active、任职存在、会员门店关系存在且店active；每次读取重查，不把负责会员的门店纳入通用readableStoreIds。predicate(memberColumn,storeColumn)只接内部固定列名及服务端Long，显式会员/门店对限制共享会员的其他门店记录。

会员列表的归属、权益筛选，会员详情与卡包，体测/合同查询与详情，预约列表/详情、取消、签到、签课及记录/汇总已接统一范围。通用课程/排期管理仍走部门范围；会员业务内部用memberSession校验特定会员门店，不能借预约ID跨会员操作。体测自行关联报告还须上传者为当前账号；文件内容服务仍维持原权限，尚未开放SELF下载/上传。

运行库专用QA角色175、账号257(gymselfqa)、员工9由用户明确授权创建，role.data_scope=5，分配member20/store125，其他既有角色不改。授权范围仅本次会员/卡包/合同读取、体测写入及负向验证所需会员写权限；不是迁移示范授权61条的一部分。浏览器测试后恢复admin。自动未签提醒当前恢复默认300000ms扫描，先前5000ms仅为QA覆盖。


## 负责会员附件的实时读取授权

GymFileServiceImpl在既有门店范围之外调用GymMemberFileAccess。SELF读取contract需contracts:read及实际合同unsignedFileId/signedFileId关联，读取body_report需measurements:read及实际体测reportFileId关联，均复核当前负责会员/门店。files:read仍由controller要求；不向响应暴露存储URL。任何同租户绑定落在不可见会员范围，整个文件拒绝SELF读取，避免共享附件泄露；未关联文件、其他类别、纯SELF的无门店历史文件均拒绝。元数据和内容共用getFile，每次请求重新检查，撤销后原token也无法获取字节。

体测更新允许保留已关联同会员同店的管理员报告，拒绝把其他会员报告绑定过来，即使上传者相同；未关联报告仍要求本人上传。黑名单列表改为统一member/store谓词，详情和列表一致，变更仍总部权限。用户另行明确同意专用gym_self_qa增加files:read，未授上传权限。


负责会员体测报告上传现补充GymMemberFileAccess.canUploadReport：仅body_report类别、measurements:write及目标门店当前存在有效负责关系可使用SELF上传例外；controller还要求files:upload。仍校验门店存在和真实文件类型/大小并保存当前上传者。上传不授予文件读取权，须随后通过会员范围校验的体测记录绑定；其他类别、其他店及撤销后不允许。原总部/部门上传范围兼容，没有修改QA角色上传权限。


## 会员训练计划与本人授课选择

SQL45新增gym_training_plan，保存会员、门店、标题、纯文本内容、受控附件、active/archived及标准租户/创建修改审计。TrainingPlanController复用measurements:read/write档案权限，Service按MemberService会员/门店谓词处理分页、详情、创建和锁行修改。会员及门店不可更换；内容和附件至少一项；显式fileId:null可移除附件，省略保留。没有硬删除接口，归档仍保留历史。

训练附件类别training_plan允许20MiB内PDF/PNG/JPEG。上传需files:upload及measurements:write、有效负责门店或原部门范围；读取需files:read、measurements:read和实际计划绑定、当前负责关系。共享到不可见会员的文件对SELF整体拒绝；SELF不能重绑其他会员附件，自己的未绑定上传或同会员同店已有绑定可关联。文件通过infra内部存储，业务响应不暴露URL。前端未增加训练计划页面。

用户已确认SELF预约仅本人授课。MemberCoachService.coachPredicate/checkCoachScope按部门范围OR当前负责关系中的本人employee+store限制课程、排期和教练选项；读取详情也复核，响应门店名不借通用整店查询。创建预约在会员/权益范围之外额外校验授课员工。课程创建/排期修改仍保留原管理范围，不把负责关系变为门店管理权。负责人变更实时影响选择，不影响历史业务审计。当前负责关系联查新增北京时间hired_on/joined_on已到日期检查，避免未来任职提前获权。


## 私有存储原始URL封堵

审查复现原始infra下载URL可匿名返回训练PDF（HTTP200/614字节），仅隐藏URL不足以满足附件隔离。新增infra通用FilePrivatePathPolicy，服务端配置yudao.file.private-path-prefixes=gym/。公共FileController下载先解码并验证安全相对路径，私有前缀直接HTTP403，不调用存储、不依赖登录角色；业务GymFileService仍在校验当前会员/门店范围后经内部FileApi读取。业务响应继续不返回原始URL。

FileService在上传前复用该策略，私有前缀仅允许DBFileClient，避免直接公开的对象桶/静态目录绕过应用鉴权。私有前缀禁止前端直传预签名及FileCreateReqVO外部URL登记，业务后端上传的真实字节仍可落DB。非私有目录沿用原存储行为。当前run-local.sh选择配置4数据库存储，master22不变；不配置专用DB而落到外部主存储会明确拒绝，不能静默降级。

配置在yudao-server/application.yaml中生效；独立复用infra的其他部署默认无私有前缀。gym部署须保留gym/。历史外部S3地址无法由应用下载入口撤销；本地库有18条此类附件，需要在原存储侧修复/撤销访问或通过原件重新导入。此修复只保证受控DB及后续私有上传，不宣称已修复历史外部桶。


通用admin/app infra上传入口另在读取字节前校验目录，gym及gym/子目录均必须走业务上传，不因全局master日后切DB而放行；业务内部DB上传保持可用。


### SELF 改价审批范围与来源（SQL46）

GymApprovalService允许纯SELF通过当前responsibilities/本人授课产品发起price_change，服务端冻结sourceCoachId。本人申请列表、详情与幂等重放都重新检查有效关系；其他申请人同产品申请也不可读。审批模板选项只发布改价类型，SELF附件拒绝。决定仍受原门店范围、指定当前步骤、员工/账号及decide权限约束。创建时模板指定审批人的账号有效性检查仅对getUser忽略申请人的部门过滤，租户隔离和显式tenantId核对仍生效。SQL46同店同租户回填来源且不覆盖已有值。

嵌套VO使用父VO限定的OpenAPI Schema名称，避免工资、分账、销售、配置Item及审批Step等重名覆盖；实际JSON格式不变。前端SELF申请门店选项仍依赖整店stores列表，接口正向与浏览器发起表单受限需分别记录，不扩大store资料范围。


### 教练生命周期和权限登记补正

预约按教练→会员→排期→权益锁序，入锁前做会员/排期/本人授课范围授权，当前在职/任职生效复核。排期取消取得教练锁后不要求仍在职，原管理权限及已签课保护保留，取消/预占释放同事务。

SQL47在黑名单页面下新增现行单数编辑权限；用户明确授权后，只给已有旧复数编辑权限且全范围的tenant1示范总部角色映射，门店/SELF及已撤旧权者不补发。旧菜单和账号/数据范围不改。


## gym 登录菜单分类（2026-10-16）

`AuthController.getPermissionInfo` 先沿用既有用户、有效角色、菜单授权和禁用祖先过滤，再由 `AuthConvert` 生成权限结果。新增可选扩展 `AuthMenuTreeCustomizer`（通过 `ObjectProvider.orderedStream` 调用），仅接收并返回菜单树，不接触权限集合。gym 模块提供 `GymAuthMenuTreeCustomizer`，system 模块不依赖 gym；未启用 gym 的部署没有该适配器。

分类以用户提供的 `gym.ts` 为依据，定义保存在后端适配器中：门店、员工、会员、会员卡、课程、合同、审批、设备、优惠券、积分、消息、内容、加盟、财务、报表，共15个分类。只组织输入中已经授权的真实页面，不凭分类配置创建业务页、不按旧 `hq/store` 字样重新授权。当前35业务页为首页加12个非空分类；优惠券、积分、消息无已交付页面，因此不返回空目录。加盟管理及二维码、员工薪资关联、门店工资表等隐藏规则沿用附件；原有隐藏状态也不会被提升。其他系统菜单和未识别的人工菜单保留。

数据库 `system_menu` / `system_role_menu` 继续作为授权事实，保持原有扁平业务菜单及全部迁移脚本，无菜单迁移、授权补丁或新增业务权限。分类目录及原地址兼容入口只存在于认证响应，使用避开现有ID的负数展示ID，不能作为数据库菜单ID提交授权。`MenuVO.sort` 由既有 `MenuDO.sort` 映射，分类顺序由适配器设置，客户端据此排列。

现有 JSS 转换器无条件拼接父子路径，所以分类入口例如 `/gym/training/course-products`；后端同时返回隐藏的 `/gym/course-products` 原地址入口。报表目录与旧前缀一致，使用 `performance`、`members`、`member-cards` 作为子路径，不重复注册旧地址。首页保留 `/gym/home`，改为“首页”。完整HQ响应含35种业务组件、31个额外隐藏兼容入口、12个目录；不增加接口或数据访问权限。

客户端限制：当前首页通过可见菜单的固定原地址查找快捷入口，隐藏兼容入口不会进入它的可见菜单集合，因此分类后部分首页快捷卡片消失；直接访问原地址仍有效。旧地址与分类地址的路由名称不同，同一页可能显示两个页签，旧地址也不会自动展开新分类。这些现象无法仅通过当前客户端接受的菜单字段同时消除；本次严格不改前端。管理端“菜单管理”仍展示数据库授权树，和认证接口的业务分类展示树用途不同。


## Flyway版本迁移与独立开发seed（2026-10-16）

当前发布链：框架基座一次性导入 → `script/gym/db.sh init`（新gym环境）或 `adopt-legacy`（完整旧gym库接管一次） → 后续每次发布 `db.sh migrate` / `validate` → Java应用启动。Java应用和H2单测不加载Flyway依赖；数据库工具是独立 `sql/gym/pom.xml`，固定Flyway13.10.3、MySQL支持模块和Connector/J9.7.0。H2建表资源仍只属于测试上下文，BPM当前禁用。

`db.sh → database.py → 独立Flyway Maven插件 → sql/gym/migration/mysql`，历史固定为 `flyway_gym_schema_history`。V001是59表结构，V002只登记system_menu中的菜单/权限，V003执行必要历史回填；没有账号、角色、角色/用户绑定写入。新gym环境已有框架表，所以显式baseline0后迁移；不会自动baseline非空gym库。旧库先以绑定V001校验和的legacy-schema.json核对59表的列/默认值/字符集/排序规则、索引及可见性、约束、外键与引擎，再完整mysqldump备份，成功后baseline1并执行V002/V003。结构不符或备份失败不接管；失败的MySQL DDL不能假设自动回滚。

现有开发库已经接管至003：历史记录为1 BASELINE、002 SQL、003 SQL。接管前三类必要回填数量均0；接管后6张系统表（菜单、角色、授权、账号、部门、用户角色）的完整行数据哈希一致，既有密码、数据范围、授权和页面菜单均未变化。完整备份在 `/tmp/gym-db-backups/ruoyi-vue-pro-before-flyway-20261016-205827-111751.sql`，权限600。默认备份目录可通过GYM_DB_BACKUP_DIR改为持久路径。

`seed.sh development → database.py seed-development → sql/gym/seed/development/demo.sql` 是独立开发入口，不在Flyway扫描目录。必须显式声明开发环境、目标库和租户，且当前版本全部已迁移、校验通过；只对本次新建演示角色初始化菜单授权、本次新建演示账号初始化绑定，既有撤销授权/绑定与密码不恢复。同名非种子或已删除身份冲突通过临时guard和事务拒绝，回滚相关写入。迁移不执行该seed，生产发布不生成演示账号。

旧47个SQL原样移动到 `sql/gym/legacy` 并固定sha256，旧开发导入在legacy/development；旧全量、schema-only、menus-only重放入口停用。当前verify-migrations.py使用真实Flyway和随机MySQL库验证；历史验证在verify-legacy-migrations.py/verify-legacy-seeds.py独立保留。新增GitHub数据库CI执行同一隔离回归，当前只完成本机验证，尚未在远端运行CI。后续追加V004等新版本，不改已有迁移；legacy-schema.json只用于旧基线接管，不代表未来最新结构。
