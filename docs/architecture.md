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
| `gym → system.api` / `infra.api` / `member.api` | 跨模块**只能**调 `api` 包下的接口（如 `FileApi`、`PermissionApi`），不碰对方的 `service`/`dal` |
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

Worker仅使用下单时服务端获取的原发起后台账号，重新读取账户状态、租户、sales:read和实际门店权限，不提升范围；失效时失败留痕，后续有权人工重试可记录当前操作者承接。执行前后的SecurityContext与TenantContext恢复，不接受客户端tenant/principal字段。原后台内部下单仍是后台类型2；会员购买和类型1执行分支见V022增量，厂商回调尚未接入。

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


## 小程序会员身份与本人数据边界（2026-10-23）

`yudao-server → gym → member`：根 reactor 启用 member 模块，gym 仅通过 `MemberUserApi` 获取已认证账号手机号及状态。member 的 `/app-api/member/auth/**` 保持原路径；员工身份仍使用 system。模块装配不等于所有通用 member 入口已具备表和部署配置，本批仅迁移所需 `member_user`，其余入口须在部署前核查开放范围。

小程序 `/app-api/gym/me/**` → `GymMobileIdentityService.requireMemberId` → 校验会员类型令牌、令牌租户及账号状态 → `gym_mobile_identity` → 当前租户 `gym_member`。本人条件在 Mapper 查询和分页计数前加入，不将会员 ID 冒充后台用户 ID 调用 `GymDataScope`。历史档案查询不以当前卡有效、门店活跃作为前提。

首次初始化：事务 → `GymMobileIdempotency`（租户 + 用户类型 + 用户 + 操作 + 请求键唯一）→ 行锁/请求体摘要 → 已有 `GymBusinessMutex` → 手机号绑定或创建 gym 档案/门店关系 → 同事务保存响应。相同请求重放原结果，改变参数报冲突；异常整体回滚。唯一关联约束避免并发账号认领同一档案。来源及邀请码只留存，未实现邀请奖励。

`GymMobileRecordsService` 投影本人档案、签到、合同和运动汇总；`GymMobileEntitlementsService` 投影本人会员卡、当前适用门店及课包余额。卡的过期/待生效/耗尽为查询时计算，状态过滤先于分页，不为读取修改历史行；退款形成的 `inactive` 原样保留。公开响应不暴露整份持久化对象或内部合同证据。

会员文件：`GymMobileFilesService → FileApi` 写入既有 `gym/` 私有存储路径 → `gym_file` + `gym_mobile_file_owner`。文件 uploader_id 属于后台账号，因此会员上传不填该列，归属只写新表。图片签名、用途与限额校验在存储前；下载校验本人真实业务引用，人脸原图拒绝。既有后台通用文件服务拒绝 `member_` 类文件，避免仅凭门店权限绕过归属。

`GymMobileWebConfiguration` 拦截通用 member 两个换手机号入口，防止绕过健身业务的手机号不可变要求；`GymMobileExceptionAdvice` 仅作用于移动 Controller 包，处理缺失幂等头及 JSON 字段拒绝异常，不改变共享框架异常处理。V005 追加会员账号表及 3 张 gym 表，gym 表总数变为 62，未修改历史迁移。第三方支付/自动续费、签约、设备、监控按用户要求暂缓，见 [实施边界](uniapp/uniapp-implementation.md)。


## 小程序预约与共享预占规则（2026-10-23）

`AppGymBookingsController → GymMobileBookingsService` 从会员令牌取得本人，写请求在 READ_COMMITTED 事务中先锁持久幂等请求，再按教练 → 会员 → 排期 → 预约 → 权益顺序预占；既有后台 `GymCourseService.createBooking` 同样持有会员锁。两端共用 `GymBookingRules` 的教练任职、课包适配、时间冲突、预占和取消释放规则，避免后台与会员端并发重复占用同一会员时段。取消按排期 → 预约 → 权益锁顺序释放；异常使状态、计数、幂等结果一并回滚。

写预约只接受课包和排期ID，服务端验证产品、门店、本人归属、完整有效期、状态和剩余可用课时；读取可约状态不代替写入时重查。列表使用本人谓词和课程开始时间筛选，关联排期、签到、课消批量查询。后台状态attended投影为completed；实际签到/课消关联再校验本人和门店，不直接信任某个关联ID。

`GymMobileBookingProperties` 绑定 `yudao.gym.mobile.bookings.cancel-before-minutes`，默认0且范围0～10080，限定会员自助取消时间；不改变后台管理员处理历史预约的权限，也不触发自动爽约扣课。V006增加独立取消原因和课消时余额列：后台原确认服务在同一扣课事务中保存余额，新查询直接读取快照，旧记录与不限次数卡保留NULL。会员QR签课及设备签到尚未接入，不能将本次历史查询当作对应写流程已完成。


## 移动工作台档案、跟进及分配（2026-10-23）

`/admin-api/gym/mobile` 的两个Controller先执行现有业务功能权限，再进入 `GymMobileWorkAccess`：要求admin用户类型、令牌与上下文租户一致、AdminUserApi返回的账号有效，随后使用 `GymMemberService` / `GymMemberCoachService` 检查门店与会员负责关系。该检查在幂等响应读取前执行；调岗或撤销负责关系后，旧成功请求键也不能返回已失去权限的资料。

`GymMobileWorkRecordsService` 复用既有体测、训练计划及合同创建服务，移动响应投影为显式公开字段。合同列表要求指定会员/门店并验证范围后分页，签署中/作废状态转换在SQL过滤前完成；合同创建另查实际权益与目标会员/门店一致，仍由原合同服务冻结模板和生成私有原件，不接入外部签署。没有把旧后台完整VO中的IP、设备和事件暴露给移动调用方。

`GymMobileWorkMembersService` → `gym_member_followup` 保存员工跟进，作者必须是当前账号关联的唯一在职员工，不接受客户端employeeId。分配使用持久幂等键 → 原有租户业务锁 → 会员/门店关系行锁 → 比较assignment_version → 复用原负责关系替换 → 返回新版本。`GymMemberCoachService.replace` 在实际集合变化时递增版本，未变化不递增；旧后台同样维护版本，所以移动请求能检测后台先发生的变更。旧后台原接口仍无expectedVersion参数，保留原整体替换行为，不能宣称所有旧客户端也获得乐观锁保护。新员工需已到入职/任职日期且账号有效。

V007追加跟进表及负责关系版本列，gym表总数63。旧关系从版本0开始，不重写员工授权；工作台会员列表/详情已提供assignmentVersion读取入口。


## 移动工作台会员读取（2026-10-23）

`GymMobileWorkMembersController → GymMobileWorkMemberQueryService` 使用`gym:members:read`、`GymMobileWorkAccess`后台身份及`GymMemberCoachService`现有门店/SELF谓词。会员SQL通过实际门店关联的EXISTS过滤权限、负责员工及有效权益，再统计和分页，每位会员仅一行。省略门店时选满足全部条件的最小可见门店ID，避免一行混合多个门店的负责关系版本；详情必须指定门店。

列表和详情采用只读REPEATABLE_READ事务，关联负责关系和门店版本来自同一快照；批量读取本页会员相关卡、卡门店、课包和负责关系，再投影选中门店的有效权益合计。查询不改变权益状态，不把all卡旧快照当作当前适用关系。手机号只输出脱敏值；内部DO、操作人及其他门店数据不进入响应。筛选参数通过绑定传入SQL，客户端不能提供列名或SQL片段。无需新增表或迁移。


## 小程序公开展示与素材发布引用（2026-10-24）

`AppGymPublicController → GymMobilePublicService` 为5个公开目标路由：租户上下文必需，读当前active门店；列表位置排序在SQL分页前，关联环境素材批量投影。首页从原`gym_client_setting`读启用的miniapp配置公告，并分别从miniapp/app生成封面、加盟模型及下载信息。原`GymContentService`及配置VO新增可选结构校验，保留原权限和整份JSON替换语义。全局回退仅在门店配置不存在时发生，禁用或无效覆盖不会重新发布总部内容；没有新表。

公开人数从`gym_checkin`计数上海当天成功且不在未来的已登记入场，保留当前真实来源的局限。无完整进出事件与设备健康证据时实时人数null、状态unavailable，更新时间为最后成功入场；不以人数查询启用外部设备功能。首页支付、续费、电子签署、门禁和监控能力固定关闭。

`AppGymPublicMediaController` 是目标7–11的辅助资源路径，不额外计入135。素材只输出本站受控URL，读取时重查公开引用与文件归属/类别/内容魔数，底层调用FileApi；已启用全局配置也属于公开引用，所有引用撤销后才不可读。返回Resource支持真实Range，禁用缓存，不暴露原始存储凭证。会员业务文件类别不允许从这个匿名入口读取。

`GymPublicMediaTenantFilter` 在原TenantContextWebFilter前，仅对精确公开素材GET URL的单个合法query选择器补租户header，不替换已有header，也不处理其他API。原TenantContextWebFilter仍设置和清理上下文，TenantSecurityWebFilter继续验证租户有效性、到期及登录租户一致性。过滤器注册在gym模块，无共享框架改动；浏览器可直接显示图片/视频链接。

### 公开商品目录与版本（2026-10-24）

AppGymCatalogController → GymMobileCatalogService → 门店、模板、销售配置、私教商品及有效任职关系。卡/包月在受限配置集中过滤后分页，私教在SQL中先筛有效商品/门店/任职再计数分页；只读REPEATABLE_READ保持响应售价与配置版本来自同一快照。响应不暴露员工手机号或内部实体。公开门店复用GymMobilePublicService规则。

卡/包月返回模板version和门店saleRevision，私教返回V008的product.version。原GymCourseService在行锁下对实际资料变化及审批通过的价格变化增版，创建从1开始；无变化保持版本。续费条款和协议版本复用原销售配置JSON，成对填写，缺失时关闭公开包月展示。目录发布状态与购买能力分别返回；未实现下单、未接入自动续费不会伪装为可购买。

### 教练公开资料（2026-10-24）

原GymEmployeeController写权限及全部共享任职范围 → GymEmployeeService → GymCoachPublicProfiles校验并保存V009的coach_published/coach_profile。默认旧员工保持私有，提供可选publicCoachProfile对象时整体替换，其他员工编辑保持原公开设置。未引入自动角色授权。

AppGymCoachesController → GymMobileCoachService → 同一公开条件查询（明确发布、有效员工/任职、营业门店、同租户） → SQL分页 → 公开字段。只读REPEATABLE_READ保持资料和图片引用快照。GymMobilePublicService媒体下载增加对公开教练引用的检查，复用原图片类型、实际内容校验及租户过滤；没有新辅助路由。公开图片须仍属于有效任职营业门店，撤销最后一个有效引用才失效。

### 本人教练资料编辑与草稿预览（2026-10-24）

GymMobileCoachProfileController认证 → GymMobileWorkAccess后台账号及唯一员工 → GymMobileCoachProfileService检查有效任职及教练资料/私教商品 → 稀疏字段合并。PATCH使用READ_COMMITTED和员工行锁，取得锁后核对账号绑定及有效状态，更新仅包含coach_profile和审计字段；不写发布标志、姓名、账号等员工字段。首次草稿沿用V009默认不公开，无新增DDL。

本人Media使用同组鉴权预览辅助路由，读服务重查资料绑定、当前任职和image类别，再从FileApi取真实内容并校验魔数；不接入公开媒体query租户选择器。新关联文件须由本人后台账号上传，管理员原有绑定可保留；读取不要求扩大为整店文件权限。普通员工不因职位名称获得教练资料能力，通用工作台上传仍待完成。

### 工作台文件上传用途与目标（2026-10-24）

GymMobileWorkFilesController文件功能权限 → GymMobileWorkFilesService有效后台身份 → 按用途检查本人教练/会员/整店范围 → 类型和大小校验 → FileApi私有命名空间 → gym_file与V010 gym_mobile_work_file同事务持久化。请求头不作为multipart字段绑定，客户端不能指定上传人或员工。gym表数增至64。

GymWorkFileBindings由移动文件服务及原体测、训练计划、公开教练资料、合同模板服务共用；新增上传不能换会员、换员工或将会员合同材料变为公共模板。历史文件无目标记录，原逻辑保持。下载区分未绑定材料与真实业务绑定，复用既有SELF范围检查；教练图片按员工而不是仅上传账号归属，账号重绑不能接管另一员工的图片。

原上传入口和角色授权未变。事务只能回滚数据库元数据，外部FileApi已存对象需要另行清理；尚无自动清理接口。此边界有回滚测试，不能将数据库原子性表述为跨存储事务。

## 工作台身份与权限上下文（2026-10-24 增量）

`GET /admin-api/gym/mobile/context` → `GymMobileWorkContextController`（已认证）→ `GymMobileWorkContextService` → `GymMobileWorkAccess.requireAdmin`（后台令牌、租户、账号启用）→ system `PermissionApi.getUserPermissionInfo` 返回真实启用角色及可展示菜单权限；gym只保留`gym:*`。新增跨模块入口位于api包，gym不直接读取系统角色、菜单表。system内部复用既有Role/Permission/MenuService，忽略部门数据过滤以读取本人权限，租户隔离保持；不改原授权或菜单接口。

Gym上下文在只读事务内读取当前有效员工、整店范围、有效SELF关系和本人有效任职，查询当前租户营业门店形成入口列表。员工可为空；门店入口不是整店授权，尤其本人任职不扩展会员负责范围。每个业务接口仍独立鉴权，无新增数据库表、权限码或自动角色授权。

## 工作台预约、签到与课消读取（2026-10-24 增量）

`GymMobileWorkCoursesController`的四个目标路由分别检查bookings/checkins/lessons读取权限 → `GymMobileWorkCoursesService`验证有效后台身份和有界分页、必传日期 → 复用`GymMemberCoachService.predicate/coachPredicate`在SQL分页前施加会员/门店与授课范围。SELF预约和课消取两个谓词交集；签到只取会员谓词。业务范围没有从上下文门店列表推导，也没有扩大旧后台查询权限。

预约通过同租户同门店排期EXISTS验证并筛选开始时间；课消使用已确认记录及其保存余额，签到只读成功入场记录。分页与关联转换在只读可重复读事务中，姓名、排期、签到及课消批量查询；所有VO为明确字段。无新增表、迁移、授权或外部渠道调用。

## 工作台排课与代会员预约（2026-10-24 增量）

`GymMobileWorkCourseWriteController` → 功能权限 → `GymMobileWorkCourseWriteService`（READ_COMMITTED写事务）→ 当前后台身份/本人任职或整店范围 → `GymMobileIdempotency`持久化请求锁 → 写入与首次响应同事务。DTO仅接受契约字段，拒绝额外字段。

排课在共享教练行锁下重查本人绑定和任职，锁商品并验证时长/归属，调用`GymBookingRules.rejectOverlappingSession`后写固定容量1排期。原`GymCourseService.validateSession`只将原重叠查询移动到该共享方法，保留原权限。本人排课能力只新增于移动入口。

代约预先核对会员/教练范围，在教练锁下核对商品，再委托原`GymCourseService.createBooking`执行实际预约及权益预占，使用`GymMobileWorkCoursesService.booking`返回明确VO。两个写入口和原后台通过同一教练/会员/排期锁序竞争；重复键返回首次快照，事务失败不留下业务行或幂等结果。无需新迁移。

## 短时二维码与统一签课事务（2026-10-24 增量）

工作台发码Controller → 有效后台身份及显式bookings:write → 具体排期/预约和当前签发范围 → 持久化幂等锁 → 排期/预约锁 → HMAC签名随机nonce → V011保存哈希、不可变业务绑定和过期时间。绑定不由客户端携带任意个人字段，签名租户上下文来自服务端。

会员确认Controller → 已绑定会员本人预约 → 幂等请求锁 → 签名验证 → 排期/预约/二维码锁 → 精确绑定、签发人当前权限及时间窗 → GymLessonConfirmationService。原GymCourseService.confirmLesson保留原后台鉴权和锁顺序，也委托该共享服务。共享层检查同预约/会员/店签到、课程当日、禁入和真实权益，扣课/释放预占、保存余额和首次source、更新状态，重复合法确认读原记录。二维码使用行及幂等结果同事务提交。

GymMobileLessonProperties绑定TTL/窗口/密钥，GymMobileLessonToken使用HMAC-SHA256与常量时间比较，密钥不提供默认后备值。两个HTTP入口禁记访问日志正文；发码幂等记录保留原token用于重试。没有设备刷脸调用共享服务，部署配置和迁移要求见脚本手册。

## 会员月度签到聚合（2026-10-24 增量）

`AppGymAttendanceController → GymMobileAttendanceService → GymMobileIdentityService`校验会员身份，再以当前门店关系或本人既往成功签到授权读取。该只读入口不借门店排名暴露其他会员档案，也不依赖仍有效的卡或营业状态。

`GymMobileAttendanceMapper`在数据库中按租户/门店/月份和当前时间筛成功记录，关联同租户未删除会员，聚合本人次数、活跃天数与高于本人的参与人数。`Asia/Shanghai`逐日边界由Java生成并绑定SQL参数，历史夏令时不依赖MySQL时区表。本人聚合和排名聚合共享只读REPEATABLE_READ快照；没有新表、权限码、缓存或后台写流程改动。

## 移动工资读取与独立发布（2026-10-24 增量）

`GymMobilePayrollController → GymMobilePayrollService`按本人或整店路径分别授权；本人通过`GymMobileWorkAccess.employee`绑定当前在职员工，管理者要求工资读取权限和`GymDataScope.check`。工资明细Mapper通过父批次EXISTS在SQL分页前限定租户、门店、月份及发布状态，之后批量读取父批次，在只读REPEATABLE_READ事务中投影冻结金额和受控计算项目。移动资源是每员工明细，旧后台资源是整店批次，ID不可混用。

`GymPayrollPublicationController → GymPayrollPublicationService`复用有效后台身份和整店范围，按原计算/确认的门店→工资批次顺序加锁；仅confirmed批次写V012的published_at/published_by，重复调用保持首次值。原confirmed状态不变，因此计算冻结和工资配置期间保护仍适用。原`GymPayrollService`的SELF查询补充发布与当前有效员工过滤，整店草稿审核不变；旧响应增加可空发布时间。没有自动发布历史工资、自动授予权限或发起打款。

## 移动活动、报名与公开封面（2026-10-24 增量）

`AppGymActivitiesController/GymMobileActivityController → GymMobileActivityService`区分匿名公开、令牌本人及后台整店权限。公开筛选在分页前执行，只读快照投影活动/总报名数/门店；本人历史按会员谓词分页。创建通过原GymMarketingService在同一幂等事务创建草稿并发布。

旧GymMarketingService.register与新报名共用GymActivityParticipationService，调用者先授权、锁活动再锁会员，公共服务承担窗口、容量、活动会员唯一与取消后重报逻辑。移动请求键与实际报名同事务提交；旧后台取消/活动整体取消与新会员取消都先锁活动，保持名额一致。V013的cancel_deadline默认NULL，已有报名后不允许修改；后台管理取消保留原活动开始前规则。

`GymActivityPublicAssets`校验活动图片是否存在公开引用、文件归属营业门店及工作用途；`GymMobilePublicService`将其转为既有受控Media URL，下载复用文件魔数检查。取消或撤销最后公开引用后旧URL失效，私有会员/教练/合同上传不能改作活动封面。原跨店活动在移动端保留门店集合和共享容量；无新表、自动授权或外部通知。

## 反馈、一次回复与匿名图片（2026-10-25 增量）

AppGymFeedbackController/GymMobileFeedbackController → GymMobileFeedbackService → 会员绑定或有效后台身份/功能权限/整店范围。本人/门店谓词及筛选在SQL分页前执行，附件按页批量读取，返回显式VO不返回DO审计字段。V014的会员归属只供服务端验证本人和附件所有权；可选卡摘要只保存显式同意时的有效数量。

提交在READ_COMMITTED事务内持幂等行锁，按fileId排序锁文件、验证同店本人feedback用途和唯一关联，写反馈及附件并保存首次响应。回复先检查当前范围，再在幂等事务下锁反馈重查范围，仅首次内容可写；重试不覆盖。读取采用只读REPEATABLE_READ快照。反馈新增幂等payload使用有序TreeMap，保持外层键序稳定。

GymFeedbackImages使用内存ImageIO解码、像素边界检查、新建sRGB图像并重新编码，清除原文件名及元数据；提交预检，专用门店下载再次验证并转码，不写新的存储对象。专用附件链路检查反馈/文件实际关系，通用文件服务继续拒绝member_文件。V014只登记权限，不授权角色；前端及外部消息渠道未修改。

## 器械场景码、教学与公开素材（2026-10-25 增量）

`AppGymEquipmentController → GymMobileEquipmentService`在当前租户只读REPEATABLE_READ事务中，通过持久化scene或器械ID定位已发布且active器械，并验证营业门店。读取active教程，投影id/title/instructions及明确image/video Media，不返回DO内部字段。V015的nullable public_scene兼作明确发布标记，默认历史不公开。

`GymEquipmentPublicationController → GymMobileEquipmentService`执行有效后台身份、gym:equipment:write与整店范围校验，锁原器械行后首次生成随机scene或显式清空；与原GymDeviceService编辑使用同一行锁。唯一索引为tenant/scene，重复发布返回原码，撤销后再次发布新码。

`GymEquipmentPublicAssets`验证active教程→已公开器械→营业门店及文件同店/类型/无工作用途绑定，供列表Media投影与既有GymMobilePublicService下载共同使用。下载前再次检查公开引用与文件魔数，复用原公开媒体Controller的范围响应；其他独立公开引用可继续授权共享文件。没有增加对外存储URL、渠道调用或前端改动。

## 移动销售报表与分组财务查询（2026-10-25 增量）

`GymMobileSalesReportController → GymMobileSalesReportService`验证有效后台身份、gym:reports:read、整店范围、日期窗口及分页。服务端解析允许门店集合和上海时间边界，组成不可变Criteria交给`GymMobileSalesReportMapper`，禁止客户端指定租户或绕过范围。

Mapper以确认财务事件为事实源，原始筛选SQL共享于总金额/去重订单数、分组行数和分页三个查询。列表按订单/门店/员工/类型聚合，再连接匹配门店的订单编号，相关子查询读取该归属组历史最早payment；分页后批量读取订单项名称快照。金额不JOIN多条订单项，避免放大；整个读取处于只读REPEATABLE_READ快照。V016增加门店日期和订单付款索引，不改财务写链路或旧报表。

## 员工与门店业绩聚合（2026-10-25 增量）

`GymMobilePerformanceController → GymMobilePerformanceService`验证功能权限、后台身份及日期；通过整店范围或显式SELF身份/任职确定允许门店及可选唯一employee条件。混合权限先取整店，明确范围外门店才可进入本人规则；本人拒绝store分组。

`GymMobilePerformanceMapper`分别聚合财务归属金额/去重订单和confirmed课消条数，所有谓词在SQL内绑定，未加载原始交易明细。服务并入当前可核实零业绩员工/门店，保留事件中的历史员工和未归属行，计算去重人数、均值、排名后分页；全程只读REPEATABLE_READ。人事模型没有完整离职/任职历史，不能从现表还原全部历史零业绩编制，实施文档明确这一限制。无新表/迁移或授权扩展。

## 移动会员与临期权益汇总（2026-10-25 增量）

`GymMobileMemberSummaryController → GymMobileMemberSummaryService → GymMobileMemberSummaryMapper`复用reports:read、当前有效后台身份与整店数据范围；SELF不授予整店人口查询。服务捕获一次业务时钟，查询当前授权未删除门店，生成上海注册日边界和30×24小时临期界限。

Mapper以当前有效会员档案为外层一人一行，会员门店、卡适用门店及私教包使用同租户同门店EXISTS过滤；一次聚合得到五项人数，避免多张权益或跨店关系乘积。门店读取与统计共享只读REPEATABLE_READ事务，不加载会员明细到服务层。date只筛选当前人群中的初次注册日，updatedAt标识其余指标的实际统计时点；缺历史状态版本时不伪造历史总量。无新表、缓存、权限定义或既有写流程修改。

## 移动首页概览及逐项功能权限（2026-10-25 增量）

`GymMobileDashboardController → GymMobileDashboardService → GymMobileDashboardMapper`检查有效后台身份，优先采用整店范围，显式选择范围外本人任职店时才按SELF退回本人模式；无合法范围拒绝。scope、date与updatedAt由服务端确定，客户端角色/员工字段不起作用。

服务分别检查reports、checkins、lessons、members读取权限，只有授权才执行对应聚合；审批数另需read和decide及整店模式。SQL按租户/所选门店过滤：本人财务限定employee_id，签到关联同店当前负责会员，课消再限定本人教练；成员用EXISTS按人去重；审批用当前步骤/当前账号与有效员工绑定EXISTS计数，不继承旧页面10条上限。所有指标在只读REPEATABLE_READ事务中读取，不加载原始流水或档案列表到服务层。

日期只确定事件日/昨日/月累计及当前人群中的注册日；当前成员总数、负责关系和待办不伪造历史快照。响应记录显式保留null，以区分未授权与真实零值。无新增表、权限定义、自动授权或既有业务写流程修改。

## 移动审批读取与共享决策事务（2026-10-25 增量）

`GymMobileApprovalsController → GymMobileApprovalsService`分别执行read/create/read+decide功能权限，服务校验后台身份后用原Approval/Task/Template Mapper读取。mine按申请账号，pending按整店及当前步骤，processed按已处理步骤；指定审批人查询关联当前有效员工绑定。只读事务中先SQL授权分页，再批量读取步骤并按业务类型白名单投影，未复用会返回手机号/附件/完整结果的旧后台VO。

模板选项检查当前店范围、published状态、每步员工/账号/整店权限，不给SELF整店能力。决策在READ_COMMITTED事务中先验证当前处理资格，再进入GymMobileIdempotency请求锁、原GymBusinessMutex及审批行锁，比较V017版本并委托GymApprovalService.decide。原服务每次真实处理递增version，因此旧后台与移动端共享状态机、锁、业务副作用及版本。幂等结果与最终卡/价格/内部合同变更一起提交或回滚；读缓存前也重新检查身份和处理资格。旧程序未实现version递增，须所有实例更新后启用移动入口。

## 移动发卡申请与私教开通审批（2026-10-25 增量）

`GymMobileCardIssueController → GymMobileCardIssueService`校验approvals:create及V018 cards:issue、有效后台身份和整店/会员关系，进入持久化幂等事务，构造受控card_opening请求后调用既有GymApprovalService.create。客户端无法传价格、课时、状态或内部业务快照；返回使用移动审批白名单投影，首次响应与申请同事务。

会员卡继续使用不可变模板版本；新增course-products开通来源调用GymCourseService.approvalIssueSnapshot，在教练→会员→商品锁下冻结版本及条款。最后步骤issueApprovedPack重新验证当前条件和冻结数据，再委托issuePack写真实课包与可选内部未签合同，沿用approval/审批ID唯一来源。全链路复用审批行/版本/幂等机制，合同或业务失败一并回滚；无订单、财务事件或第三方成功结果。私教产品没有有效期字段，本入口明确发放无固定到期日课包。


## 私教专属改价申请与公开价隔离（2026-10-25 增量）

`GymMobileCoursePriceController → GymMobileCoursePriceService`检查功能权限、有效后台身份和商品/会员当前范围，规范化金额及原因后进入移动幂等事务，构造来源受控的price_change请求并调用GymApprovalService。SELF商品和指定会员权限在缓存重放前也验证；实际同店会员关系不被总部全范围绕过。

`GymCourseService.quoteApprovalSnapshot`按教练→会员→商品顺序加锁并冻结条款，最终审批通过validateQuoteApproval重新比较。新course-quote-products分支只写批准结果，不调用applyApprovedPrice；原course-products分支保持公开价格更新。专属快照用productId且刻意不写sourceId，使旧二进制缺少公开改价必需参数而失败回滚。移动受控投影增加报价范围与版本，无新表/迁移；报价实体及结算绑定为后续独立步骤。


## 私教报价持久化与会员读取（2026-10-25 增量）

GymMobileCourseQuotesController/AppGymCourseQuotesController → GymMobileCourseQuoteService，分别要求有效后台账号及courses:quote、或令牌绑定会员。后台创建先验证当前范围和SELF审批归属再查幂等缓存；新操作按请求锁→租户业务互斥→审批锁→课程共享校验的教练/会员/商品锁冻结条款。一个approved专属申请对应一条GymCourseQuoteDO，V019唯一索引在租户内兜底；同申请新键读取原报价不续期，原价不需要审批但仅整店权限可生成。

会员读取使用租户Mapper和真实会员/门店关系，在只读REPEATABLE_READ事务中投影安全Quote；当前产品版本、营业店及教练任职决定active/expired，保留原金额和截止时刻。没有使用后台身份执行会员读取。GymMobileQuoteProperties只控制受限前端分享路径，未更改或部署前端页面。

新增gym_course_quote使当前表数为68，保存签发/到期微秒时间、审批唯一关联及内部条款；used_order_id由下述会员下单事务原子绑定，报价HTTP接口本身不写入该字段。订单创建已接入重验和消费规则，支付确认仍需后续衔接，不能将查询状态当作最终支付许可。


## 会员订单准备与报价消费（2026-10-25 增量）

AppGymOrdersController → GymMobileOrdersService通过真实会员身份服务确定用户和会员，不复用要求后台数据范围的prepareCardOrder。创建在请求幂等锁→租户业务互斥→门店/报价→教练→会员→商品锁下校验并冻结服务端条款，写共用订单/明细/策略表；报价服务在真实订单写入后核对会员、商品、数量1、版本、金额和期限，才写used_order_id。失败全部回滚，取消不释放报价。

读事务按会员/租户/线上单项明细和计算支付状态在SQL中分页，随后批量投影关联条目，历史记录不依赖当前门店关系。取消与原Commerce收款共用互斥/订单锁，并停止未完成任务；V020持久化会员账号、到期和首次取消审计，旧行保持NULL。可信收款入口对新deadline校验支付发生时刻，旧无deadline订单保留原语义。

新会员订单尚未入自动履约队列：原worker以后台员工身份执行，不能冒用会员数字ID。下一步需补会员购买的安全自动履约和重试，订单策略/快照已持久化供其使用；目前明确pending且无模拟付款/发权益。未改前端，表数仍68。


## 履约任务的账号类型隔离（2026-10-25，V021）

`gym_fulfillment_task.principal_user_type`与`principal_user_id`共同确定执行身份，使用UserTypeEnum（会员1、后台2）。V021为历史任务补类型2：这些任务此前只能从后台身份入队，保留原账号、状态、次数和租约；不为会员订单创建任务。新后台入队显式写2。

Worker领取租约后先检查类型，只有2才调用AdminUserService及后台权限校验；会员、未知或缺失类型均失败留痕，不将同号会员当成员工。既有重试入口也只接受类型2，不能通过管理员重试把会员任务改造成后台任务。失败沿用有上限的退避和上下文恢复机制。身份字段不加入对外任务响应。

这是V021阶段的前置隔离；V022已在此基础上增加真实会员执行分支和入队，见下节。部署先迁移再更新所有执行器，后续启用会员任务前必须确认已无忽略类型字段的旧执行器。


## 会员购买自动履约（2026-10-25，V022）

GymMobileOrdersService在订单/报价事务内调用Queue.enqueueMember，以buyer用户类型1持久化。Worker按类型分支：2维持原后台实时授权，1设置真实会员LoginUser并调用GymMemberOrderFulfillmentService，未知值失败。管理员重试会员任务保留原buyer身份；租约、退避和上下文恢复共用。

GymOrderEntitlementAccess只接受已落库订单或明细ID，不接受请求体商品快照。租户互斥→订单/策略/明细→门店→教练→会员→商品锁，重新核对会员绑定、buyer、资金/审批状态和当前资格；卡/课程/合同的购买钩子必须已有事务，私有共享写入器仅在此授权后使用冻结条款。新合同再次比对真实权益来源、会员、门店和商品类型，不借后台数据范围。合同actor_user_type=1，后台uploader_id=NULL。

权益及合同写入后，同事务保存明细权益ID、策略fulfilledAt、订单fulfilled和任务完成；任一失败回滚，既有支付事务不撤销。订单锁与来源唯一约束防重，已履约重放不新增权益。外部文件存储不参与SQL回滚，未引用blob仍需清理。私教成交价和时长分别进入price_snapshot、duration_minutes_snapshot；预约查询/写入共用matchesDuration，NULL历史权益保持旧规则。可信收款路径支持course_sale并记private收入，卡退款路径继续拒绝course_sale。

V022只恢复有真实买家且满足约束的缺失会员任务，零元/全额收款变pending，未付/部分付仍waiting_payment；不重写历史订单或产生资金/权益。部署暂停会员下单和全部执行器后迁移、统一升级再恢复，防止旧写入实例漏入队和旧执行器身份混用。仍68表；无前端、真实渠道或业务库操作。

## 会员退款申请、前置审核与凭据关联（V023）

AppGymRefundApplicationsController → GymRefundApplicationService → gym_refund_application；会员身份和本人订单先于幂等缓存校验。后台GymRefundApplicationsController使用sales:read及决定时approvals:decide，服务层再校验有效后台身份与整店范围。申请和审核按租户互斥→订单→申请→履约任务加锁；唯一活动位置和版本共同防重复与并发决定。会员请求人和后台审核人分别保存，不能混用账号类型。

申请→Queue.holdForRefund将未发权益任务设为waiting_refund并撤销旧租约/token；Commerce和OrderEntitlementAccess直接发放路径、失败重试路径也检查活动申请。驳回→Queue.resumeAfterRefundRejection按当前资金和原重试预算恢复；既有人工审批等待不被覆盖。已发权益不因申请本身撤销。

Commerce.acceptRefundForApplication → 原可信凭据核验 → 申请批准时间/原订单/限额校验 → 真实退款及原GymRefundReviewService权益处理 → 申请关联金额和状态更新 → 任务取消，共用事务。GymRefundReviewService是退款后的卡权益人工处理，GymRefundApplicationService是退款前的申请审核。无关联的原退款流程保留，可关闭余额不足的申请但不计入其已退金额；凭据关联不可事后挪用。课程可申请审核，真实私教退款/权益撤销仍拒绝。

V023新表及旧退款可空关联字段不回填历史审批，69表；无额外授权。部署暂停相关写入和执行器，统一升级后恢复，避免旧代码忽略申请暂停条件。未接真实退款渠道，未改前端或业务库。

## 工作台设备与柜格查询（总表122–123）

GymMobileDevicesController（gym:devices:read）→GymMobileDevicesService→GymMobileWorkAccess有效后台身份、GymDataScope整店权限→原gym_device/gym_device_compartment Mapper。门店限定必填，设备类型只取door/locker；柜格通过真实父设备确认门店且必须locker，仅configured记录参与分页。采用只读REPEATABLE_READ，范围/类型/逻辑删除在SQL分页前处理；仅投影契约字段，配置状态不等于在线或占用状态。

查询不依赖真实设备适配器，不发送指令或变更遥测。管理后台原设备维护入口保持，无表结构或角色授权变更；移动命令/回执仍未实现，前端未改。

## 站内通知投影与会员偏好（V024）

预约两端创建/共享取消、Card/Course实际发放、Contract生成、ActivityParticipation报名及MobileActivity/Marketing取消 → GymMobileNotificationPublisher（MANDATORY）→ 原业务源行锁与最新通知当前读 → gym_mobile_notification。发布器只从持久化业务解析会员、门店、事件和目标，不接收公共HTTP的任意接收人/内容。会员绑定及授课员工关联账号决定接收人，账号类型1/2区分同号账号。业务变化与站内记录同事务，复用现有幂等/回滚规则；第三方发送不参与本批。

会员/工作台NotificationsController → GymMobileNotificationsService → 当前Identity/WorkAccess → 接收人SQL过滤。工作台只有当前授课预约来源，额外PermissionApi与MemberCoach的会员、教练双范围及实际预约/排期EXISTS；总数也受当前范围约束。已读行锁仅首次保存时间。通知type精确区分member_card与course_pack，来源business_id和对外target_id分开，活动打开activity而非registration。

会员偏好写入gym_mobile_notification_preference，受限业务模板键、真实账号/档案与租户唯一、批量同事务；使用现有租户互斥序列化写入。偏好只供未来外部适配读取，不抑制站内业务记录、不生成授权或送达状态。V024增加两表至71表，统一升级相关业务写入实例以免旧版本漏事件；无前端或角色授权变化。

## 门店服务私信与会员已读（V025）

总表92–97由GymMobileConversationsController及AppGymConversationsController→GymMobileConversationsService实现；后者另提供会员read辅助路由。Service复用MobileWorkAccess、MemberService、Identity和PermissionApi，每次根据当前身份、私信功能权限、门店会员范围校验。员工共享该门店会员服务会话，不按创建人永久授权；会员历史属于已核验绑定的gym会员。

V025新增gym_mobile_conversation及gym_mobile_message，当前73表。会话唯一键为tenant/member/store，消息保存真实账号类型与账号ID。创建使用幂等行→租户互斥→会话行锁；发送使用幂等行→会话行锁；会员已读只锁会话再验证目标消息。消息插入、最新ID、会员未读数和幂等响应同事务，失败全部回滚。读游标单调递增并重新统计后台未读消息，不将GET当作确认。查询用REPEATABLE_READ保证分页/摘要快照一致，列表批量取得最新消息，无逐条N+1查询。

只新增read/send权限定义、不授予角色。七个入口均关闭API请求/响应正文日志。与站内通知发布器无调用关系，无外部渠道或WebSocket。创建和发送使用READ_COMMITTED，锁后再次验证当前访问条件；旧幂等响应前也先检查当前身份/范围。详细契约及历史访问边界见uniapp/uniapp-api.md的V025章节。

## 会员邀请与首次身份归属（V026）

AppGymInvitationController→GymMobileInvitationService→IdentityService校验当前会员，再由营业门店/会员门店关系约束邀请资格。幂等行→现有租户互斥→重新校验后插入gym_mobile_invitation，邀请码、分享页面、微秒时间和幂等响应同事务。新键新码，旧键重放首次签发快照；此服务不依赖微信或积分。

IdentityService的首次建档分支直接读取InvitationMapper，避免与邀请Service形成循环依赖。在原租户互斥事务内验证码、门店、到期及邀请人当前账号/身份/会员关系；身份保存四项归属快照，与档案和门店关系一起提交或回滚。旧身份重复初始化不修改归属；旧自由文本码没有验证快照，不能进入奖励依据。invitationNewMember区分新建档案与老会员首次绑定。V026一表及身份字段使当前gym表数74，独立租户唯一索引和完整归属约束辅助服务端校验。配置类只允许内部pages路径及1～365天有效期；前端页面与奖励业务仍待后续。

## 会员本人连续包月查询（V027）

AppGymSubscriptionsController→GymMobileSubscriptionsService→IdentityService当前有效账号/绑定→GymSubscriptionMapper。两个查询使用只读REPEATABLE_READ，会员/租户/删除/门店条件在SQL分页前应用；详情同样加入本人条件，无需后台数据范围或当前会员门店关系。返回原协议8字段投影，不连接当前商品/门店或渠道凭据；历史门店/商品删除不丢失本人记录。

状态映射固定白名单，unconfigured/failed保留、未知为unknown，旧pending/pending_authorization映射pending_sign、cancelled映射terminated。不以签署时间、计划扣费时间或查询时刻驱动状态转换。V027给gym_subscription新增可空agreement_version和tenant/member/deleted/store/id索引，仍74表；版本来自协议自身，不从实时销售配置推导，历史NULL保留。没有新增写入路径或外部适配器，既有后台合同签约审批逻辑保持。

## 积分账户、不可变流水与会员读取（V028）

AppGymPointsController→MobilePointsService→IdentityService→PointAccount/PointRecordMapper。本人及租户条件在查询前加入，读事务使用REPEATABLE_READ；没有账户时返回零且不写库。账户按tenant/member唯一，流水按tenant/businessKey唯一；有会员/类型/原流水索引，账本使用安全整数和微秒时间。

GymPointLedgerService.append为内部MANDATORY方法，必须由已核验来源/授权的业务事务调用，当前没有生产调用者或对外写接口。锁顺序为租户互斥→会员→同键旧流水→账户→原流水及已发生冲回；查询均锁定当前数据，串行校验余额与原额上限。新流水、累计正负收支和净余额同事务；重复同载荷返回首次流水和当时余额，变更载荷拒绝。earn正向、spend负向且不超支、refund反向关联原earn/spend；不允许修改或删除既有流水。原earn已消费后的冲回可形成负余额，后续支出拒绝，账本忠实保留欠额。

V028两表使当前gym表数76，数据库约束账户恒等式、计数边界、单笔符号/类型及必需原流水。未接消费/邀请或退款自动积分钩子，未造历史账户，未改Card/Commerce/Identity业务；商品与兑换仍待后续，自动奖励暂缓保持。


## 积分兑换商品（V029）

会员AppGymPointProductsController与后台GymPointProductsController分别进入GymPointProductsService的catalog/manage/save。会员读取调用IdentityService验证真实会员及有效账号，再检查当前门店关系；后台以独立功能权限、账号类型/租户和GymDataScope门店范围控制。两个分支都用同一产品持久化表，不复用现金销售价格或虚构兑换订单。

产品固定关联store_id与card_product_id（具体卡模板版本），维护required_points、可空剩余stock、enabled和revision。修改遵循租户业务互斥→门店→商品→模板锁；旧版本拒绝、同值不更新、失败全部回滚。模板下架/删除后可下架商品，不能以过期模板重新启用。会员目录读取有效模板ID后在产品SQL分页前加入全部商品过滤，查询无账户、库存或权益写副作用。

当前只完成配置和目录，不把库存配置当作已完成库存消费。后续62–63须以真实兑换记录连接LedgerService和受控会员发卡入口，加入库存版本递增及失败一致性；不能绕过现有后台授权或冒充已支付订单。V029新增一表至77表、一个权限定义，未授予角色、未接入自动奖励。


## 积分兑换持久化流程（V030）

AppGymPointRedemptionsController→MobilePointRedemptionsService（NEVER外层事务）→RedemptionTransactions.prepare提交资金/库存准备，再调用RedemptionWorker→Transactions.fulfill或fail，最后读取本人结果。三段事务明确隔开；发放异常不会把已保存的processing单抹掉，也不能在发卡已回滚时丢失返还线索。

prepare沿用Idempotency但保存兑换ID而非首次状态；兑换表另以tenant/user/request_key唯一，重试查询最新结果并校验原载荷。租户互斥下核验当前门店/会员/黑名单、商品及模板，冻结模板和原积分成本，写唯一spend、预留refundReserve、扣可售库存并增加reservedStock。失败全体回滚。商品可用库存与处理数量、revision与待完成数量、累计收入与返还空间各有数据库边界约束。

RedemptionAccess验证持久化兑换、原扣分及当前会员资格，供CardService.issuePointRedemption与ContractService.issueForRedeemedCard使用。仅这条已验证分支跳过后台门店授权，始终保持会员账号类型，发卡来源为point-redemption/兑换ID。复用实际卡、跨店权益、未签合同和站内通知流程，无现金订单或付款。fulfill同事务释放库存/数值预留并落succeeded；fail同事务关联原spend写refund、恢复适用库存并落failed，终态不会逆转。

Worker从数据库读取真实发起人，临时设置会员上下文后恢复；发放失败再调用独立返还事务。Scheduler枚举并验证租户，Timer使用自有线程，默认30秒扫描等待至少30秒的processing单、每租户100条。请求与多执行器依靠相同行锁串行；返还故障保留processing供恢复，单个失败不阻断同批其他兑换。GET只读，不驱动工作流。

V030新增gym_point_redemption并扩展原账户/商品，当前78表。需要统一升级积分写入、商品维护和兑换处理实例以遵守预留字段；旧余额与商品配置不回填业务记录。内部合同文件存储不参与数据库事务，失败产生的未引用blob沿用既有清理流程。自动奖励及真实电子签约仍暂缓。


## 风控告警与不可变处置（2026-10-26，V031）

`GymMobileRiskController` → `GymMobileRiskService` → `GymRiskAlertMapper/GymRiskResolutionMapper`。有效后台身份、功能权限及GymDataScope整店范围在服务层复查，SELF不足；分页先SQL筛选再投影。处置事务通过GymMobileIdempotency及告警行锁，将open/version1改为resolved/version2，并保存真实操作者、时间及一次处置，唯一约束防止第二份审计。相同键重试仍先校验当前授权；失败回滚业务和幂等响应。原始来源快照不可改写，不调用CardService或门禁放行服务。

`GymRiskAlertPublisher`为内部MANDATORY事务接收器，在租户互斥锁下从登记设备获得门店，验证可选会员及同店私有证据。来源以租户/设备/事件键/类型唯一，规范化微秒时间和证据顺序；同源相同内容返回原ID，变化拒绝。当前没有生产调用者；未来可信网关须先验签、防重放并完成真实事件分类，再在业务事务中调用。不把该服务称为已接入风险检测，也不向客户端开放创建入口。

证据链：告警读取授权 → 检查不可变evidenceFileIds关联 → 同租户/同店risk_evidence元数据 → FileApi取原始字节 → 格式/大小校验 → 私有附件流。通用GymFileService拒绝此类别的元数据/下载，通用上传类别也不开放；未新增真实采集入口。日志不记录该Controller请求/响应业务内容。V031新增告警/处置两表至80表和两项功能权限定义，不授予角色、不回填事件。自动冻结、规则累计、设备事件生产仍未实现。


## 设备命令结果与完成时间（2026-10-26，V032）

GymMobileDeviceCommandsController → GymMobileDeviceCommandsService → GymDeviceCommandMapper，以租户和有效后台身份读取既有命令。本人历史按requesterId授权；他人需PermissionApi的gym:devices:read及GymDataScope整店范围，并核对不可迁移归属的设备记录。无当前设备记录时他人不可读；本人历史不依赖当前门店/设备可用性。服务层承担“本人或管理者”完整授权，不在Controller附加会排除本人历史的固定功能权限。

查询只投影7字段，失败原因白名单、模拟/缺失完成证据的成功降为unknown；不调用发送器。V032为gym_device_command增加completed_at及状态/时间约束，不修改历史记录。既有GymDeviceService在未发送失败事务内记真实结束时间；真实设备回执仍无生产写入者。未来回执须校验真实来源，不能用更新时间替代完成时间。表数仍80，无新增权限或角色变更。


## 会员团购预览（2026-10-26，V033）

AppGymCouponPreviewsController → GymMobileCouponPreviewsService（NEVER事务）→ GymCouponPreviewTransactions.begin只读身份/门店/设置检查 → GymMemberCouponProvider复用GymMeituanRetailClient.prepare及必要的mobileReceipts → Transactions.save重查并持久化。网络位于数据库事务外；归属只接受可信完整手机号相同或平台本人券列表精确包含，掩码后缀和客户端手机号均不能授权。两个平台接口的platform编码显式转换。设置/身份/映射在网络前后变化时拒绝保存。

配置GymMobileCouponProperties按租户/门店/dealId精确映射具体卡模板，默认关闭。模板快照、设置版本及映射指纹与会员预览同事务保存；不触发consume、CardService、账本、签到或通知。GymCouponCipher从32字节配置主密钥按用途派生AES和HMAC密钥，用随机GCM nonce与租户/门店/用户AAD保护券码，索引用租户/平台HMAC，数据库不保存明文券码或手机号。V033新增gym_coupon_preview至81表，无权限/角色及历史回填。当前只完成32，33–34核销和结果仍待实现，真实渠道联调尚未完成。

## 会员单券核销适配准备（2026-10-26，无迁移）

GymMemberCouponConsumption → GymMeituanRetailClient → GymMeituanTransport。当前没有生产业务调用者或新增路由，不替代会员身份、占券、幂等流水或发卡授权。consume使用外部编排已冻结的requestId和scope，固定单券；scope由精确租户/门店商家审计配置和平台授权生成，用GymCouponCipher独立authorization用途HMAC绑定。移动消费开关独立于预览且默认关闭，配置变化时拒绝旧scope。

Confirmation只来自对应同步consume成功且精确单券/商品/流水验证后的响应；Observation只来自getConsumed，类型不含本次requestId和消费流水，不能进入发卡授权。两个网络方法NEVER事务，无自动重试。发生超时/歧义时返回结果未知；持久化状态流转与恢复仍未连接。原后台身份填充及16项入口保持原行为，无数据库或权限迁移，仍V033/81表。

## 会员核销持久化、单次派发与本地履约（2026-10-26，V034）

AppGymCouponVerificationsController → GymMobileCouponVerificationsService（NEVER）→ Transactions.prepare提交本人请求/唯一券占用 → Worker真实会员上下文 → inspect/Provider.prepare/start重查并提交sending → Consumption.consume一次 → confirm独立事务 → fulfill本地事务。通用幂等仅缓存记录ID，持久化业务键/预览/券占用约束在缓存清理后继续防重；结果总是按当前用户/会员/租户读取。

GymCouponVerificationAccess只接受当前本人、confirmed阶段、匹配请求/授权/商品/券摘要的同步凭据以及冻结卡模板，拒绝缺凭据或未知态。CardService.issueCouponVerification与ContractService.issueForCouponCard只从该授权对象进入会员路径，不冒用同号员工。卡、会员门店/阶段、适用未签合同/会员审计、通知、旧gym_verification记录与最终fulfilled同事务；失败回滚而provider确认不回滚。卡sourceType=coupon-verification、sourceId=核销ID，起始时间固定confirmedAt，不产生现金订单/收入或积分。

数据库锁顺序为通用幂等键（提交时）→租户权益互斥→核销记录→实际门店/会员/权益；网络在事务外。prepared→sending只有一个赢家，进程在sending崩溃也禁止重发。同步凭据本地保存最多立即重试2次；结果未知保留占用，查询租约先提交、再解密并读取平台，每60秒最多6次观察，观察不能授权发卡。confirmed恢复无预览时效/消费开关要求，但保留当前会员资格校验；失败延后60秒，不返还已消费券。

独立GymCouponVerificationTimer默认30秒扫描有效租户到期任务，每租户最多100条，以nextCheckAt排序防止长期失败任务永久占据队首；用持久化会员账号执行并在finally恢复上下文。无共享默认调度器替换，无真实平台回调/人工确认入口。V034新增一表至82表，旧表/权限不变；预览与核销关联用于恢复，不得清理未决核销引用的加密预览。

## 设备操作认证器与一次性授权（2026-10-26，V035）

GymMobileActionConfirmationsController → GymMobileActionConfirmationsService → GymActionTotp / GymActionVerificationStateMapper / GymActionConfirmationMapper。TOTP依赖JCA，不新增认证服务供应商依赖；密钥按租户及后台账号从GymActionConfirmationProperties读取，默认关闭且没有默认凭据。GymMobileWorkAccess、PermissionApi和GymDataScope共同限制有效管理员、设备操作权限和整店范围，设备及柜格身份从数据库读取。

签发入口禁止外层事务，内部TransactionTemplate按READ_COMMITTED执行；锁设备后通过账号唯一行ensure+FOR UPDATE串行化校验。失败结果作为值返回，先提交失败次数/300秒锁定再抛业务异常。通过时写入token摘要、凭据指纹、已接受步数、设备/动作/柜格与120秒有效期。历史最大步数查询包括逻辑删除记录；不保存明文token、验证码或密钥，不缓存可重放成功响应。

消费入口要求外层事务，按设备→门店/柜格→指令→token顺序加锁并重读指令，拒绝外层事务的过时queued快照；重查账号、权限、范围、资源与当前密钥指纹，绑定真实queued且非模拟的本人指令；同事务更新唯一consumedCommandId和consumedAt，失败回滚不丢令牌。未来命令写入必须使用同一事务，且在网络派发前完成消费；当前该入口只在隔离测试调用，生产设备通信、事件/回执接口仍暂缓。现有后台命令的诚实失败记录逻辑不变。V035增加两表至84表，无历史补写或角色授权；异常处理与访问日志不暴露动态码和令牌。

## main 与 jdk25 上游同步（2026-10-26）

main接入upstream/master-jdk25 70ad21ad6c，同时保留移动接口分支f27ce5ad7f的全部gym实现。运行基线为JDK25、Spring Boot4.1.2；Excel依赖随上游升级至Apache Fesod 2.1.0-incubating，框架转换器及报表DTO同步迁移。MyBatis接收上游大小写兼容ResultMap/SQL注入与Jackson初始化修复，管理员认证保留本地AuthMenuTreeCustomizer并纳入上游二维码登录逻辑。

本项目gym业务模型、V001–V035迁移、受控文件及权限扩展未被上游版本替换；环境连接参数保留。默认启用server依赖链通过完整测试与打包，可选未启用模块仅完成源码合并/格式统一，未声明运行验收。所有第三方暂缓范围及125/135的接口统计口径保持不变；应用启动仍不自动迁移gym业务库。

## 统一公告管理与备注迁移（2026-10-27，JSS V010）

移动公告列表/详情/编辑 → 原工作台安全链 → NoticeController → NoticeServiceImpl → 生产NoticeMapper及租户插件 → system_notice。JSS V010只为既有框架表新增remark varchar(200)默认空字符串，VO/DO和H2基座同步；部署服务器代码前必须显式migrate/validate，不在启动时迁移，不重放原框架SQL。既有客户端省略备注时更新保持原值，显式空字符串可清空；管理VO返回备注，DO的@JsonIgnore防止原WebSocket实体推送带出新后台备注。

批删在REQUIRED事务中去重、整批校验当前租户可见记录并核对影响数；混合外租户/缺失目标整体拒绝。前端富文本使用既有mp-html解析器，再白名单生成rich-text节点，保存不改写原正文。既有在线推送API/消息协议不在本批实际调用或验收，实时消息及授权边界仍需独立检查。

## API日志查询与处理编号（2026-10-27，JSS V011）

移动API访问/错误日志四页 → 原工作台安全链 → 原infra两个日志Controller → Service/Mapper及租户SQL → infra_api_access_log/infra_api_error_log。访问按beginTime、错误按exceptionTime过滤；处理人取安全上下文的管理员编号，不接受前端传入。错误只允许INIT变为DONE/IGNORE，按id+INIT条件更新并核对影响数；并发失败者返回已处理，不覆盖胜者的状态、员工或时间。原外层事务可回滚处理更新。

JSS V011将已有process_user_id从int拓宽到nullable bigint default0，管理VO由Integer同步Long，DO的Long不变。部署前显式备份+migrate/validate，旧正文、旧int/NULL/0不重写；框架前置加入两张API日志表。API日志仍受租户SQL隔离，不能因DO继承BaseDO而推定共享；后台导出权限与查询/处理权限分离。应用启动不自动迁移，留存/脱敏、实际异步生成与HTTP/Redis仍需验收。

隔离HTTP用例另注册仅测试源码/专用jss-http-fixture profile的故障探针，用于经真实异常处理器产生访问/错误日志；初始化器要求已归属随机库及loopback。没有新增生产Controller或主动发送外部消息。

## 会员经验与人工调级的并发边界（2026-10-27）

原MemberLevelService的加减经验/人工调级两个REQUIRED事务先经MemberUserService.getUserForUpdate调用生产Mapper的FOR UPDATE当前读，锁定当前租户可见会员行；该锁同时覆盖经验记录、等级变更记录和最终会员更新。它避免旧RR快照/MyBatis缓存导致覆盖，也使同级调级和重复取消按已提交值判断。锁持续到调用方外层事务完成，调用方不能把getUserForUpdate当普通无事务查询。

经验计算用long中间值，超int正上限拒绝，不足扣减到0；两个新变动记录都写实际差额，不再被等级配置的门槛覆盖。最低启用门槛以下暂按无等级0处理，全部配置不存在/停用保留原行为；用户策略偏好未确认。接口结构、表及历史迁移不变，不重写旧记录。相同业务事件的重投幂等、配置竞争、通知和完整HTTP仍有独立待办。

## 会员经验事件幂等（2026-10-27，JSS V012）

上述会员行锁内，非ADMIN事件先INSERT member_experience_event，唯一键是tenant_id/user_id/biz_type/区分大小写biz_id；成功后才写经验/等级记录和会员余额，全部处在同一事务。撞唯一键时精确当前读已有事件；相同带符号请求值返回已处理，值不同报业务冲突，旧事件NULL请求值只表示已封存且原请求未知。失败/外层回滚撤销事件键，允许同键重试；同一外层事务重投不会把事务标成意外回滚。非零请求即使实际只扣0，也消费该事件；显式零请求仍沿用无变动行为。ADMIN手工操作的固定编号0不用于去重。

选择先插入而不是先查缺失事件，是为避免缺失行的范围锁参与不同会员的插入竞争；范围/间隙锁和完整唯一键匹配的区别见[MySQL 8.4 InnoDB锁文档](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html)。六名独立会员并发已在实际MySQL通过；不将此推广为完整交易系统无死锁或吞吐已验。

V012只新建事件表并按原经验历史封存外部事件，包含已逻辑删除记录；重复旧记录保留，不重新加减余额，不把旧实际变动猜成原始请求量。新表无逻辑删除标志/公开删除接口，保留事件键独立于日志清理，避免清理日志重新开放重投。先备份+migrate/validate V012，再部署含此代码的服务；不改写V001–V011，应用启动不自动迁移。

## 会员资料编辑与分类关联（2026-10-27）

原管理会员更新Controller → MemberUserServiceImpl的REQUIRED事务 → 当前租户会员FOR UPDATE → 原会员Mapper写入。资料转换忽略levelId，旧客户端回传等级仍可兼容，实际调级必须走独立权限及等级记录Service；积分和经验没有加入资料写入。标签/分组通过同模块Mapper校验，避免User与Tag/Group的Service循环依赖：新增关联只允许本租户有效标签及启用分组，原失效关联可保留或移除，标签去重且限制实际逗号字段255字。

更新VO内部记录birthday/areaId/groupId/tagIds是否出现，标志不接受JSON赋值也不输出。省略保持原值，显式null清空；地区及分组0也清空，标签空值归一为空列表。地区在Long转int前校验非负及上限；可空数据库列通过显式UPDATE SET NULL清除。原禁用撤权分支保留，但本批实际数据库测试没有实例化或调用OAuth/Redis客户端，不能视作登录、RBAC或撤权验收。没有新增表或迁移。

## 手工会员积分调整（2026-10-27）

原MemberUserController的update-point权限入口 → MemberPointRecordService的REQUIRED事务 → ADMIN类型先对当前租户会员FOR UPDATE → 原积分条件更新与积分流水插入。同管理员的独立手工调整仍分别执行，不把后台账号bizId当去重键；余额和流水共用事务。人工扣减不足明确抛USER_POINT_NOT_ENOUGH，积分加法用long中间值、超过int上限返回业务错误，null增量及不可见会员也返回业务错误。零增量保持原无操作行为。

其他业务类型保留原查询/条件更新方式及预检扣减不足时记录错误后跳过的策略，本批没有把自动退款不足改成异常，也没有验收完整订单/退款或签到链。自动加分预检同样识别整数上溢，但并发自动奖励的锁顺序/上限竞争及业务事件幂等仍待独立处理。无需新增迁移，原V012经验事件表不覆盖积分事件。

## 后台订单调价的当前读与支付状态（2026-10-27）

管理端订单列表/详情/按码查询共用TradeOrderBaseVO，核销码与数据库及DO均为String，转换链不再按Long解析；前导零、字母或长码保持原值。此修复不调整门店授权、状态验证或核销事务，真实HTTP/核销链仍待验，见testing的核销码转换记录。

原调价Controller → TradeOrderUpdateService原REQUIRED事务 → 租户交易单FOR UPDATE当前读 → 按编号锁订单项并分摊 → 实际PayOrderApiImpl → PayOrderService的REQUIRED事务及支付单FOR UPDATE → 待支付条件更新。锁延续到原外层事务完成，避免RR旧快照/MyBatis缓存覆盖已提交调价或已支付状态。交易单仅待付款且未支付可改，任何非零历史调价值均视为已调价，修复负数调价可以再次调整/归零重开的漏洞；历史数据不重写。

增量null/0明确业务拒绝，交易单总额用long中间值检查正数及int上限，支付单独立金额必须正数；交易单/支付单更新均核对状态条件与影响数。订单、项及支付单在同一数据库事务中，后置失败整体回滚；本批没有启用生产日志切面、支付渠道或回调/通知任务，不能据此认定审计原子性、完整付款竞态或全系统锁顺序已验。无需新迁移，部署V012前置继续保留。

## 运费模板修改与删除的锁顺序（2026-10-27）

原模板update/delete的REQUIRED事务先按租户与编号对模板根行FOR UPDATE当前读，已删除目标立即业务拒绝。修改再按编号顺序当前读锁计费行、包邮行，用实际已提交规则做diff，最后更新根字段；删除在同一根锁下删除三类记录。所有锁保留到原外层事务结束，不依靠RR一致性快照或MyBatis详情查询缓存判断当前根/规则，避免全替换竞争积累多套有效规则、旧快照在根删除后插入孤立子行。

仍按最后一次完整保存生效，不增加版本号或逐字段合并；同编号计费行可保留，省略包邮数组按原行为清空。六事务先读旧快照/缓存再并发替换、六事务保留计费ID并发编辑以及另一连接删除后旧事务保存，均有实际MySQL验证；这不是全系统无死锁/吞吐保证。跨模板重名创建/更名竞争、普通详情/报价多个查询的一致快照、已被其他保存删除的旧子行ID业务错误以及商品引用删除策略仍待验，没有新表或迁移。

## 支付查询的应用名称元数据（2026-10-27）

支付筛选器使用GET /admin-api/pay/app/simple-list，经现有SecurityFrameworkService.hasAnyPermissions检查app/channel/order/refund/notify/transfer六类query任一项；工作台会话先取权限上限与候选项交集，再交由原RBAC API判断。查询能力不等于支付应用配置读取能力，原/list及/get权限不变。Controller复用PayAppService.getAppList及原Mapper，在响应边界仅构造独立PayAppSimpleRespVO的id/name，不继承完整配置VO，保留已停用应用以筛选历史记录。

底层仍是原pay_app及V004的tenant_id/逻辑删除路径；没有TenantIgnore、跨租户查询、表/迁移或权限授予变更。新测试实际运行方法鉴权代理与生产JSON模块，RBAC存储和业务数据是替身；实际请求认证、SQL租户隔离及H5联调仍须在隔离服务验收，不将源码路径检查当作已执行跨租户验证。
