# 健身门店小程序接口设计文档

接口速查：[接口总表与数量统计](./uniapp-api-summary.md)（按请求方法与完整路径去重，共 135 个，其中小程序侧 133 个、设备集成侧 2 个）。

> 版本：V1.0 设计稿；日期：2026-10-23。
>
> 需求依据：[健身门店业务文档（小程序）](./uniapp.md)。适用会员端、员工版、管理者版、总部管理版。
>
> 本文定义拟开发的小程序接口契约，不代表接口已经实现或上线。除明确注明“已有”的接口外，下面的路径、字段、枚举和规则均为设计建议；待确认口径集中在第十二章。实现状态另见 [实施进度与第三方边界](./uniapp-implementation.md)，不能用设计条目替代完成度。

## 一、现有基础与设计边界

当前 `yudao-module-gym` 已有门店、员工、会员、卡、课程、预约、体测、训练计划、合同、审批、工资、报表、设备等管理端 Controller，可复用其业务服务和数据权限。会员端必须新增面向本人数据的接口，不能直接向会员开放管理端接口。

代码核对结果：

| 能力 | 现状 | 本文处理方式 |
| --- | --- | --- |
| 管理端业务 | 已有 `/admin-api/gym/**` Controller | 复用底层服务，增加移动工作台所需的接口与返回裁剪 |
| gym 用户端 | 加盟意向接口已存在；新增身份、档案、合同查询与会员文件接口 | 逐项实现进度见总表与实施记录 |
| 微信会员登录 | `yudao-module-member` 已通过 gym 依赖装配，新增 gym 会员关联 | 本地测试不代表微信真实授权登录已联调 |
| 管理员与会员身份 | 框架区分 admin-api 与 app-api 的用户类型 | 分开保存两类令牌，不能只靠前端切换角色取得权限 |
| 支付、电子签约、门禁与消息渠道 | 存在本地业务基础；具体渠道需独立配置和验证 | 不将本地审批、创建记录、提交命令当作外部执行成功 |

本文采用两个业务前缀：

| 简称 | 完整前缀 | 使用方 |
| --- | --- | --- |
| A | `/app-api/gym` | 游客、登录会员 |
| W | `/admin-api/gym/mobile` | 员工、管理者、总部；该前缀为新增设计 |

例如 `GET A/stores` 表示 `GET /app-api/gym/stores`。W 接口是移动端适配接口，不替换已有管理后台路径。W 的实现应复用服务层，不通过 HTTP 绕一圈调用管理端 Controller。

## 二、通用接口约定

### 2.1 请求与认证

| 请求头 | 必填场景 | 说明 |
| --- | --- | --- |
| `Authorization: Bearer <token>` | 除标记为“游客”的接口外 | A 使用会员令牌，W 使用后台用户令牌 |
| `tenant-id` | 启用租户隔离的环境 | 来源于可信小程序配置或服务端租户解析；服务端仍校验令牌与租户关系 |
| `Content-Type: application/json` | JSON 请求 | 文件上传使用 `multipart/form-data`，由客户端自动生成 boundary |
| `Idempotency-Key` | 表中标记“幂等”的写操作 | 同一业务重试复用同一个 UUID，改变操作内容须换新键 |

`storeId` 是门店筛选条件，不是授权凭据；角色、员工编号、负责会员范围必须由服务端确认。所有请求通过 HTTPS。

### 2.2 返回格式

沿用项目 `CommonResult<T>`，成功 `code=0`，`msg` 为空字符串：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [{"id": "101", "name": "示例门店"}],
    "total": 1
  }
}
```

业务错误主要通过响应体 `code` 表达；客户端同时处理 HTTP 网络错误和业务错误，不能只检查 HTTP 200。

```json
{"code":1070009004,"msg":"须先签到入场，才能确认签课扣课","data":null}
```

### 2.3 字段与分页

| 类型/约定 | 规则 |
| --- | --- |
| ID | 业务 ID 统一为十进制字符串，如 `"101"`，避免 JavaScript 大整数精度问题 |
| 金额 Money | 新设计接口使用人民币元字符串，两位小数，如 `"199.00"`；适配层与现有 BigDecimal 转换；支付渠道单位转换仅在服务端处理 |
| DateTime | 新接口使用带时区 ISO 8601，如 `2026-10-23T14:30:00+08:00`；适配已有 Instant/LocalDateTime 字段 |
| Date / Month | `YYYY-MM-DD` / `YYYY-MM`；营业统计默认 `Asia/Shanghai` |
| 时间范围 | `startDate` 包含、`endDate` 不包含；查询 10 月 23 日使用 `[2026-10-23,2026-10-24)` |
| 分页 P | `pageNo:int=1`，`pageSize:int=10`，允许 1～200；返回 `Page<T>={list:T[],total:int}` |
| 排序 | 列表默认 `createdAt DESC,id DESC`；课程时段按开始时间升序，排行榜按指标降序 |
| 空值 | 无记录返回 `[]`，可选对象返回 `null`，不以 `0` 伪装未知人数或未知金额 |
| PATCH | 新接口省略字段表示保持，明确允许清空的字段可传 `null`；不自动沿用旧接口各异的 PATCH 规则 |

接口表中的 `?` 表示可选字段，其余所列请求字段必填；`P` 表示可选分页参数。GET 参数位于 query，路径中的 `{id}` 为 path 参数；POST/PATCH/PUT 参数默认 JSON body。返回栏描述 `data`，省略统一外壳。

所有列表的 `total` 必须在权限筛选后统计。客户端不能传入任意排序 SQL 或绕过上限获取全量数据。

### 2.4 幂等与并发

标记“幂等”的接口按租户、登录主体、操作类型、幂等键保存请求摘要及结果；同键同内容返回原业务结果，同键不同内容拒绝。加盟匿名提交沿用已有实现，不能假设与新接口的键范围相同。

订单、权益发放、课消、积分兑换另设业务唯一约束，不能只依赖短期缓存幂等键。预约预占、课时扣减、活动名额和积分扣减通过事务及锁/条件更新处理。对结果未知的超时操作先查询状态，不盲目创建新业务。

## 三、身份、角色和数据范围

### 3.1 角色范围

| 角色 | 预约 | 会员资料 | 经营数据 | 工资 | 审批与设备 |
| --- | --- | --- | --- | --- | --- |
| 会员 | 本人预约 | 本人 | 无 | 无 | 本人合同签署 |
| 员工 | 本人授课预约 | 当前负责的会员 | 本人业绩和课消 | 本人已发布工资 | 本人申请、明确分派给本人的审批任务；默认无远程设备权限 |
| 管理者 | 授权门店预约 | 授权门店会员 | 授权门店及员工汇总 | 授权门店工资表 | 按权限和指定审批任务操作 |
| 总部 | 授权门店集合 | 授权门店集合 | 分店及跨店汇总 | 与管理者相同查看能力，范围为被授权门店集合 | 不绕过指定审批人和设备权限 |

总部不等于跨租户访问。员工离职、负责关系撤销或门店权限变更后，下一次请求即重新校验；已打开的页面和已生成链接不能继续扩大访问范围。

### 3.2 登录与业务身份绑定

可复用的源码接口（当前服务是否装配需先处理）：

| 方法与完整路径 | 请求 | 返回/说明 |
| --- | --- | --- |
| `POST /app-api/member/auth/weixin-mini-app-login` | `phoneCode:string,loginCode:string,state:string` | 源码返回 `userId,accessToken,refreshToken,expiresTime,openid`；保留现有格式，由前端认证适配层转换 |
| `POST /app-api/member/auth/refresh-token` | query：`refreshToken:string` | 同登录响应，注意不是 JSON body |
| `POST /app-api/member/auth/logout` | 无 body | `boolean` |

W 使用项目已有 system 认证流程取得后台令牌。微信身份绑定员工账号属于单独的身份认证工作，不能按客户端手机号自动签发后台令牌，也不能把会员令牌直接交换为管理员令牌。

拟新增接口：

| 方法与路径 | 权限 | 请求 | 返回 |
| --- | --- | --- | --- |
| `POST A/me/profile-initializations` | 会员，幂等 | `storeId:ID,source:string,inviteCode?:string` | `{memberId:ID,customerType:string,created:boolean}` |
| `GET A/me/context` | 会员 | 无 | `{memberId:ID,defaultStoreId:ID?,profileComplete:boolean}` |
| `GET W/context` | 员工/管理者/总部 | 无 | `{employeeId:ID?,roles:string[],permissions:string[],stores:StoreBrief[]}` |

业务身份首次初始化使用已验证手机号，在租户内关联或建立健身会员档案；首次无卡建档记为潜在客户。重复登录不重复建档，有卡老会员不因登录被降级为潜客。记录来源门店和邀请关系，但不允许重复登录随意转移负责员工。

`GET W/context` 已实现（总表6）：只使用当前有效后台账号和令牌租户，不接受客户端指定身份。`employeeId` 为当前已入职且在职的关联员工编号，没有有效员工档案时为 `null`，不因此拒绝合法总部账号。`roles` 是系统实际启用角色代码；`permissions` 是启用角色菜单经停用菜单及其子菜单过滤后的 `gym:*` 权限码，去重排序，不推断“店长/总部”、不返回合成通配符，也不表示这些功能都已开发。

`stores` 是门店入口集合：当前租户营业门店与“整店数据范围、有效SELF负责关系、本人员工有效任职”三者并集的交集，去重后按ID升序。全数据范围可列出当前租户所有营业门店；无匹配时空数组。当前上下文不加载封面、不计算距离，`cover/distanceMeters=null`。本人任职门店出现不代表获得整店会员或财务权限；客户端不得把此列表当授权凭据，后续每次业务请求仍由服务端校验功能和数据范围。权限清单用于入口展示，既有接口授权语义不变。

## 四、首页与公共内容

### 4.1 门店展示

| 方法与路径 | 权限 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET A/stores` | 游客 | `P,keyword?:string,longitude?:number,latitude?:number` | `Page<StoreBrief>`；仅公开营业门店 |
| `GET A/stores/{id}` | 游客 | path：门店 ID | `StoreDetail` |
| `GET A/stores/{id}/home` | 游客 | path：门店 ID | `{store:StoreDetail,occupancy:Occupancy,features:Feature[],notices:Notice[]}` |
| `GET A/stores/{id}/occupancy` | 游客 | path：门店 ID | `Occupancy` |
| `GET A/client-settings` | 游客 | `storeId?:ID` | `{entryCover:Media?,appDownload:{enabled:boolean,url:string?,benefitText:string?},franchise:FranchiseInfo}` |
| `POST A/stores/{id}/camera-play-sessions` | 会员且具有该店监控查看资格 | `cameraId:ID` | `{playUrl:string,expiresAt:DateTime}` |

公共首页不混入个人会员卡、手机号或工资数据；会员卡另外调用本人接口。员工及管理者首页复用公开内容，个性化业务走 W，不向 app-api 传后台令牌。

主要对象：

| 对象 | 字段 |
| --- | --- |
| `StoreBrief` | `id:ID,name:string,cover:Media?,address:string,distanceMeters:number?`；距离仅提供有效经纬度时返回 |
| `StoreDetail` | StoreBrief + `description:string,areaSqm:number,features:string[],longitude:number,latitude:number,businessHours:{opens:string,closes:string},media:Media[],servicePhone:string?,serviceWechat:string?` |
| `Media` | `fileId:ID,type:image或video,url:string,expiresAt:DateTime?,caption:string?`；公开环境素材和受控业务文件分开授权 |
| `Occupancy` | `currentCount:int?,todayEntryCount:int,updatedAt:DateTime?,dataStatus:live或stale或unavailable` |
| `Feature` | `key:string,enabled:boolean,disabledReason:string?`；未接通支付/监控等功能应明确禁用 |
| `Notice` | `id:ID,title:string,content:string,publishedAt:DateTime` |
| `FranchiseInfo` | `configured:boolean,introduction:string,phone:string,models:[{name:string,preview:Media,modelUrl:string?}]`；配置完成时模型数量为 3 种，未配置时字符串为空、models为空数组 |

`currentCount` 指当前在店人数，`todayEntryCount` 指当天入场事件次数，二者不可混用。只有可靠进出事件才能计算实时人数；设备离线时保留更新时间并标记 stale，无可靠来源返回 null。前端建议每 30 秒刷新一次，页面隐藏时停止轮询，此频率为可配置建议。

监控地址使用短时凭据，不返回设备账号密码、长期地址或任意摄像头列表。会员可看哪些摄像头、哪些时间段须在上线前确认。

当前公共展示实现约定：

- 门店以当前租户下`status=active`作为营业且公开的口径；没有额外的独立发布状态。停用/删除/其他租户门店不出现在列表，详情按不存在处理。关键词匹配名称和地址；经纬度须同时提供，分别限定±180/±90、最多7位小数。有位置时在数据库分页前按球面距离升序，未知坐标最后，同距按门店ID；没有位置时按ID升序。距离以米取整，不把缺失坐标当成0。
- 环境素材只来自已绑定门店且属于该门店的image/video文件；不返回原始存储URL。`Media.url`指向辅助入口`GET /app-api/gym/public-media/{fileId}/content?tenantId={当前租户}`，按请求重新核对当前公开引用，支持Range，响应`Cache-Control: no-store`。此辅助路径不增加135项目标计数。仅这条公开GET媒体路径允许把query租户选择器转为header；其余接口仍使用正常租户头。既有租户有效性及登录租户一致性校验继续执行，不提供租户忽略。
- 公开素材不需要登录，但必须在当前租户的营业门店素材、或启用的小程序配置封面/加盟模型中存在有效引用。文件本身类别与内容类型仍校验，会员头像、人脸、体测、合同等私有业务文件不因知道ID而公开。撤销所有公开引用后旧URL不可读取；若总部配置或其他公开引用仍在，素材仍属公开。公开引用按当前状态授权，`expiresAt=null`。
- 人数暂时只统计当前`gym_checkin`中上海当天、截至查询时刻、状态`succeeded`的已登记入场次数；不是完整设备事件覆盖证明。`currentCount=null`、`dataStatus=unavailable`，直到有经过验证的完整进出事件及设备健康来源。`updatedAt`为最后一条已登记成功入场时间，没有记录为null，不能把查询时刻伪装成设备更新时间。
- 首页能力键为`view_cards,book_courses,view_records,view_contracts,payment,auto_renewal,e_sign,access_control,camera`。前四项表示当前后端对应能力可用，登录/会员资格仍在业务入口校验；后五项固定禁用并返回`external_integration_deferred`。不代表前端页面已实现或真实外部接入完成。

展示资料通过已有后台 `PUT /admin-api/gym/client-settings` 配置，要求`gym:content:manage`和对应门店或总部权限，不增加移动管理入口。沿用现有全量替换语义：编辑时需保留所需字段。新增以下可选字段，仍持久化于原有`gym_client_setting.content`，无需新迁移：

| 配置字段 | 内容及校验 | 公开使用 |
| --- | --- | --- |
| `appBenefitText` | 最多2000字符 | `platform=app`的下载福利文案；仅启用且有合法http/https下载地址时展示 |
| `franchise` | `introduction`1～5000字符、`phone`1～32字符；`models`必须恰好3项，每项`name`1～100且不重复、`previewFileId`必填正ID、`modelUrl`可选http/https无账号凭据URL | `platform=miniapp`的品牌资料与三个模型；预览必须为可用图片且符合配置门店归属，没有完整素材时`configured=false`，不提供演示模型 |
| `notices` | 最多50项；`id`正整数且不重复，标题1～120、纯文本正文1～5000、`publishedAt`必填、`expiresAt`可选且晚于发布时间 | 首页仅返回已到发布时间、尚未到期的公告，按发布时间倒序/ID升序 |

小程序封面来自`miniapp.coverFileId`；App下载地址和福利来自`app`配置。两种平台分别先取指定门店整份配置，不存在才回退总部；门店配置存在但`enabled=false`或无效时不回退，不合并单个字段。未配置、禁用或无效配置不发布，下载关闭、封面为空、加盟`configured=false`、公告为空。总部全局公开配置本身仍可通过不传门店的接口读取，不因某门店覆盖而变为私密。


### 4.2 加盟意向（已有接口）

`POST /app-api/gym/franchise-inquiries`，允许匿名，必须携带 `Idempotency-Key`。

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| contactName | string | 是 | 非空，最多 80 字符 |
| phone | string | 是 | 匹配 `1[3-9][0-9]{9}` |
| preferredCity | string | 是 | 非空，最多 100 字符 |
| message | string | 否 | 最多 2000 字符 |

```json
{"code":0,"msg":"","data":{"id":"801","status":"received"}}
```

当前 Controller 配置为 60 秒内 5 次，限流键由 `GymInquiryRateLimiterKeyResolver` 决定。匿名响应不返回联系人、手机号或后台处理进度。

## 五、会员卡、订单、私教购买与包月

### 5.1 接口清单

以下为目标契约，当前实现状态见总接口表；商品查询允许游客，其余要求会员登录。

| 方法与路径 | 请求 | 返回 |
| --- | --- | --- |
| `GET A/card-products` | `P,storeId:ID` | `Page<Product>`，仅可售卡产品 |
| `GET A/course-products` | `P,storeId:ID,coachId?:ID` | `Page<Product>`，仅可售私教产品 |
| `GET A/products/{id}` | `productType:card或course或subscription,storeId?:ID` | `Product`，同时标注购买限制 |
| `GET A/me/cards` | `P,storeId?:ID,status?:string` | `Page<MemberCard>` |
| `GET A/me/cards/{id}` | 无 | `MemberCard`；校验本人归属 |
| `GET A/me/course-packs` | `P,storeId?:ID,coachId?:ID` | `Page<CoursePack>` |
| `POST A/orders`（幂等） | `storeId:ID,productType:string,productId:ID,quantity:int,quoteId?:ID` | `Order` |
| `GET A/me/orders` | `P,status?:string` | `Page<Order>` |
| `GET A/me/orders/{id}` | 无 | `Order` |
| `POST A/orders/{id}/payment-sessions`（幂等） | `channel:weixin_miniapp` | `{paymentId:ID,payParams:object,expiresAt:DateTime}` |
| `POST A/orders/{id}/cancellations`（幂等） | `reason?:string` | 更新后的 `Order`，仅未支付订单 |
| `POST A/orders/{id}/refund-applications`（幂等） | `reason:string` | `{id:ID,status:pending_review,requestedAmount:Money}`；金额由服务端按剩余可退额计算 |
| `GET A/me/refund-applications/{id}` | 无 | `{id:ID,orderId:ID,status:string,requestedAmount:Money,refundedAmount:Money,reason:string,failureReason:string?}` |
| `GET A/subscription-products` | `P,storeId:ID` | `Page<Product>`，明确周期价格与解约说明 |
| `POST A/subscriptions`（幂等） | `storeId:ID,productId:ID,agreementVersion:string` | `{id:ID,status:pending_sign,signParams:object?,expiresAt:DateTime?}` |
| `GET A/me/subscriptions` | `P,storeId?:ID` | `Page<Subscription>` |
| `GET A/me/subscriptions/{id}` | 无 | `Subscription` |
| `POST A/subscriptions/{id}/termination-requests`（幂等） | `reason?:string` | `Subscription`，提交解约不等于渠道已解约 |

`quantity` 为正整数，首期建议限 1；不接受客户端传入支付金额、会员 ID、已支付状态。`payParams` / `signParams` 为选定渠道 SDK 所需参数，由渠道适配器生成；接入时补充具体结构，未配置时返回能力未开通错误，不能返回演示参数。

### 5.2 核心返回对象

| 对象 | 字段 |
| --- | --- |
| `Product` | `id:ID,type:string,storeId:ID,name:string,description:string,price:Money,version:int,validDays:int?,lessonCount:int?,coachId:ID?,saleStatus:string,purchaseLimitReason:string?,renewalTerms:string?,agreementVersion:string?,saleRevision:long?,cardType:duration或times或null,includedUses:int?,billingDay:int?,renewalNoticeDays:int?,graceDays:int?` |
| `MemberCard` | `id:ID,name:string,type:duration或times,status:pending或active或frozen或expired或exhausted或inactive,storeIds:ID[],validFrom:DateTime?,validUntil:DateTime?,remainingTimes:int?,frozenReason:string?` |
| `CoursePack` | `id:ID,productId:ID,storeId:ID,coachId:ID,totalLessons:int,remainingLessons:int,reservedLessons:int,availableLessons:int,validUntil:DateTime?,status:string`；可用余额为剩余减预占且最低为 0；是否可预约还需校验有效期、状态和课程规则 |
| `Order` | `id:ID,orderNo:string,storeId:ID,productType:string,productId:ID,productName:string,quantity:int,payableAmount:Money,paidAmount:Money,refundedAmount:Money,paymentStatus:string,fulfillmentStatus:string,expiresAt:DateTime,memberCardIds:ID[],coursePackIds:ID[]` |
| `Subscription` | `id:ID,storeId:ID,productId:ID,status:pending_sign或active或past_due或terminating或terminated,cycleAmount:Money,nextChargeAt:DateTime?,agreementVersion:string,terminatedAt:DateTime?` |

商品查询已实现：卡/包月按实际门店销售配置定价；详情省略门店时选有合格公开配置的最小营业门店ID，私教使用所属门店。`version`为真实模板/商品版本，`saleRevision`为门店销售配置版本（私教为null）。`saleStatus`为`on_sale/not_started/ended`，列表仅返回on_sale；当前卡/私教购买限制为`checkout_unavailable`，包月为`auto_renewal_integration_deferred`。包月没有已发布的续费条款与协议版本时不展示；配置和版本规则见[实施说明](./uniapp-implementation.md#商品目录实现与边界)。

支付状态与履约状态分开：`paymentStatus=unpaid/paid/closed/partially_refunded/refunded`，`fulfillmentStatus=pending/processing/succeeded/failed`。支付成功但发卡失败显示“已支付，权益处理中”，后台可重试履约，禁止引导重复付款。

### 5.3 业务流程

1. 会员选商品，服务端检查门店、商品版本、资格和价格，创建待支付订单。
2. 服务端创建支付会话，小程序调起支付；客户端支付成功提示只触发订单状态查询。
3. 经签名校验的渠道支付通知或服务端主动查单确认支付结果，验证订单、商户、币种、金额，幂等记录支付并发放权益。
4. 创建卡/私教权益时按合同配置生成待签合同；若合同为权益生效前置条件，则在真实签署前限制使用。
5. 退款申请进入审核，渠道退款成功后调整可退权益和财务记录；审批通过本身不表示退款到账。

连续包月按“微信支付自动续费”设计。用户确认协议并完成渠道签约后方可扣款；每个周期独立记录扣款和权益，失败重试不能重复发卡。解约成功后停止后续扣费，已支付周期如何保留权益由产品规则明确。

### 5.4 团购验券

| 方法与路径 | 权限 | 请求 | 返回 |
| --- | --- | --- | --- |
| `POST A/coupon-verification-previews` | 会员 | `storeId:ID,platform:string,couponCode:string` | `{previewId:ID,productName:string,redeemable:boolean,reason:string?,expiresAt:DateTime}` |
| `POST A/coupon-verifications`（幂等） | 会员 | `previewId:ID` | `{id:ID,status:processing或succeeded或failed,memberCardIds:ID[],failureReason:string?}` |
| `GET A/me/coupon-verifications/{id}` | 会员 | 无 | 同核销结果 |

服务端校验平台门店映射、券归属/资格、核销数量和有效期。核销预览不消耗券；外部超时保持 processing 并查单，不重复核销。渠道确认核销后才发权益。已有管理端团购能力按平台逐项复用，不能直接把管理端任意券查询开放给会员。

## 六、教练、预约、签到与签课

### 6.1 接口清单

| 方法与路径 | 权限 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET A/coaches` | 游客 | `P,storeId:ID` | `Page<Coach>` |
| `GET A/coaches/{id}` | 游客 | `storeId:ID` | `Coach`，含介绍与公开资质 |
| `GET A/coaches/{id}/sessions` | 会员 | `storeId:ID,startDate:Date,endDate:Date,productId?:ID,P`；区间最多31天 | `Page<CourseSession>` |
| `POST A/bookings`（幂等） | 会员 | `sessionId:ID,coursePackId:ID` | `Booking` |
| `GET A/me/bookings` | 会员 | `P,storeId?:ID,startDate?:Date,endDate?:Date,status?:string` | `Page<Booking>` |
| `GET A/me/bookings/{id}` | 会员 | 无 | `Booking` |
| `POST A/bookings/{id}/cancellations`（幂等） | 会员 | `reason?:string` | `Booking` |
| `GET A/me/checkins` | 会员 | `P,storeId?:ID,startDate?:Date,endDate?:Date` | `Page<Checkin>` |
| `GET A/me/attendance-summary` | 会员 | `storeId:ID,month:Month` | `{checkinCount:int,activeDays:int,rank:int?,rankingPopulation:int,updatedAt:DateTime}` |
| `POST A/lesson-confirmations`（幂等） | 会员 | `bookingId:ID,qrToken:string` | `LessonRecord` |
| `GET A/me/lesson-records` | 会员 | `P,storeId?:ID,startDate?:Date,endDate?:Date` | `Page<LessonRecord>` |

| 对象 | 字段 |
| --- | --- |
| `Coach` | `id:ID,name:string,avatar:Media?,introduction:string,specialties:string[],qualificationFiles:Media[]`；不公开手机号等内部资料 |
| `CourseSession` | `id:ID,storeId:ID,coachId:ID,productId:ID,startsAt:DateTime,endsAt:DateTime,capacity:int,remainingCapacity:int,bookable:boolean,unavailableReason:string?` |
| `Booking` | `id:ID,sessionId:ID,coursePackId:ID,storeId:ID,coachId:ID,startsAt:DateTime,endsAt:DateTime,status:confirmed或cancelled或completed或no_show,checkedIn:boolean,cancelDeadline:DateTime?,lessonRecordId:ID?` |
| `Checkin` | `id:ID,storeId:ID,enteredAt:DateTime,exitedAt:DateTime?,source:face或manual,bookingId:ID?` |
| `LessonRecord` | `id:ID,bookingId:ID,checkinId:ID,confirmedAt:DateTime,source:face或qr或manual,deductedLessons:int,remainingLessons:int?`；余额为本次课消时快照，旧数据未知或不限次数时为空 |

### 6.2 预约与签课约束

公开教练查询已实现；总表35–36按当前租户、发布状态、营业门店及已经生效的任职过滤后分页。管理员沿用`POST /admin-api/gym/employees`或`PATCH /admin-api/gym/employees/{id}`的`gym:employees:write`权限，提交下列可选对象，发布到该员工全部当前营业任职门店：

```json
{
  "publicCoachProfile": {
    "published": true,
    "avatarFileId": null,
    "introduction": "管理员填写的教练介绍",
    "specialties": ["力量训练"],
    "qualificationFileIds": []
  }
}
```

对象省略或null保持原值；提供时整体替换，`published=false`下架。头像/资质只接受当前租户内员工任职门店的image类别文件，下载仍检查有效公开引用；不能填入人脸或合同附件。介绍最多4000字，擅长项目最多20项、每项80字，资质图片最多20个且不重复；旧员工默认不公开。工作台本人教练资料入口见第8.1节。完整边界见[实施说明](./uniapp-implementation.md#教练公开资料实现与边界)。

- 首期按现有私教容量 1 设计。创建预约时验证教练、产品、门店、时段、权益有效期和剩余课时，并原子预占 1 课时；同一会员时间冲突拒绝。
- 取消预约在可取消时间内释放预占；已签课不可走普通取消。当前自助取消默认须早于开课，配置 `yudao.gym.mobile.bookings.cancel-before-minutes` 可设置提前分钟数（0～10080）；截止时刻本身不可取消，超时联系门店。此规则不自动扣课，不启用爽约收费或扣课。
- 扫码签课必须已有预约及本课程当日、同店的有效签到，并在允许的课程时间内。前端不能伪造 `checkedIn=true` 代替服务端校验。
- 教练二维码是短时签名 token，默认60秒（可配30～300秒）；服务端绑定租户、门店、教练、排期时间、具体预约与会员，以及预约创建时间。截图过期失效，只有绑定预约的会员可确认。
- 刷脸自动签课须匹配唯一有效预约和时间窗口；无法唯一匹配时只签到，不自动扣课。
- 扫码与人工确认已共用签课服务，`tenantId + bookingId`有课消唯一约束，同一预约只扣一次。重复合法确认返回原课消记录并保留首次真实来源；刷脸接入仍暂缓，尚未调用该服务。
- 月度汇总（总表43）已实现：`storeId`为必填正整数，`month`为必填`YYYY-MM`（1900–9999年）；从会员令牌识别本人，不接受客户端选择其他会员。本人须有该店当前会员关系或该店既往成功签到；失效权益或门店停用不影响本人历史读取。
- 统计同租户、同店、所选上海自然月`[月初,下月月初)`内、截至服务端当前时刻的未删除成功签到。`checkinCount`为成功记录条数，`activeDays`按上海自然日去重；一天多次入场计多次、只计一天。失败、删除、未来记录不计，无法关联有效会员档案的记录不计。
- `rankingPopulation`是同店同月至少有一次上述签到、且会员档案未删除的人数；历史停用会员仍参与统计。按活跃天数降序，竞争排名为`1 + 活跃天数严格更多的会员数`，同分同名次并跳过相应位置（例如1、1、3）。本人无签到时`rank=null`；未来月份各计数为0且名次为空，仍先校验访问权限。只返回本人名次与总人数，不返回其他会员身份或榜单。
- `updatedAt`为本次计算时刻，非最后签到时间。月/日边界由Java的`Asia/Shanghai`时区规则生成，包含历史夏令时；读取在同一只读事务快照内聚合。本地成功签到不代表已接入真实门禁设备。

扫码签课44及工作台二维码82已实现。二维码生成使用后台令牌、`gym:bookings:write`与整店授权或“本人授课且当前负责该会员”范围，针对当前私教容量1的具体预约发码。会员端只接受`bookingId/qrToken`；必须是本人有效身份、真实成功签到，且签到关联同一预约、会员和门店、发生在课程开始的上海自然日，不能用请求补造签到。

扫码允许窗口为`[课程开始,课程结束+confirm-after-end-minutes)`，默认结束后120分钟，可配0～1440分钟。二维码有效期还受窗口截止约束，等于过期时刻即失效。新确认验证签名、租户和全部绑定、当前签发账号/权限/负责关系及营业门店，再执行共享扣课；其他会员、错课码、改期或取消重约后的旧码均不能授权新扣课。已有课消时返回原记录，人工与扫码争抢也只扣一次；新的签到不会在扫码时自动生成。

两个POST均要求`Idempotency-Key`。相同参数和成功键重放首次响应，不再次扣课；新键必须重新验证有效二维码。原成功确认键可在二维码过期后重试以恢复本人结果，不能将它当作重新授权。二维码生成键重放返回原二维码及原过期时间，需新二维码时使用新键；目标已取消时生成重试也拒绝。

部署前执行V011；设置`GYM_MOBILE_LESSON_QR_SIGNING_KEY`为至少32字节随机密钥的标准Base64编码，各实例一致。空值/非法密钥返回“签课二维码未配置”（`1070023015`），二维码无效/过期返回`1070023014`。格式为HMAC-SHA256签名的随机凭据，完整绑定在服务端，不把个人资料编码进二维码。二维码表只存token哈希，生成操作的幂等响应会保存原token以供重试，两个接口关闭访问日志请求/响应正文记录。轮换密钥使旧二维码不能用于新确认；已成功确认的原键重试仍返回其本人结果。

### 6.3 预约请求示例

```http
POST /app-api/gym/bookings
Authorization: Bearer <member-token>
tenant-id: 1
Idempotency-Key: <uuid>
Content-Type: application/json

{"sessionId":"601","coursePackId":"701"}
```

```json
{
  "code":0,
  "msg":"",
  "data":{
    "id":"901","sessionId":"601","coursePackId":"701",
    "storeId":"101","coachId":"201",
    "startsAt":"2026-10-24T14:00:00+08:00",
    "endsAt":"2026-10-24T15:00:00+08:00",
    "status":"confirmed","checkedIn":false,
    "cancelDeadline":null,"lessonRecordId":null
  }
}
```

## 七、会员个人中心与互动

### 7.1 资料、人脸、合同和运动档案

| 方法与路径 | 请求 | 返回/约束 |
| --- | --- | --- |
| `GET A/me/profile` | 无 | `{memberId:ID,nickname:string,avatarFileId:ID?,maskedPhone:string,faceStatus:string}` |
| `PATCH A/me/profile` | `nickname?:string,avatarFileId?:ID` | 更新后资料；昵称 1～30 字符；拒绝手机号字段 |
| `POST A/me/face-enrollments`（幂等） | `fileId:ID,verificationToken:string,consentVersion:string` | `{id:ID,status:pending或active或rejected,reason:string?}`；verificationToken 为服务端认可的身份/活体验证凭据 |
| `GET A/me/face-enrollments/{id}` | 无 | 同人脸登记结果 |
| `GET A/me/contracts` | `P,status?:unsigned或signing或signed或voided` | `Page<Contract>` |
| `GET A/me/contracts/{id}` | 无 | `Contract` |
| `POST A/contracts/{id}/sign-sessions`（幂等） | `agreementVersion:string` | `{sessionId:ID,signUrl:string,expiresAt:DateTime}` |
| `GET A/me/body-measurements` | `P,storeId?:ID,startDate?:Date,endDate?:Date` | `Page<Measurement>` |
| `GET A/me/body-measurements/{id}` | 无 | `Measurement` |
| `GET A/me/training-plans` | `P,storeId?:ID,status?:active或archived` | `Page<TrainingPlan>` |
| `GET A/me/training-plans/{id}` | 无 | `TrainingPlan` |
| `GET A/me/fitness-summary` | `storeId?:ID` | `{latestMeasurement:Measurement?,activePlanCount:int,totalCheckins:int,totalLessons:int}` |

所有接口要求会员登录，服务端从令牌关联本人，不允许请求传入其他会员 ID。

| 对象 | 字段 |
| --- | --- |
| `Contract` | `id:ID,title:string,storeId:ID,status:string,issuedAt:DateTime,signedAt:DateTime?,originalFileId:ID,signedFileId:ID?,agreementVersion:string` |
| `Measurement` | `id:ID,storeId:ID,measuredAt:DateTime,heightCm:number?,weightKg:number?,bodyFatPercent:number?,muscleMassKg:number?,note:string?,reportFileId:ID?,coachId:ID?` |
| `TrainingPlan` | `id:ID,storeId:ID,title:string,content:string?,fileId:ID?,status:active或archived,coachId:ID?,createdAt:DateTime,updatedAt:DateTime` |

人脸新照片验证和设备同步成功前保留旧的有效凭据；失败返回明确原因。用户只能改昵称、头像和经验证的人脸，不能通过通用会员更新接口绕过手机号不可修改规则。

签署会话创建不代表签署完成。签约服务确认签名、签署人和合同版本后，才保存真实签署证据与 signed 状态。普通手写图片上传不能冒充电子合同签署闭环。

运动档案以长期保留为目标，不因卡过期、教练更换或会员换店删除；用版本/归档保留历史。跨店档案对会员本人可见，对员工仍按负责关系和授权范围裁剪。

### 7.2 积分、邀请、反馈、活动、器械

| 方法与路径 | 权限 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET A/me/points` | 会员 | 无 | `{balance:int,totalEarned:int,totalSpent:int}` |
| `GET A/me/point-records` | 会员 | `P,type?:earn或spend或refund` | `Page<{id:ID,delta:int,balanceAfter:int,reason:string,createdAt:DateTime}>` |
| `POST A/me/invitations`（幂等） | 会员 | `storeId:ID` | `{inviteCode:string,sharePath:string,expiresAt:DateTime?}` |
| `GET A/point-products` | 会员 | `P,storeId:ID` | `Page<{id:ID,name:string,requiredPoints:int,stock:int?,cardProductId:ID}>` |
| `POST A/point-redemptions`（幂等） | 会员 | `storeId:ID,productId:ID` | `{id:ID,status:processing或succeeded或failed,spentPoints:int,memberCardId:ID?}` |
| `GET A/me/point-redemptions/{id}` | 会员 | 无 | 同兑换结果 |
| `POST A/feedbacks`（幂等） | 会员，对门店匿名 | `storeId:ID,type:feedback或review,content:string,rating?:int,fileIds?:ID[],allowCardContext?:boolean` | `{id:ID,status:received,createdAt:DateTime}` |
| `GET A/me/feedbacks` | 会员 | `P,storeId?:ID` | `Page<{id:ID,type:string,content:string,rating:int?,status:string,reply:string?,createdAt:DateTime}>` |
| `GET A/activities` | 游客 | `P,storeId:ID,status?:open或ended` | `Page<Activity>` |
| `GET A/activities/{id}` | 游客 | 无 | `Activity` |
| `POST A/activities/{id}/registrations`（幂等） | 会员 | 无 body | `{id:ID,activityId:ID,status:registered,registeredAt:DateTime}` |
| `DELETE A/activities/{id}/registrations/me` | 会员，业务幂等 | 无 | `{status:cancelled}`，遵守活动取消截止时间 |
| `GET A/me/activity-registrations` | 会员 | `P,status?:string` | `Page<{id:ID,activity:Activity,status:string,registeredAt:DateTime}>` |
| `POST A/qr-resolutions` | 游客 | `scene:string` | `{type:equipment,equipmentId:ID,storeId:ID}`；仅解析服务端认可的器械码 |
| `GET A/equipment/{id}/tutorials` | 游客 | 无 | `{equipmentId:ID,name:string,tutorials:[{id:ID,title:string,content:string?,image:Media?,video:Media?}]}` |

`Activity`：`id:ID,storeId:ID,storeIds:ID[],title:string,description:string,cover:Media?,startsAt:DateTime,endsAt:DateTime,registrationDeadline:DateTime,cancelDeadline:DateTime?,capacity:int?,registeredCount:int,status:string`。`storeIds`保留原后台跨店活动，`capacity=null`表示历史不限名额；移动创建仍要求1–100000的整数名额。

规则：

- 消费积分基于已确认实付金额发放，退款按来源账本冲回；邀请奖励的触发条件需确认，不能仅凭生成邀请链接发积分。
- 兑换扣积分与创建兑换单同事务，发卡有业务唯一键。异步发卡失败进入可查询的处理中/失败状态，重试不能重复扣积分；最终失败返还积分并记流水。
- 反馈内容 1～2000 字符，附件最多 9 个；评价 `type=review` 时必须有 1～5 整数评分。`allowCardContext` 默认 false。
- “匿名”暂定义为对门店员工隐藏身份，后台为防滥用保留受限关联。管理端列表、附件元信息和回复均不得泄露手机号、会员 ID、卡号；如要求对平台也匿名，需调整方案。
- 活动报名以活动与会员唯一，名额原子扣减；同一会员重复报名返回已有记录。过期、取消、满额活动拒绝报名。

活动接口（总表66–70、121）已实现：

- 公开列表要求营业门店，只展示`published/closed`活动，草稿/取消/删除不可公开读取。默认按开始时间倒序、ID倒序；状态条件在SQL分页前应用。响应状态为`upcoming/open/registration_closed/ongoing/ended`，本人历史还可有`cancelled`。`open`表示报名窗口`[registrationStartsAt,registrationDeadline)`，即使名额已满仍为open，由`capacity/registeredCount`显示剩余名额；写入时再次检查满额。`ended`包括已结束或后台已关闭活动。
- 原后台多店活动共享一个总名额。列表`storeId`是筛选门店，公开详情取当前营业关联门店最小ID，`storeIds`列当前营业关联门店；本人历史`storeId`保留实际报名门店，`storeIds`保留原活动关联门店。`registeredCount`为该活动全部门店的成功报名总人数，不返回报名人身份。
- 报名无body、必传`Idempotency-Key`，只使用当前会员。首次报名优先选“默认门店且当前会员关系有效、营业、属于活动”，否则取满足相同条件的最小门店ID；均不满足则拒绝。已有报名固定原门店，不随默认门店变化迁移。写事务锁活动再锁会员，与旧后台共用名额和重复报名规则；活动/会员唯一，不会跨端超卖。
- 同一活动已报名时返回原记录；取消后用新请求键可在有效报名窗口重新报名，复用原报名ID并更新报名时间。同键重试返回首次响应快照，取消后重试旧键不会重新占名额，当前状态应读取报名历史。账号、租户与有效会员身份在重放前仍检查。
- 会员取消不需要幂等头，没有本人报名或已经取消返回`{status:cancelled}`。有有效报名时须活动仍published且当前时刻严格早于`cancelDeadline`；为空时使用活动开始时间。取消只释放名额，不删除历史；已失去门店关系仍可取消本人合法报名。旧后台管理取消保留原“活动开始前”权限，可处理会员截止时间之后的取消。
- 本人历史按报名时间和ID倒序，状态筛选registered/cancelled；门店停用不丢失历史。如活动被软删除无法还原其详情，保留报名记录并返回`activity=null`，不制造内容；活动整体取消会沿用原后台事务取消所有有效报名。
- 工作台创建须有效后台账号、`gym:activities:manage`及整店范围，原子创建并发布单店活动；报名从创建时刻开放，报名截止必须在未来且不晚于开始，结束严格晚于开始。可选取消截止介于开放与开始之间，可早于报名截止；这种情况下晚报名会员可能已不能自助取消。名称1–100、介绍1–4000字符，额外状态/报名人数/身份字段拒绝。幂等请求键变更内容报冲突；同键恢复首次结果前重查当前门店权限。
- V013增加可空`cancel_deadline`；旧活动保留NULL并默认开始前取消，不改历史名额。已有任何报名记录后，后台不得修改取消截止、活动时段或门店，避免改变已报名规则。封面须原后台上传的所属活动门店image文件，带其他移动工作用途绑定的文件不能挪作公开封面；当前移动上传没有活动封面用途。公开封面走现有受控素材URL，读取重查发布、门店、归属、用途及实际内容；取消最后公开引用后不可下载。

### 7.3 文件接口

| 方法与路径 | 权限 | 请求 | 返回 |
| --- | --- | --- | --- |
| `POST A/files` | 会员 | multipart：`file:binary,purpose:avatar或face或feedback,storeId?:ID` | `{fileId:ID,name:string,mimeType:string,byteSize:int}` |
| `POST W/files` | 员工及以上 | multipart：`file:binary,purpose:coach_profile或body_report或training_plan或contract,storeId:ID,memberId?:ID` | 同上；体测/训练档案校验目标会员范围 |
| `GET A/files/{id}/content` | 本人可访问的文件 | 无 | 二进制，携带正确 Content-Type |
| `GET W/files/{id}/content` | 有相应业务及数据权限 | 无 | 二进制 |

当前工作台上传已实现：coach_profile为JPEG/PNG、5 MiB；body_report/training_plan为PDF/JPEG/PNG、20 MiB且必填memberId；contract仅PDF、20 MiB。合同提供memberId时检查会员范围及gym:contracts:write，不提供时要求gym:contracts:templates和整店范围；上传不表示合同已签署。所有工作台上传/下载还分别要求gym:files:upload/read，业务权限不会由文件权限替代。

文件用途和目标随上传持久化，体测/训练及模板绑定不能改作其他目标；上传人从令牌取得。教练图片按员工归属下载，未绑定的会员附件仅原上传人在仍有目标权限时可预览，绑定后重新检查当前业务授权。响应不带存储URL，下载禁缓存；精确边界及旧文件兼容见[实施说明](./uniapp-implementation.md#工作台文件实现与边界)。

会员端头像/人脸仍为JPEG/PNG、5 MiB，反馈为JPEG/PNG、10 MiB。不能只修改扩展名通过类型校验。上传与业务绑定是两个步骤，已绑定运动档案不得按临时文件清理；数据库回滚后的孤立存储对象自动清理尚未实现。

## 八、员工、管理者和总部工作台

本章全部为 W 新增适配接口。共用接口由服务端按照角色和权限返回不同范围，而不是前端传 `role=manager` 决定范围。

### 8.1 工作台与预约

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/dashboard` | 员工及以上 | `storeId?:ID,date?:Date` | `Dashboard`，默认当天 |
| `GET W/bookings` | 员工及以上 | `P,storeId?:ID,startDate:Date,endDate:Date,status?:string,coachId?:ID` | `Page<WorkBooking>` |
| `GET W/bookings/{id}` | 员工及以上 | 无 | `WorkBooking` |
| `GET W/checkins` | 员工及以上 | `P,storeId?:ID,startDate:Date,endDate:Date,memberId?:ID` | `Page<WorkCheckin>` |
| `GET W/lesson-records` | 员工及以上 | `P,storeId?:ID,startDate:Date,endDate:Date,coachId?:ID` | `Page<WorkLessonRecord>` |
| `POST W/lesson-qr-codes`（幂等） | 本人授课教练/获授权管理者 | `sessionId:ID` | `{qrToken:string,expiresAt:DateTime}` |
| `GET W/me/coach-profile` | 教练 | 无 | `Coach` |
| `PATCH W/me/coach-profile` | 教练 | `avatarFileId?:ID,introduction?:string,specialties?:string[],qualificationFileIds?:ID[]` | `Coach`，仅修改本人公开资料 |
| `POST W/course-sessions`（幂等） | 有排课权限的员工/管理者 | `storeId:ID,coachId:ID,productId:ID,startsAt:DateTime,endsAt:DateTime` | `CourseSession` |
| `POST W/bookings`（幂等） | 负责会员的员工/管理者 | `memberId:ID,sessionId:ID,coursePackId:ID` | `WorkBooking` |

工作台写入85–86已实现。排课要求`gym:courses:write`及有效后台身份，允许当前账号关联的有效员工给本人任职门店排课（不要求已有负责会员），或由具有整店数据范围的管理者为授权门店排课。本人操作不授予整店管理权限，原后台排课权限不变。教练与任职须已生效，商品为同门店同教练的启用私教商品，门店营业；开始时间须在未来，时长等于商品课时长度。时间保存到微秒，容量固定1、初始预占0、状态scheduled；不能由请求改容量、状态或价格。与旧后台共用教练锁及重叠检查，紧邻时段允许，重叠拒绝。

排课返回`CourseSession`；因请求未选定会员，`bookable=false,unavailableReason=member_context_required`，不能把排课成功当作任意会员都能约。代约要求`gym:bookings:write`，SELF同时满足本人授课与当前负责会员关系，管理者按整店范围；只接受`memberId/sessionId/coursePackId`。服务端校验实际有效私教商品，再复用原后台预约链路检查会员、门店、黑名单、课包归属和有效期、可用课时、名额及会员时间冲突。成功只预占1课时，不消耗课时或完成签课。

两项必传`Idempotency-Key`（8～128位字母、数字、下划线或连字符），按租户、后台账号和操作类型隔离。相同键相同参数返回首次成功结果，参数变化报冲突；业务写入与结果同事务，失败一起回滚。同键重放先重新检查当前身份和数据范围；不重新执行排期/预占，后续业务状态变化不改写旧响应。预约取消后以旧创建键重试仍返回首次结果、不会重新预占，要重约必须换新键；当前状态通过预约详情GET查询。

本人教练资料GET/PATCH（总表83–84）已实现。使用后台令牌，服务端关联唯一在职员工并检查当前营业门店任职，以及已有教练资料或有效私教商品；不会接受客户端指定员工。仅编辑本人字段，省略保留，头像null清除，介绍/数组不接受null，可用空字符串/空数组清空。管理员发布状态保持不变，新建草稿不会自动公开。

新图片须为本人后台账号上传、当前任职门店的image文件，原来由管理员绑定的图片可保留。本人响应中的图片地址为`/admin-api/gym/mobile/me/coach-profile/files/{id}/content`，需后台令牌和租户头，按当前资料绑定鉴权；这是不计入135项的预览辅助入口。工作台上传74已实现，可使用coach_profile用途上传本人图片，需已有gym:files:upload权限和有效教练任职。详情见[实施说明](./uniapp-implementation.md#本人教练资料实现与边界)。

`WorkBooking/WorkCheckin/WorkLessonRecord` 为会员端相应对象加 `memberId,memberName,coachId`；WorkLessonRecord另带`storeId`。当前查询不返回手机号、内部备注或操作人资料。员工的预约/课消强制为本人授课；负责会员相关记录还需会员服务关系授权。签到默认只看本人负责会员，管理者看授权门店。

工作台查询78–81已实现：预约列表/详情要求`gym:bookings:read`，签到列表要求`gym:checkins:read`，课消列表要求`gym:lessons:read`，同时要求有效后台身份。SELF预约/课消必须同时匹配当前会员负责关系及本人授课，整店范围按授权门店放行。所有范围和筛选在SQL分页计数前执行；无权查看与不存在的预约详情统一报“预约不存在”。

三个列表均须提供上海日期`startDate/endDate`，区间为`[startDate,endDate)`；预约按课程开始时间筛选，签到按入场时间，课消按确认时间。预约按预约时间及ID降序，签到/课消按对应事件时间及ID降序，页大小1～200。预约状态只接受`confirmed/cancelled/completed/no_show`，存储`attended`映射`completed`。签到只返回`succeeded`，课消只返回`confirmed`；真实来源原样返回，不代表设备已接通。

预约需能关联同租户、同门店的未删除排期；不能核验归属的异常预约不返回。`checkedIn`及`lessonRecordId`检查关联会员/门店（课消还检查教练），不使用不相符的记录。签到无有效对应预约时`bookingId/coachId=null`。历史卡预约没有课包时`coursePackId=null`；会员资料已删除时`memberName=null`。课消`remainingLessons`来自确认时保存的余额，旧记录无法还原时null；`cancelDeadline`沿用会员自助取消截止时间展示，不授予后台取消权限。授权管理者仍可查停用门店历史记录，SELF随当前任职/负责关系收回访问。

`Dashboard`：

```json
{
  "scope":{"type":"store","storeIds":["101"],"employeeId":null},
  "date":"2026-10-23",
  "performance":{"todayAmount":"6800.00","monthAmount":"125000.00"},
  "checkins":{"todayCount":86,"yesterdayCount":79},
  "lessons":{"todayCount":12,"yesterdayCount":10,"monthCount":226},
  "members":{"newTodayCount":5,"responsibleCount":null,"storeTotalCount":820},
  "pendingApprovalCount":3,
  "updatedAt":"2026-10-23T16:00:00+08:00"
}
```

员工响应 `scope.type=self`，`employeeId` 为本人，仅返回本人指标；未授权指标返回 null，不能返回门店汇总再交给前端隐藏。该接口已实现，功能权限、日期与当前人数/待办口径见本文末尾“工作台首页概览已实现补充”。

### 8.2 会员、潜客、分配和私信

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/members` | 员工及以上 | `P,storeId?:ID,keyword?:string,segment?:all或prospect或private_course或card_expiring或course_expiring,expireWithinDays?:int,coachId?:ID` | `Page<WorkMember>` |
| `GET W/members/{id}` | 员工及以上 | `storeId:ID` | `WorkMember`，含本次授权范围内权益摘要 |
| `GET W/members/{id}/follow-ups` | 负责员工/管理者 | `P,storeId:ID` | `Page<FollowUp>` |
| `POST W/members/{id}/follow-ups`（幂等） | 负责员工/管理者 | `storeId:ID,content:string,nextContactAt?:DateTime` | `FollowUp` |
| `PUT W/members/{id}/assignments`（幂等） | 管理者/总部且有分配权限 | `storeId:ID,employeeIds:ID[],expectedVersion:int` | `{memberId:ID,storeId:ID,employeeIds:ID[],version:int}` |
| `POST W/member-conversations`（幂等） | 有私信权限的管理者/负责员工 | `storeId:ID,memberId:ID` | `{id:ID}` |
| `GET W/member-conversations/{id}/messages` | 会话授权参与者 | `P,beforeId?:ID` | `Page<Message>` |
| `POST W/member-conversations/{id}/messages`（幂等） | 会话授权参与者 | `content:string` | `Message` |
| `GET A/me/conversations` | 会员 | `P` | `Page<{id:ID,storeId:ID,unreadCount:int,lastMessage:Message?}>` |
| `GET A/me/conversations/{id}/messages` | 会员本人 | `P,beforeId?:ID` | `Page<Message>` |
| `POST A/me/conversations/{id}/messages`（幂等） | 会员本人 | `content:string` | `Message` |

`WorkMember`：`id,name,maskedPhone,customerType,storeId,coachIds,cardSummary,courseSummary,assignmentVersion`。摘要仅包含授权门店的卡有效期、课时余额等字段；潜客筛选不等价于“没有任何历史消费”。

会员列表每名会员只返回一行，按会员ID倒序，`total`按会员计数。指定`storeId`时只读取该门店；省略时，选择该会员**满足所有筛选且有权访问的最小门店ID**，响应的`storeId`、负责员工、权益摘要和版本均对应这一门店，不合并跨店版本。详情须指定门店。查询支持姓名、手机号、会员编号关键词；返回手机号脱敏，不返回原手机号及内部操作人。

- `segment`默认`all`；`prospect`按真实客户阶段`lead`筛选并映射为`customerType=prospect`，其他客户阶段保持实际值。它不会以消费历史重新推断潜客。
- `private_course`要求当前门店有已生效、未过期、状态active且剩余课时大于0的课包，已全部预占仍属于持有私教权益。`card_expiring`/`course_expiring`只包含当前有效权益，到期区间为`(查询时刻, 查询时刻+expireWithinDays天]`；默认30天，允许1～365，仅在这两种分组使用。未生效、失效、冻结、耗尽、到期时刻相等及无到期时间的权益不进入到期分组。
- `coachId`按本门店启用的负责员工关系筛选（员工ID），与响应`coachIds`同一口径，不是课包授课教练筛选。读取负责关系不自动删除已离职员工的历史分配；SELF可访问范围仍另行检查账号、当前任职及有效负责关系。
- `CardSummary`：`activeCount,durationCount,timesCount,remainingUses,reservedUses,availableUses,nearestExpiresAt`。次数合计只计算次数卡；期限卡计入卡数量，不编造使用次数。`CourseSummary`：`activeCount,remainingLessons,reservedLessons,availableLessons,nearestExpiresAt`。两个摘要只合计响应门店当前有效、未耗尽的权益；卡适用门店取当前权益关系表，不能仅凭旧`accessScope=all`跨店展示。可用量按每项`max(0,剩余-预占)`相加；最近到期时间取非空最小值，全部无限期或没有有效权益时为null，空摘要数量为0。时间按上海时区输出。
- `assignmentVersion`与`coachIds`在同一数据库读事务快照中取得，提交分配时作为`expectedVersion`。实际写入仍重新校验当前权限及版本。


`FollowUp`：`id,memberId,storeId,employeeId,content,nextContactAt?,createdAt`；正文1～2000字符，下次联系时间新增时须在未来，员工ID来自当前账号关联的唯一在职员工。`Message`：`id,conversationId,senderType:member或employee,content,createdAt`；正文 1～2000 字符。

分配更新对当前门店负责关系整体替换；须核对 `expectedVersion` 防止覆盖他人修改，新员工必须在职、已到入职及门店任职日期并关联有效后台账号；单次最多20名且不能重复。旧后台替换负责关系也维护同一版本。分配不修改其他门店关系。私信先按站内会话设计，是否还需微信客服渠道另行确认。

### 8.3 体测、训练计划、合同与发卡

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/body-measurements` | 负责员工/管理者 | `P,storeId:ID,memberId:ID` | `Page<Measurement>` |
| `POST W/body-measurements`（幂等） | 负责员工/管理者 | `storeId:ID,memberId:ID,measuredAt:DateTime,heightCm?:number,weightKg?:number,bodyFatPercent?:number,muscleMassKg?:number,note?:string,reportFileId?:ID` | `Measurement` |
| `GET W/training-plans` | 负责员工/管理者 | `P,storeId:ID,memberId:ID,status?:string` | `Page<TrainingPlan>` |
| `POST W/training-plans`（幂等） | 负责员工/管理者 | `storeId:ID,memberId:ID,title:string,content?:string,fileId?:ID,status:active或archived` | `TrainingPlan` |
| `GET W/contracts` | 负责员工/管理者 | `P,storeId:ID,memberId:ID,status?:string` | `Page<Contract>` |
| `POST W/contracts`（幂等） | 有合同发放权限的管理者 | `storeId:ID,memberId:ID,sourceType:member_card或course_pack,sourceId:ID,templateId:ID` | `Contract` |
| `POST W/card-issue-applications`（幂等） | 管理者/总部且有发卡权限 | `storeId:ID,memberId:ID,productType:card或course,productId:ID,reason:string,approvalTemplateId:ID` | `Approval` |

体测沿用现有校验范围：身高 0.01～300 cm、体重 0.01～500 kg、体脂率 0～100%、肌肉量 0～500 kg；备注最多 2000 字符。训练计划标题最多 120、正文最多 20000 字符。创建人从当前账号获取，不接受冒名上传。

发卡申请通过最终审批后才落地权益及合同，申请不能直接生成有效卡。已有合同依据实际权益创建，不接受伪造的“已购买”状态；同一来源重复发合同返回既有结果。

### 8.4 业绩与总部汇总

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/reports/sales` | 管理者/总部 | `P,storeId?:ID,startDate:Date,endDate:Date,productType?:card或course,employeeId?:ID` | `{list:SaleRow[],total:int,summary:SalesSummary}` |
| `GET W/reports/performance` | 员工及以上 | `P,storeId?:ID,startDate:Date,endDate:Date,groupBy:employee或store` | `{list:PerformanceRow[],total:int,summary:PerformanceSummary}` |
| `GET W/reports/member-summary` | 管理者/总部 | `storeId?:ID,date:Date` | `{memberCount:int,privateCourseMemberCount:int,newMemberCount:int,expiringCardCount:int,expiringCourseCount:int,date:Date,updatedAt:DateTime}` |

`SaleRow`：`id,orderNo?,storeId,employeeId?,productType,productName?,paidAmount:Money,refundedAmount:Money,netAmount:Money,paidAt:DateTime?`。

`SalesSummary`：`paidAmount,refundedAmount,netAmount,paidOrderCount,cardAmount,cardOrderCount,cardAverageOrderAmount,courseAmount,courseOrderCount,courseAverageOrderAmount`，金额均为 Money。

`PerformanceRow`：`storeId?,employeeId?,name,rank?,netAmount,paidOrderCount,lessonCount,averagePerDayAmount`。

`PerformanceSummary`：`netAmount,employeeCount,storeCount,averagePerEmployeeAmount,averagePerStoreAmount,lessonCount`。员工只能查本人，不能借 `groupBy=store` 获取门店业绩；员工响应不返回其他员工数量与汇总。

建议统计口径：

- 销售额按已确认实收减已确认退款，未付款订单和待审批改价不计入；退款按退款发生日冲减。此为设计建议，实施时需与现有财务报表口径对齐。
- 客单均价 = 对应产品净实收 / 对应已付款订单数；每人平均业绩 = 员工归属业绩总和 / 参与统计员工数；每店平均业绩 = 门店净实收 / 参与统计门店数。分母为零返回 `"0.00"`。
- 日均业绩 = 当前范围净实收 / 查询区间自然日数；员工数量按查询区间内有有效任职且纳入报表的人员去重，门店数按授权且纳入筛选的门店去重，均包含零业绩对象。
- 多人销售归属按固定分摊快照计算，分摊金额之和等于订单可归属金额；总部汇总不能把每个员工的完整订单重复相加。
- 总部不传 `storeId` 汇总授权门店，传具体门店下钻。超范围门店拒绝，不静默扩大或返回全租户。
- 销售、退款、签课是独立指标；“本月总消课”按成功课消记录统计，不能拿预约数代替。

### 8.5 工资与审批

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/me/payrolls` | 员工本人 | `P,month?:Month` | `Page<Payroll>`，仅已发布记录 |
| `GET W/me/payrolls/{id}` | 员工本人 | 无 | `Payroll` |
| `GET W/payrolls` | 管理者/总部且有工资查看权限 | `P,storeId:ID,month:Month,employeeId?:ID` | `Page<Payroll>` |
| `GET W/payrolls/{id}` | 管理者/总部且有范围权限 | 无 | `Payroll` |
| `GET W/approvals` | 员工及以上 | `P,storeId?:ID,view:mine或pending或processed,status?:string` | `Page<Approval>` |
| `GET W/approvals/{id}` | 申请人、指定审批人或授权管理者 | 无 | `Approval` |
| `GET W/approval-options` | 有申请权限的员工及以上 | `storeId:ID,businessType:string` | `{templates:[{id:ID,name:string,version:int}],businessTypes:string[]}` |
| `POST W/approvals/{id}/decisions`（幂等） | 当前步骤指定审批人 | `decision:approve或reject,comment?:string,expectedVersion:int` | `Approval`；拒绝必须填写原因 |

`Payroll`：`id,storeId,employeeId,month,status,baseAmount,commissionAmount,bonusAmount,deductionAmount,totalAmount,items:[{name,amount,description}]`；金额均为 Money。管理者与总部按需求拥有相同的工资表查看能力，实际门店范围仍由授权控制。计算、确认、发布流程复用后台，不默认给小程序所有人计算权限。

工资查询（总表108–111）已实现，具体约定：

- 移动`Payroll.id`为`gym_payroll_item.id`，即“员工在某门店某月的一份工资明细”；不是旧后台整店工资批次`gym_payroll.id`。列表按批次ID降序、员工ID及明细ID升序，分页前按租户、门店、月份和员工过滤，`total`计员工工资明细条数。`month`支持1000–9999年，管理查询必须同时给出门店和月份；本人查询月份可省略。
- 本人入口仅需有效后台身份及唯一当前在职、已入职员工绑定，不要求整店工资读取权限；即使拥有全店权限，`/me`也只返回本人且已确认、已发布的工资。离职、账号停用、绑定改变后重新校验。请求不能指定其他员工，其他人或未发布明细统一不可读。
- 管理入口要求`gym:payroll:read`和整店数据范围；SELF或仅本人任职不等于全店工资授权。管理者可读`calculated`、`confirmed`、`published`；不要求当前门店仍营业或历史工资所属员工仍在职。列表过滤`employeeId`时不扩展门店权限。
- `baseAmount`为冻结底薪；`commissionAmount`为会员卡销售、私教销售、课消提成之和；`bonusAmount`为固定加项，`deductionAmount`为扣项汇总，`totalAmount`为原账本实发金额。均为两位小数人民币元字符串。`items`从已保存计算项目投影，扣项明细为负值；不按当前模板重算，不输出源交易、他人明细、工资配置或内部完整快照。统计金额可能包含员工跨店来源汇总，但不暴露跨店源记录。
- 后台辅助发布接口：`POST /admin-api/gym/payrolls/{id}/publications`，这里`id`是旧后台整店工资批次ID（从原后台工资列表获得），无请求体；要求有效后台账号、`gym:payroll:confirm`及整店范围。仅已确认批次可发布，返回`{id:ID,publishedAt:DateTime}`；相同批次重复/并发调用返回首次发布时间，无重复副作用。该辅助接口支撑108–111，不加入固定135个目标数量。
- V012新增`published_at/published_by`，原状态仍为`confirmed`并保持不可重算，移动响应有发布时间才投影`published`。确认不自动发布，历史已确认记录也不自动公开；暂无撤销发布动作。发布人/时间持久化，原后台工资响应增加可空`publishedAt`。旧后台仅SELF的读取同时要求已发布与当前有效员工，管理者整店审核草稿能力保留。

`Approval`：`id,businessType,storeId,applicantId,status:pending或approved或rejected,currentStep:int,version:int,businessSummary:object,steps:[{step:int,approverId:ID,status:string,comment:string?,decidedAt:DateTime?}],createdAt`。`businessSummary` 只能是发卡/改价等对应的受控摘要，不能任意输入可执行业务指令。

112–115已实现，具体可见范围、快照白名单、版本与幂等语义见本文末尾“移动审批读取、模板与决策已实现补充”。审批操作必须校验当前步骤、账号、权限、版本及业务快照。并发审批只允许一个决策生效，最后一步与本地业务变更同事务；外部支付、签约等异步动作有独立状态。

### 8.6 私教产品链接和改价

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `POST W/course-price-applications`（幂等） | 教练/管理者 | `storeId:ID,productId:ID,memberId?:ID,proposedAmount:Money,reason:string,approvalTemplateId:ID` | `Approval` |
| `POST W/course-quotes`（幂等） | 管理者或已获批准的教练 | `storeId:ID,productId:ID,memberId?:ID,approvalId?:ID` | `Quote` |
| `GET A/course-quotes/{id}` | 被允许购买的会员 | 无 | `Quote`，不返回内部审批意见 |

`Quote`：`id,storeId,productId,memberId?,amount:Money,expiresAt:DateTime,status:active或expired或used,sharePath:string`。

服务端计算报价：未改价使用商品现价；优惠价必须来自有效审批快照。教练不能直接传入“已批准”金额生成报价，管理员直接改价能力需单独确认权限策略。报价使用建议 24 小时有效期，绑定商品版本、门店、适用会员；创建订单传 `quoteId`，结算时重新验证。链接不得携带可直接信任的金额参数。

116–118专属改价申请、报价生成与会员读取已实现，见本文末尾补充。专属报价使用独立实体，不能为了一个客户的优惠覆盖所有人的商品公开售价；订单结算绑定仍待后续完成。

### 8.7 评价、活动与设备操作

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/feedbacks` | 管理者/总部 | `P,storeId:ID,type?:feedback或review,status?:received或replied` | `Page<{id:ID,type:string,rating:int?,content:string,fileIds:ID[],status:string,createdAt:DateTime,reply:string?,cardContext:{activeCardCount:int,activeCoursePackCount:int}?}>`，不返回身份 |
| `POST W/feedbacks/{id}/replies`（幂等） | 管理者 | `content:string` | `{id:ID,status:replied,reply:string}` |
| `POST W/activities`（幂等） | 有活动管理权限的管理者 | `storeId:ID,title:string,description:string,coverFileId?:ID,startsAt:DateTime,endsAt:DateTime,registrationDeadline:DateTime,cancelDeadline?:DateTime,capacity:int` | `Activity` |
| `GET W/devices` | 有设备查看权限的管理者 | `P,storeId:ID,type?:door或locker` | `Page<{id:ID,name:string,type:string,status:string}>` |
| `GET W/devices/{id}/compartments` | 有柜格查看权限的管理者 | `P` | `Page<{id:ID,number:string,status:string}>` |
| `POST W/devices/{id}/commands`（幂等） | 有远程操作权限的管理者 | `command:open_door或open_locker,compartmentId?:ID,reason:string,confirmationToken:string` | `DeviceCommand` |
| `GET W/device-commands/{id}` | 发起人或授权管理者 | 无 | `DeviceCommand` |
| `POST W/action-confirmations` | 有相应操作权限的管理者 | `action:open_door或open_locker,resourceId:ID,verificationCode:string` | `{confirmationToken:string,expiresAt:DateTime}`；verificationCode 来自已配置的二次验证渠道 |

`DeviceCommand`：`id,deviceId,command,status:queued或sent或succeeded或failed或unknown,requestedAt,completedAt?,failureReason?`。

开柜必须传 `compartmentId`，开门不得传；校验柜格所属设备。二次确认 token 绑定账号、设备及操作，短时有效。命令接收/发送成功不代表门已打开；实际设备回执确认后才标记 succeeded，无明确回执显示 unknown，禁止自动反复开门。记录操作者、原因、门店、设备、请求和回执。

## 九、消息、门禁事件与风控

### 9.1 小程序消息

| 方法与路径 | 使用方 | 请求 | 返回 |
| --- | --- | --- | --- |
| `GET W/notifications` | 员工及以上 | `P,type?:string,unreadOnly?:boolean` | `Page<Notification>` |
| `GET A/me/notifications` | 会员 | `P,type?:string,unreadOnly?:boolean` | `Page<Notification>` |
| `POST W/notifications/{id}/read` | 接收人 | 无 | `true`，重复已读成功 |
| `POST A/me/notifications/{id}/read` | 接收人 | 无 | `true` |
| `POST A/me/notification-preferences` | 会员 | `templateKeys:string[],enabled:boolean` | `{saved:boolean}`；仅记录意愿，不伪造微信授权成功 |

`Notification`：`id,type,title,content,businessId?,storeId?,createdAt,readAt?`。站内消息为可查询记录，微信订阅消息依赖用户授权和渠道配置；拒绝授权不影响业务操作。

入场推送路由：

| 入场对象 | 店长/负责人 | 私教员工 |
| --- | --- | --- |
| 本店会员 | 授权门店负责人全部接收 | 仅当前负责该会员的员工接收 |
| 陌生新客/潜客 | 仅店长接收 | 不推送 |

推送按事件和接收人去重；打开通知再次校验权限。权限撤销后历史消息中的详情也不能继续访问。会员本人只接收其预约、合同、权益、活动等相关消息，不接收他人入场记录。

### 9.2 设备与第三方回调边界

以下是服务端集成接口设计，不由小程序直接调用；具体网关前缀应在接入时配置，不放在游客接口白名单中裸奔：

| 方法与建议路径 | 来源 | 核心内容 | 处理结果 |
| --- | --- | --- | --- |
| `POST /integration-api/gym/access-events` | 已认证门禁网关 | `eventId,deviceId,occurredAt,direction,subjectToken,verificationResult` | 受理回执；持久化入出场、签到及风控事件 |
| `POST /integration-api/gym/device-command-results` | 已认证设备网关 | `eventId,commandId,result,occurredAt` | 幂等更新实际命令结果 |
| 支付/自动续费渠道通知 | 已验签支付渠道 | 由渠道定义 | 校验并推进支付、解约、履约状态 |
| 电子合同通知 | 已验签签约渠道 | 由渠道定义 | 校验合同身份及真实签署证据 |

设备事件采用设备/网关身份认证、签名、时间戳、nonce 防重放；租户与门店由已登记设备映射，不信任事件中任意传入的租户。对事件 ID 建唯一约束；回调乱序不能回退已完成状态。接收回执格式服从对应设备/渠道协议，不强制使用小程序 CommonResult。

### 9.3 风控规则

| 需求 | 建议判定与处理 | 待确认项 |
| --- | --- | --- |
| 一人一刷脸 | 经设备核验的人脸身份关联有效会员，禁止用会员 ID 代替人脸核验结果 | 活体能力和异常人工复核流程 |
| 30 秒同设备多人入场报警 | 滚动 30 秒内同设备识别到至少 2 个不同人员触发告警，重复事件不重复计数 | 是否所有正常连续进场也报警，还是仅尾随事件 |
| 带人超 3 次冻结卡 | 以确认的带人违规事件累计，第 4 次冻结；普通多人报警不自动等同带人违规 | 统计周期、冻结哪张卡、申诉与解冻权限 |
| 黑名单拦截 | 入场决策前校验有效黑名单，拒绝入场并记录原因 | 是否允许特定人工例外 |
| 夜间异常逗留 | 结合可靠进出记录、配置的夜间时段和最长时长产生告警 | 夜间时间段、超时阈值、接收人 |
| 欠费禁止入场 | 依据服务端欠费及门类型规则决策；按原文补充“A 门直接挡住” | B 门规则与原“ab 门除外”有歧义，暂不默认放行 |

告警接口：`GET W/risk-alerts`，请求 `P,storeId:ID,status?:open或resolved`，返回 `Page<{id:ID,type:string,deviceId:ID,memberId:ID?,occurredAt:DateTime,status:string,evidenceFileIds:ID[]}>`，仅授权管理者访问。

处置接口：`POST W/risk-alerts/{id}/resolutions`（幂等），请求 `result:confirmed或false_positive,note:string,expectedVersion:int`，返回更新后的告警。确认违规与冻结权益须记录来源事件，不提供任意修改统计次数的接口。

## 十、错误码与客户端行为

以下数值已在 gym 错误码源码中定义，可由新接口复用：

| code | 含义 | 客户端行为 |
| --- | --- | --- |
| `0` | 成功 | 处理 data |
| `400` | 框架参数校验失败 | 提示并修正输入 |
| `401` | 未登录/认证失效 | 按身份刷新令牌，失败后重新登录 |
| `403` | 操作权限不足 | 提示无权限并刷新能力菜单 |
| `1070001001` | 门店不存在 | 返回门店选择页 |
| `1070001002` | 门店范围无权限 | 停止请求该门店数据 |
| `1070005004` | 黑名单禁止入场 | 显示服务端提示和客服入口 |
| `1070007006` | 会员卡可用次数不足 | 引导查看权益 |
| `1070008010` | 私教可用课时不足 | 引导查看私教权益 |
| `1070009003` | 私教排期已被预约 | 刷新时段 |
| `1070009004` | 须先签到才能签课 | 提示先入场签到 |
| `1070009005` | 课程时间或签到日期不符合 | 显示可签课时段 |

以下为新增错误语义，暂不分配数值，实施时在 `ErrorCodeConstants` 注册且核对不冲突；不能将符号字符串放入当前整数 `code` 字段：

| 建议常量 | 含义 | 客户端行为 |
| --- | --- | --- |
| `MOBILE_IDEMPOTENCY_CONFLICT` | 同幂等键请求内容不同 | 停止自动重试并修正客户端 |
| `MOBILE_VERSION_CONFLICT` | 数据版本变化 | 刷新详情再操作 |
| `MOBILE_QR_TOKEN_EXPIRED` | 签课二维码过期 | 重新获取二维码 |
| `MOBILE_QUOTE_EXPIRED` | 报价失效 | 重新获取报价，不按旧价付款 |
| `MOBILE_CANCEL_DEADLINE_PASSED` | 超过预约取消期限 | 联系门店处理 |
| `MOBILE_ACTIVITY_FULL` | 活动满额 | 刷新活动信息 |
| `MOBILE_POINTS_INSUFFICIENT` | 积分不足 | 显示余额 |
| `MOBILE_CAPABILITY_UNAVAILABLE` | 支付/签约/设备渠道未开通 | 显示不可用原因，不展示成功 |
| `MOBILE_PHONE_IMMUTABLE` | 不允许修改手机号 | 保持原资料 |

网络超时：GET 可有限重试；有副作用请求复用原幂等键并优先查询订单/核销/设备等状态。刷新令牌采用单请求合并，防止多个并发请求同时刷新。参数错误、权限拒绝、名额不足不自动重试。

## 十一、与现有代码的落地对应及验收

### 11.1 复用关系

| 本文能力 | 当前源码/已有管理端基础 | 需新增或适配 |
| --- | --- | --- |
| 公共门店与首页 | `GymStoreController`，`/admin-api/gym/stores` | 公开字段裁剪、首页聚合、可靠实时人数 |
| 会员与负责关系 | `GymMemberController`，`/gym/members` 及成员负责关系接口 | 微信会员关联、本人读取、潜客跟进、移动分配 |
| 预约与签课 | `GymCourseController`，`/gym/bookings`、`/{id}/checkins`、`/{id}/lessons` | 会员端授权、动态二维码、设备自动签到入口 |
| 体测/训练档案 | `/admin-api/gym/body-measurements`、`/training-plans` | 会员本人查询、移动上传及字段裁剪 |
| 审批/合同 | `GymApprovalController`、`GymContractController` | 客户报价申请、真实签署会话和回调 |
| 工资/报表 | `GymPayrollController`、`GymReportController` | 本人已发布工资、移动工作台指标、统计口径适配 |
| 订单/包月 | `GymSalesRecordsController` 及 sales 服务 | 会员下单和支付会话，真实支付/代扣闭环 |
| 团购 | `GymGroupBuyController`、`GymMeituanRetailController` | 会员券资格、预览和确认流程，不直接透出后台接口 |
| 设备/器械 | `GymDeviceController`；71–72器械公开接口已实现 | 器械场景码及受控图文/视频教学；设备网关及命令真实回执仍暂缓 |
| 活动/加盟 | `GymMarketingController`、`AppGymFranchiseController` | 本人报名、公开展示；加盟提交沿用已有路径和字段 |
| 文件 | `GymFileController` | 会员归属模型、适配用途、受控下载 |
| 反馈 | 64–65、119–120已实现，详见实施进度 | 本人提交与历史、整店匿名查询/回复及受控附件 |
| 积分/私信/风控 | 尚无完整 gym 小程序入口 | 建模并新增接口，不宣称已有功能可直接使用 |

表中省略前缀的现有路径属于 `/admin-api`，它们不是本文 A/W 新接口。已有模块行为仍以实际源码和 OpenAPI 为准；不能只改路径便假定完成接口适配。

主要核对来源：

- [原始业务需求](./uniapp.md)
- [现有 gym API 进度与集成边界](../gym-full-api-progress.md)
- [门店与员工首批接口约定](../gym-first-batch-api.md)
- [gym Controller 源码](../../yudao-module-gym/src/main/java/cn/iocoder/yudao/module/gym/controller)
- [gym 错误码](../../yudao-module-gym/src/main/java/cn/iocoder/yudao/module/gym/enums/ErrorCodeConstants.java)
- [会员微信登录参数](../../yudao-module-member/src/main/java/cn/iocoder/yudao/module/member/controller/app/auth/vo/AppAuthWeixinMiniAppLoginReqVO.java)
- [服务模块装配](../../yudao-server/pom.xml)

### 11.2 建议开发顺序

1. P0：认证装配与会员关联、门店展示、本人卡和档案、教练与预约、员工负责范围、签课防重复。
2. P1：购卡和私教订单、支付与退款、合同签署、员工工作台、管理者审批和报表、工资查询。
3. P2：包月、团购、积分、活动、反馈、邀请、私信、监控、门禁风控及消息推送；实际顺序按渠道准备情况调整。

### 11.3 联调验收清单

- 四种角色分别验证列表、详情、附件、统计值与 total 的范围；修改 memberId/storeId/employeeId 不能越权。
- 手机号首次登录只建一份档案；重新登录不重复建潜客，有卡会员不降级；会员令牌不能调用工作台接口。
- 两人抢同一私教时段只成功一人；取消释放预占；未签到签课失败；刷脸和二维码并发只扣一次课。
- 支付回调重复、乱序、金额不符、客户端伪造成功均不能重复或错误发卡；支付成功发卡失败可恢复。
- 审批只能当前步骤指定账号处理；版本冲突不会覆盖旧数据；教练不通过审批不能生成优惠报价。
- 包月同一周期只扣一次和发一次权益；解约/扣款失败有真实状态；退款与积分回退可追溯。
- 活动不超卖、积分不扣成负数；匿名反馈及附件不泄露会员身份；员工无法读取他人工资。
- 设备回调重复不重复签到或报警，发送命令不冒充开门成功；未知状态不触发自动重复开门。
- 卡过期、换教练、换店后会员历史运动档案仍可查；员工旧权限及时失效。

## 十二、待确认事项与当前设计假设

| 事项 | 当前建议/已知结论 | 需要确认的具体问题 |
| --- | --- | --- |
| 实时人数 | 同时返回当前人数和当天到店次数 | 首页主展示哪一个；是否已有可靠出场数据 |
| 签课前置 | 已预约、同店当日有效签到、在允许课时内 | 原文“必须建立”是否还有额外建课要求；签课时间窗口 |
| 预约规则 | 容量 1，预占课时；自助取消默认早于开课、可配置提前分钟数；不自动扣课 | 门店最终取消提前量、提前预约期限、爽约是否扣课 |
| 匿名反馈 | 对门店匿名，平台受限关联；卡信息默认不附带 | 是否要求完全匿名，是否明确授权关联卡摘要 |
| AB 门欠费 | 已记录“A 门直接挡住” | B 门具体行为；与“ab 门除外”的冲突如何消解 |
| 工资表权限 | 原文已确认管理者和总部“一样权限” | 是否指相同查看操作；可见门店仍应按组织授权 |
| 微信连续包月 | 原文已确认是自动续费产品 | 周期价格、扣费日期、失败重试、解约生效及权益规则 |
| 管理者改价 | 默认使用可审计审批报价 | 是否允许拥有特定权限的管理者直接生成优惠报价 |
| 带人和冻结 | 已确认违规第 4 次触发冻结 | 统计周期、证据判定、冻结范围、解冻与申诉流程 |
| 夜间逗留 | 配置夜间时段与最长停留时间 | 具体时段、阈值和通知接收人 |
| 监控画面 | 仅授权会员访问限定摄像头 | 可见区域、可见时段、是否需脱敏 |
| 积分奖励 | 用账本记录邀请和消费，退款冲回 | 获取比例、邀请成功条件、兑换限制和有效期 |
| 业绩与平均值 | 本文给出净实收及平均公式 | 是否与现有后台一致，销售归属及退款跨期口径 |
| 员工审批 | 本人申请与指定待办分离 | 原文“审批功能/管理员审批”是否需要额外申请类型 |
| 签到排名 | 暂按每月有效签到天数 | 是否按次数、是否限制每天一次、同分规则 |
| 私信 | 先定义站内会话 | 是否需要接入微信客服及员工端发信能力 |

上述待确认项不影响公共列表和本人查询等基础接口设计；涉及扣费、扣课、冻结卡、门禁放行的规则应确认后再启用对应自动动作。

## 十三、已确认的暂缓范围（2026-10-23）

用户确认：“这个部分先跳过，文档写清楚”。支付与自动续费、电子签约、门禁设备、监控的第三方真实接入及外部联调暂缓；内部数据查询、业务建模和其他后端接口继续推进。完整边界、受影响接口编号及后续资料见 [实施进度与第三方边界](./uniapp-implementation.md#第三方接入暂缓范围)。

原 135 个接口仍保留在总表中，暂缓不等于已实现，不把未开放的渠道功能返回为成功。支付会话、自动扣费、签署完成、门禁放行及监控播放均不得用模拟结果对外宣称接入成功；未实现入口可能尚无路由，以总表当前状态为准。前端不修改。

## 反馈已实现补充（2026-10-25）

总表64–65、119–120已实现。提交仅接受storeId、type、content、rating、fileIds和allowCardContext；会员身份从令牌取得。必须是营业门店，且具有当前会员门店关系或本人已发生的成功签到。历史查询不要求门店仍营业；门店读取和回复仍要求当前整店数据范围，SELF负责关系不授予此能力。

提交、回复均须Idempotency-Key；内容去除两端空白，1～2000字符，review必须有1～5整数评分，feedback可不评分。同键相同参数重放首次快照，同键不同参数冲突；会员收到回复后重试原提交键仍返回首次received，当前状态通过本人列表读取。反馈、附件关联和请求结果同事务；并发重复提交只写一次。一条反馈只允许首次回复，新的键携带相同正文返回原回复，不同正文拒绝覆盖；回复与请求结果同事务。查询按提交时间/ID倒序，筛选在分页前，pageSize最大200。

门店列表不返回memberId、手机号、姓名、卡号、上传人或审计人。allowCardContext默认false；明确true时保存提交时的两个数量activeCardCount、activeCoursePackCount，门店响应cardContext为该快照，否则为null。只统计同店可用范围、已开始未到期且active的卡和课包，次数卡/课包剩余次数须大于0；数量不扣预占次数，也不代表当前预约资格。摘要不包含ID、名称、金额或有效期；后续卡变化不改写该快照。

附件最多9张，不可重复，仅限本人在同店上传、purpose=feedback/category=member_feedback的JPEG/PNG，每个文件仅可绑定一条反馈。提交与读取均解码验证：输入/输出不超过10 MiB，每边不超过8192像素且总像素不超过2000万。门店经辅助接口GET /admin-api/gym/mobile/feedbacks/{id}/attachments/{fileId}/content读取，要求gym:feedbacks:read和反馈门店整店范围，同时检查实际附件关联；该辅助接口不增加135项目标数。图片重新编码移除嵌入元数据，固定名称feedback-image.png或feedback-image.jpg，设置private,no-store及nosniff，JPEG可能重新压缩。会员自己的既有文件下载仍可读取原图；员工不能通过通用文件接口绕过反馈权限。

匿名指服务端不向门店暴露身份字段，平台数据库仍有受限归属关联。用户主动写入正文、回复或图片画面中的姓名等信息不会自动遮盖，提交/回复访问日志禁记请求与响应正文。没有平台完全匿名、文本审核、编辑/撤回、回复修改或自动通知功能。

V014新增gym_feedback、gym_feedback_attachment及gym:feedbacks:read、gym:feedbacks:reply两个权限定义；不会自动授权角色。部署前迁移并validate，再按职责授权。67张gym表；本任务只运行隔离迁移测试，业务数据库未迁移，前端未改动。

### 活动报名幂等摘要兼容（2026-10-25）

活动报名的新请求使用固定字段顺序计算摘要，同时兼容旧版activityId/memberId两种字段排列的摘要。兼容候选只由服务端当前活动ID与已验证会员ID生成，不接受客户端提交摘要，也不放宽身份、租户或参数匹配。已有请求的摘要与首次响应保持不变：取消后重放旧键仍返回首次报名结果，不会再次报名或占名额。各实例应部署修复版本；回退旧版仍可能遇到旧Map无固定迭代顺序引起的误冲突。其他接口的摘要算法和历史记录未改写。

## 器械扫码与教学实现约定（2026-10-25）

总表71–72已实现，当前租户内游客可访问，但器械必须明确发布。`POST /app-api/gym/qr-resolutions`仅接受`{scene:string}`，scene严格为32位小写十六进制；不接受任意URL、设备ID、门店ID或客户端指定身份。按服务端保存的场景码返回`{type:"equipment",equipmentId,storeId}`。有效租户仍必需，不根据scene自动切换租户；跨租户、未知、已撤销及停用对象返回不可用。

`GET /app-api/gym/equipment/{id}/tutorials`返回`{equipmentId,name,tutorials:[{id,title,content,image:Media?,video:Media?}]}`。为支持原需求的图文教学补充image字段；每项为一份图片或视频，content来自原instructions纯文本。仅展示active教程，按sortOrder升序、ID降序；材料必须是同店普通image（JPEG/PNG/GIF/WebP）或video（MP4），不得有工作用途绑定。失效/删除/跨店/私有文件对应教程不显示；没有有效教程时返回空数组。没有器械列表公开接口，不公开内部编码、型号、上传人、审计字段或存储URL。

先由有`gym:equipment:write`及整店数据范围的有效后台账号调用辅助`POST /admin-api/gym/equipment/{id}/public-scenes`，无body，返回`{equipmentId,scene}`；此操作明确公开该器械的当前和后续active教程。首次生成随机场景码，重复/并发发布返回同一码。`DELETE`同路径撤销，重复撤销返回true；再次发布生成新码，旧码失效。两条后台辅助路由不计入135目标数，无新增权限或自动角色授权。SELF负责关系不授予发布权。

V015增加`gym_equipment.public_scene`可空列及租户内唯一索引，历史器械一律NULL，不自动公开。器械和门店均须active才可公开读取；暂时停用不会删除场景码，重新启用会恢复访问。要永久废止旧码使用上述DELETE；撤销在门店停用时也可操作。发布/撤销和原器械编辑共用器械行锁，失败不保留场景码。

图片/视频使用既有`GET /app-api/gym/public-media/{id}/content?tenantId=...`，每次重查有效公开引用、归属和用途，检测实际内容魔数，支持既有字节范围响应及no-store。最后一个公开引用消失后不可读取；同一文件仍被其他有效公开内容引用时仍可访问。素材不做反馈图片那样的元数据清除，应由管理员上传适合公开的教程；保留原文件字节，不执行转码。一次返回该器械全部有效教程，关联校验为逐项查询，大量教程和大视频内存/吞吐尚未压测。

这里生成的是本地可解析scene值，不生成微信官方小程序码图片、不调用门禁或监控设备。将scene渲染/打印成二维码以及小程序相机扫码页面仍属调用方工作，本任务未修改前端；未声称完成真实手机扫码或视频播放联调。部署前先迁移V015并validate，仍为67张gym表，业务数据库未迁移。

## 销售报表实施口径（2026-10-25）

总表105 `GET /admin-api/gym/mobile/reports/sales`已实现。要求有效后台身份、`gym:reports:read`功能权限和整店数据范围；不传storeId汇总当前授权且未删除的门店，包含已停用门店的历史流水。显式请求范围外门店拒绝；只有SELF而无整店范围时不传storeId返回空列表/零汇总，不能借本人任职查看整店销售。employeeId只在授权门店内进一步筛选，不扩大范围。106员工业绩已在后续批次实现（见下）；107会员汇总已在后续批次实现（见下）；77概览已在后续批次实现（见下）。

startDate、endDate必填，按Asia/Shanghai自然日`[startDate,endDate)`，支持1000～9999年、1～366个自然日；pageNo>=1、pageSize<=200。商品筛选card/course，其中course对应原财务流水private；未知筛选值拒绝。

金额以同租户、授权门店、未删除的gym_financial_event确认流水为准，payment加、refund减，按各自occurredAt落入区间；与现有经营报表的财务事件口径一致，不以当前订单状态或卡发放次数替代实收。部分收款计入已确认实收；完全未付款订单和免费发卡不计入。退款即使原付款在区间外也计入本期，净额可为负。支付渠道实际接入仍暂缓，本接口只读取已有可信落库数据，不发起支付/退款或伪造成功。

列表按`订单+门店+归属员工（可空）+商品类型`聚合，因此多人归属订单可能显示多行，各行金额为账本已有归属份额；不在查询时重新分摊，不给每个员工重复整笔订单金额。id为该组在查询区间内最早的财务事件ID，不是业务订单详情ID，改变时间筛选后可能变化。按本组区间内最后事件时间倒序、id倒序分页；total是分组行数。

summary对完整筛选集计算，和页码无关。paidOrderCount为区间内有payment的业务订单去重数，部分收款也算一个；cardOrderCount/courseOrderCount分别按商品类型去重，多人份额不重复增加同类订单数。cardAmount/courseAmount均为净实收；各平均客单=该类净额/该类payment订单数，分母为0返回"0.00"。金额均为两位小数字符串，四舍五入；没有其他员工数量或会员身份字段。

paidAt为同订单/门店/商品/员工组历史最早确认收款时间，可能早于查询区间；没有可核实付款时为null。订单元信息只连接同租户同门店未删除订单；订单缺失/删除或归属不一致时，保留财务金额，orderNo和productName为null，不编造编号或借其他门店资料。商品名称来自对应类型的订单项快照，去重后以` / `连接；多商品只影响名称展示，不参与金额JOIN放大。

SQL在聚合和分页前应用权限/日期/筛选，金额、行数、分页及名称读取使用同一只读REPEATABLE_READ事务。V016仅增加门店日期与订单原收款查询索引，不回填、不改金额或归属，无新权限，仍67张gym表。部署前migrate/validate；业务数据库未迁移。实际SQL聚合和边界已在H2验证，MySQL验证仅迁移与历史数据保留，生产EXPLAIN/海量流水性能及部署HTTP尚未验证。

## 员工与门店业绩实现约定（2026-10-25）

总表106 `GET /admin-api/gym/mobile/reports/performance`已实现。必传startDate/endDate及groupBy=employee或store；上海日期左闭右开，1000～9999年、1～366自然日，pageSize<=200。要求当前有效后台账号和gym:reports:read。

整店范围优先：未指定门店时汇总授权且未删除的门店，停用门店保留历史；指定授权门店时只统计该店。没有整店范围，或明确选择整店范围外门店时，必须具有SELF权限和唯一有效在职员工身份，才能转为本人模式；本人只能groupBy=employee，门店还必须是当前已生效任职的营业门店。混合SELF/整店权限不传门店时只汇总整店范围，不偷偷扩入本人其他门店。客户端不能指定employeeId选择统计主体，无合法范围拒绝。

财务口径沿用105：区间内确认payment减refund，直接使用持久化归属金额，不把每个员工的订单重复全额相加。paidOrderCount为各行范围内payment订单去重数，多员工各自订单数不能直接相加成总订单数。lessonCount独立计算confirmed且未删除的课消记录条数，按confirmedAt筛选，不使用预约数，也不把sessionUnits课时数冒充记录数。

employee分组将同员工在所选门店的业绩合并为一行；范围只有一个门店时返回该storeId，多店时storeId=null。store分组每个授权门店一行，employeeId=null。行按净额倒序、主体ID升序，采用竞争排名（同额1、1、3）；未归属收入单独排末尾，employeeId及rank为null，不冒充员工。SELF的rank始终null，不查询其他员工来生成排名。分页在已授权聚合结果上进行，summary和排名都基于完整筛选范围。

零业绩对象包括所选门店，以及当前active、已入职且当前任职已生效、入职/任职日期早于查询结束日的员工；同员工跨店只计一次。期间有财务或课消归属的历史员工也保留，即使当前任职已删除或档案已失效，找不到档案时名称为“历史员工”。**现有人员模型没有完整任职结束日期/历史状态，不能精确重建过去某段时间全部零业绩在职人数**；当前口径为“可核实的当前有效任职+期间实际事件人员”，不能将其当作完整历史编制统计。这是数据来源限制，尚需后续任职历史建模才能消除。

summary.netAmount含未归属收入；employeeCount只计算具名员工ID，不含未归属行，storeCount为实际筛选门店数。averagePerEmployeeAmount=已归属员工净额/employeeCount，averagePerStoreAmount=总净额/storeCount，averagePerDayAmount=对应行净额/查询自然日数；分母为0返回"0.00"，金额两位小数四舍五入。本人模式所有金额、课消及人数只来自本人和本人当前任职范围，不返回其他员工数量或门店总收入。

所有聚合在SQL中先限定租户/门店/本人/时间，读取共享只读REPEATABLE_READ事务；服务只对聚合主体和可核实人员排序分页，不加载完整交易流水。复用V016财务索引，无新DDL、权限或自动授权。历史人事口径限制、海量主体排序、生产MySQL聚合性能及部署HTTP尚未完成验证；前端未改动。

## 会员汇总已实现补充（2026-10-25）

总表107 `GET /admin-api/gym/mobile/reports/member-summary`已实现。请求为可选正整数storeId和必填date（Asia/Shanghai自然日，1000～9998年）；要求有效后台身份、gym:reports:read和整店数据范围。不指定storeId时汇总当前授权且未删除的门店，包含已停用门店；指定范围外门店拒绝。只有SELF范围不能读取整店人数，不指定门店返回零汇总；没有数据或授权范围内不存在的门店返回零值。

**这是当前会员与权益汇总，不是指定历史日期的总量快照。** date仅筛选newMemberCount的注册日期，响应回显date并增加updatedAt（含时区偏移的实际统计时点）。其他四项指标按updatedAt计算。现有档案、门店关系和权益状态没有完整历史版本，无法精确还原某个过去日期的人口/权益总量；当时注册但现已停用、删除或不在可见范围的会员，也不会出现在newMemberCount中。未来date返回新增0，但其他指标仍是当前值。

| 字段 | 统计口径 |
| --- | --- |
| memberCount | 当前active、未删除、registeredAt不晚于统计时点且在至少一个所选门店有已生效未删除关系的会员档案人数；含潜客，不等于已付费人数 |
| privateCourseMemberCount | 上述人群中，在所选门店拥有active、已开始、未到期且remainingSessions>0私教包的人数；私教包门店必须同时存在该会员当前有效关系 |
| newMemberCount | 上述当前人群中，初次registeredAt位于date当天上海时间左闭右开区间的人数；新增门店关系不当作新会员 |
| expiringCardCount | 上述人群中，至少一张active、已开始、有效至未来30×24小时内的卡的持有人数；次卡还要求remainingUses>0，卡适用门店与会员关系必须在同一所选门店相交 |
| expiringCourseCount | 上述私教会员中，至少一个有效私教包在未来30×24小时内到期的人数 |

临期窗口为 `updatedAt < expiresAt <= updatedAt + 30×24小时`，精确到微秒；恰好已到期不计，恰好30天计入。无到期时间的权益不算临期，未开始/停用/删除/耗尽的权益不计。预占余额不从有效权益余额中再次扣减：这些人数用于统计持有权益，不表示此刻可再预约的人数。

全部人数在所选门店联合范围按会员ID去重；同人多张卡、多个私教包或跨店仍只计一次，因此各店人数相加可能大于联合人数。五项都是人数，临期字段不是卡数/包数；彼此有交集，不能求和作为总人数。响应仅返回五个整数、date、updatedAt，不含会员ID、手机号、姓名或卡号。

查询以当前租户、门店、会员关系和权益状态在SQL中限定，EXISTS避免多对多联接放大；门店列表与单次统计共享只读REPEATABLE_READ事务。无新DDL、权限定义或自动授权；既有会员/开卡/私教业务写流程未变。历史快照、生产规模查询计划及部署HTTP联调未完成，前端未改动。

## 工作台首页概览已实现补充（2026-10-25）

总表77 `GET /admin-api/gym/mobile/dashboard`已实现。storeId可选正整数，date可选上海自然日、默认今天；不允许未来日期，最早1000-01-02、最晚9998-12-31。服务端验证有效后台账号，不接受role、employeeId、scope参数切换身份或统计主体。

整店范围优先：不传storeId时只汇总授权且未删除的门店，含停用门店的历史事件；指定授权门店只看该店。没有整店范围，或明确选择整店范围外门店时，必须有SELF权限、唯一当前有效员工身份及已生效任职，才能进入本人模式；本人范围仅当前营业门店。混合SELF/整店权限默认不合并两者，只有显式选择本人其他任职店时转为self。无合法范围拒绝，整店范围内无数据返回授权指标的零值。

scope.type为store或self，storeIds为实际筛选门店ID字符串（升序）；store模式employeeId=null，self为当前员工ID。每项功能独立授权，未授权时整个指标对象或pendingApprovalCount明确返回null；不会先算全店总数再让前端隐藏。未授权与有权限但结果为0不同。

| 指标 | 所需功能权限 | 数据与口径 |
| --- | --- | --- |
| performance | gym:reports:read | 已确认会员卡/私教财务事件的payment减refund；self只取本人归属份额，整店含未归属金额，金额两位小数字符串，可为负 |
| checkins | gym:checkins:read | succeeded签到记录条数，不是签到人数；self仅同店当前负责会员的签到 |
| lessons | gym:lessons:read | confirmed课消记录条数，不是sessionUnits课时数；self同时要求本人授课及同店当前负责关系，与工作台课消查询的访问边界一致 |
| members | gym:members:read | 当前active、未删除、注册及门店关系已生效的去重档案人数，含潜客；self还要求同店当前负责关系 |
| pendingApprovalCount | gym:approvals:read和gym:approvals:decide | 仅store模式；当前账号是审批当前pending步骤的指定审批人，员工绑定仍有效，审批在所选授权门店内，按审批单去重；不受旧首页最多10条列表限制 |

日期字段含义：today为请求date当天，yesterday为其前一天，month为date所在月第一天至date当天结束。全部自然日边界使用Asia/Shanghai，左闭右开；业务事件还要求发生时间不晚于updatedAt，避免未来记录计入已发生业绩。日期落在月初时，昨日统计可以跨月，但不进入本月累计。updatedAt为实际统计时点。

members.newTodayCount是当前可见有效人群中初次registeredAt在date当天的人数；新增门店关系不算新会员。store模式只返回storeTotalCount，responsibleCount=null；self只返回responsibleCount，storeTotalCount=null。多店同人去重，同一人多次签到/多次课消则按记录计数。成员总量、负责关系及待办状态始终是当前值，不随历史date还原；当前已失效会员不作为历史新增；当前负责关系撤销后，本人的历史签到/课消也不再暴露。历史人群和负责关系的完整快照尚未建模。

待办只计当前可处理的步骤：后续waiting步骤、他人步骤、已完成步骤、失效员工绑定或未授权门店不计；SELF无整店审批处理范围，pendingApprovalCount=null。日期不改变当前待办数，也不表示历史当天待办。

全部源表在SQL聚合前限定当前租户、门店、当前身份/负责关系和日期；无权限的指标不调用对应聚合查询。各查询共享只读REPEATABLE_READ事务，响应只含指标和范围，不含会员档案或审批正文。无需新DDL、权限定义或授权，不改原后台首页、财务、签到、签课及审批写入流程。生产规模查询计划、完整历史快照及部署HTTP联调未验证；前端未改动。

## 移动审批读取、模板与决策已实现补充（2026-10-25）

总表112–115已实现：审批列表、详情、可用模板和决策。104发卡/私教卡申请已在后续批次实现（见下）；116专属改价申请及117–118报价生成/读取已在后续批次实现（见下），订单结算仍待衔接。

全部入口要求当前有效后台账号，租户与登录上下文一致。列表/详情要求gym:approvals:read；模板选项要求gym:approvals:create；决策同时要求gym:approvals:read和gym:approvals:decide。审批对象内的applicantId及steps.approverId均为后台账号ID，不是员工ID；步骤保留提交时指定账号，当前员工绑定用于再次鉴权。

| 查询 | 返回范围 |
| --- | --- |
| view=mine | 当前账号发起的审批，保留本人历史；不因没有整店范围而向其他申请人开放 |
| view=pending | 当前账号是当前pending步骤的指定审批人、员工仍active且绑定该账号、具备decide权限，并且审批门店属于当前整店范围 |
| view=processed | 当前账号已approve/reject的步骤所在审批；仍要求该步骤员工与账号的当前有效绑定，后续步骤可以尚未结束 |
| 详情 | 当前申请人，或当前有效员工绑定的任一步骤指定审批人（包括waiting），或拥有该门店整店数据范围的管理者；其他对象与不存在统一返回审批不存在 |

列表view必填，仅mine/pending/processed；status可选pending/approved/rejected，筛选的是整单状态，而非本人步骤状态。storeId仅作进一步过滤，不能扩大上述可见范围，不匹配返回空页。分页1～200条，按submittedAt/ID倒序，范围和筛选在数据库分页前执行。步骤在页后批量读取；没有“管理者自动查询全租户所有审批”的all视图。

业务摘要按类型白名单输出：发卡保留会员ID/姓名、卡模板ID/名称/类型、金额或订单来源；旧改价保留商品来源、名称、原价和申请价；合同保留合同来源、编号/名称和会员姓名；其他配置只保留原因。金额统一两位小数字符串，ID为字符串。不返回手机号、内部请求摘要、完整模板、附件地址/ID、业务结果中的卡号或任意扩展指令。当前旧price_change审批仍表示修改商品公开价格，不能把它当作116–118的单客户专属报价。

approval-options要求storeId、businessType；业务类型为card_opening/price_change/contract/other_configured。只列published且所有步骤当前可执行的模板：员工/账号有效、账号不重复、具备该门店整店范围及审批权限；不返回审批人身份或模板内部配置。templates按请求类型筛选，businessTypes为当前可用模板实际覆盖的类型。SELF仅能在当前负责会员所在门店选择price_change模板，还须唯一有效员工身份；不授予整店发卡或合同申请权限。停用门店不能获取申请选项。结果只是当前可用性，正式提交时仍需再次检查模板和业务条件。

决策须传Idempotency-Key（8～128位字母、数字、下划线或连字符）及expectedVersion正整数，只接受decision、comment、expectedVersion；额外业务字段拒绝。comment最长2000字符，去除两端空白，reject必须有非空原因。服务端在事务内锁定既有门店权益互斥行和审批行，再核对指定当前步骤、当前账号绑定、整店范围、版本及业务快照；过期版本返回`1_070_010_012`，须刷新后再决定。

V017为gym_approval增加version，初始及历史审批的迁移基线均为1；每次成功推进或拒绝加1，并不是按已有历史步骤数倒推。原后台决策也更新同一版本，原已完成步骤的合法重试不递增。最后一步继续调用既有业务链：卡发放、商品公开改价、内部合同审批等与步骤、整单状态、版本、幂等响应同事务；任一业务失败全部回滚，同键可以修正外部业务条件后重试。合同审批不等于第三方签署完成。

同账号同键同规范化请求返回首次响应快照；例如第一步成功后其他人已结束后续步骤，再重放第一步旧键仍得到当时的pending/version2，最新状态须GET读取。同键改审批ID、决策、原因或expectedVersion冲突。不同键不能以旧版本重新处理已完成步骤；并发相反决策只允许一项生效。重放前仍检查当前功能权限、后台身份、整店范围和指定员工绑定，撤权/离职/解绑不能继续读取旧幂等结果。

部署先执行V017 migrate/validate，再统一更新所有处理审批的服务实例，完成后启用移动决策；旧程序不会更新版本，混跑不能保证版本语义。历史审批状态、步骤和快照不改写，不新增权限或自动授权，仍67张gym表。原后台请求格式不变，移动端的必填拒绝原因及版本约束不反向改变旧请求契约。未做生产滚动发布、数据库规模压测、部署HTTP或真实第三方联调；前端与业务库未改动。

## 会员卡与私教课包发放申请已实现补充（2026-10-25）

总表104 `POST /admin-api/gym/mobile/card-issue-applications`已实现。要求当前有效后台账号、gym:approvals:create和独立的gym:cards:issue功能权限，并有申请门店的整店数据范围；SELF负责会员不能据此直接申请整店发卡。V018只登记新权限定义，不授予任何角色，部署后由管理员明确授权。

请求仅包含storeId、memberId、productType（card/course）、productId、reason（1～2000字符、去除两端空白）、approvalTemplateId；ID均为正整数，Idempotency-Key必传并沿用8～128位规则。会员必须在指定门店有当前关系，提交时状态有效、可购买，门店营业。拒绝自定义金额、课时、有效期、付款状态、审批结果或其他额外字段。

| 产品 | 请求ID及审批快照 | 最后一步通过后的权益 |
| --- | --- | --- |
| card | productId为具体已发布卡模板ID；保存productType/productId/productVersion及原会员/卡名称/金额摘要；该卡须适用于申请店 | 复用原会员卡发放，使用选中的不可变模板版本，后续另建模板版本不会替换本申请；最终时模板仍须可用，会员状态/禁购与卡规则重新校验 |
| course | productId为同店active私教商品ID；冻结版本、教练、名称、课时数、每课时分钟数、价格和会员；教练须当前有效且在该店任职 | 最终时重新校验会员门店关系/状态/禁购、营业店、当前教练及商品；版本或冻结条款变化返回`1_070_010_013`，不按新条款悄悄发放，须重新申请 |

两类都使用已发布的card_opening审批模板，审批人、步骤和账号权限沿用真实后台流程。创建只返回pending/version1的Approval，不生成卡、课包、合同、订单或实收流水；不以“提交成功”代替审批通过。移动112–115或原后台均能处理这些审批，最后一步才调用真实权益发放服务。新增course-products开通来源也可由既有后台审批创建服务使用，仍遵循其原审批创建权限；独立gym:cards:issue要求针对本次移动申请入口，不改旧后台授权策略。

私教商品当前没有有效期字段，因此此入口发放的课包从最终审批时开始，expiresAt=null（无固定到期日），总课时和余额取冻结商品。不能凭空约定30天、1年等期限，也不能从客户端绕过申请传入期限。若需限期私教产品，后续须先补产品有效期建模及冻结规则。

发放后按既有规则选择该权益类型当前最新已发布合同模板，若已配置则生成独立内部未签合同；未配置模板不会凭空创建合同。这不是第三方签约完成。生成合同失败会连同权益、会员状态变更、审批步骤/版本及决策幂等结果整体回滚。审批授予权益不证明已支付：本流程不写订单或财务事件，不能计为真实销售、扣款或收入。

同账号同键同规范化请求只建一条审批，并返回首次响应；最终审批完成后用原申请键重试仍返回当时pending，当前状态通过审批GET读取。同键改变产品、会员、门店、原因或模板冲突；不同键代表不同申请，不按会员/产品跨申请合并。重放前重新校验后台身份、功能权限、整店范围和会员门店关系；失败提交与幂等结果一起回滚，可在修正业务条件后使用同键重试。

私教快照与最终发放共用教练→会员→商品锁顺序，与商品编辑和预约一致；审批还持有原权益互斥和审批行锁。商品编辑与批准竞争时，要么按原冻结条款发放，要么商品先改变而批准失败，不产生混合条款。权益来源固定为approval/审批ID，重复决策不重复发放。摘要新增产品类型、版本及课包字段，仍不返回手机号、卡号、附件地址或完整业务结果。

部署要求V017版本字段及V018权限定义，更新所有审批处理节点后再授权并启用此入口；旧程序不能处理新增私教开通来源。仍67表，前端和业务库未修改，未执行真实支付、外部签约或生产多节点联调。116–118专属改价/报价已在后续批次实现，详见下文。


## 私教专属改价申请已实现补充（总表116）

`POST /admin-api/gym/mobile/course-price-applications`要求有效后台账号、`gym:approvals:create`和当前商品门店范围；SELF仅自己的教练商品，且必须存在当前有效负责关系。传`memberId`时须为同店会员，SELF还须实际负责该会员；总部权限也不能绕过真实会员门店关系。复用已发布的`price_change`模板及其审批人有效性检查，无新增权限定义或自动授权。

请求仅接受`storeId,productId,memberId?,proposedAmount,reason,approvalTemplateId`，必填`Idempotency-Key`（8～128位字母、数字、下划线或连字符）。ID为正数；金额非负、整数最多10位、小数最多2位，须与当前公开价不同，支持降价或加价申请；原因去首尾空白且1～2000字符。禁止自填状态、内部快照、来源或审批结果。原价报价由117生成，无需提交同价改价申请。

服务端保存`sourceType=course-quote-products,priceScope=quote,productId,productVersion,memberId?,courseName,coachId,sessionCount,durationMinutes,originalPrice,requestedPrice,reason`。**专属快照故意不保存旧公开改价使用的`sourceId`**：旧审批程序缺少该字段会在改价前失败回滚，避免把优惠覆盖到公开商品；仍须统一升级审批服务再启用入口。旧管理端`sourceType=course-products`保留公开价修改语义。

最终批准重新检查营业门店、有效教练及任职、有效商品、会员状态/同店关系和购买黑名单，并比较原商品版本及条款。任一变化不自动采用新条款，审批步骤、版本和决策幂等记录整体回滚，需按当前商品重新申请。条件性阻断解除且快照未变时，可用原失败决策键重试。批准只记录专属报价授权；不改公开价格或商品版本，不产生报价、订单、课包、合同、收入或第三方调用。拒绝同样不改商品。

响应为既有`Approval`，`businessSummary`用`priceScope=quote`区分，ID及金额按字符串输出，不含手机号或任意内部字段。申请幂等规范化金额为两位小数、原因去首尾空白；同键异内容冲突，同键重放首次响应（后来已批准仍重放首次pending），当前状态通过审批详情查询。每次重放前重新校验当前商品及会员访问范围，撤销负责关系/门店权限后不能读取缓存。

116只产生批准结果；117–118已在后续报价批次实现，20–26订单结算绑定仍未完成。报价生成核对批准状态、专属来源、商品版本和适用会员，后续结算还须再次验证；真实支付继续按约定暂缓。没有修改前端。


## 私教报价生成和会员读取已实现补充（总表117–118）

### 创建与金额来源

`POST /admin-api/gym/mobile/course-quotes`只接受`storeId,productId,memberId?,approvalId?`，ID为正数，必填`Idempotency-Key`。要求有效后台账号、V019新增的`gym:courses:quote`权限及当前商品/会员数据范围；不自动授予角色。管理者具有整店范围时可不带approvalId按当前商品原价创建。SELF教练必须具有该店当前负责关系，只操作本人教练商品，并提供**自己申请且已批准**的专属改价；指定会员还必须由本人负责。

有approvalId时，服务端校验approved、price_change、course-quote-products和priceScope=quote，并精确匹配门店、商品及适用会员（未限定会员的申请不能悄悄改成某会员，反之亦然）。旧公开价改价审批、其他申请人的SELF审批、待批或拒绝申请均不能使用。按批准快照提取金额，重新验证当前商品版本/条款、营业门店、教练任职及可选会员资格；客户端不能传金额、状态、有效期或快照。商品有变化则要求重新申请，不使用新价格替换原批准价。

### 持久化、有效期和重试

V019新增`gym_course_quote`，保存租户、门店、商品与版本、可选会员/审批、签发人、金额和条款快照；签发时间保留微秒，**到期时间固定为签发后24小时**。不更改公开商品、订单、课包、合同或财务流水。

相同幂等键同内容返回第一次响应，异内容冲突；重放前仍检查当前账号、商品/会员范围及SELF审批归属。一个批准申请在租户内只对应一份报价，**换键或换有权限的管理者也不续期**；已生成时返回同一个报价的当前状态和原到期时间。无审批的原价报价可按新的请求键另行生成。服务事务、租户业务互斥和数据库唯一索引共同保护并发，失败时报价和请求记录一起回滚。

### 会员读取和状态

`GET /app-api/gym/course-quotes/{id}`要求有效会员令牌及已关联的有效健身会员档案，服务端不接受请求中的memberId作为身份。指定会员的报价只允许该会员读取；未指定会员的报价要求当前同店会员关系。跨租户、其他指定会员或无同店关系统一不可读，购买黑名单同样拒绝。

只返回`id,storeId,productId,memberId?,amount,expiresAt,status,sharePath`，ID及Money为字符串，不含审批ID/意见、内部条款、签发账号或会员手机号。到达expiresAt（含等于）、商品删除/停用/版本变化、门店停用、教练停用或任职未生效时返回expired，保留原金额和到期时间。状态按读取时条件计算：因临时营业/教练条件失效，在未到期且版本未变时恢复条件可再次active。active只描述报价当前条款有效，不表示已有可用支付渠道。

`used`表示未来订单事务已经绑定used_order_id，不表示已付款。V019预留租户内订单唯一关联，**本步两个接口均不消费报价；订单创建/绑定和used状态流转尚未接入，也未用模拟订单制造成功**。后续订单事务必须核对会员、门店、商品版本、金额、有效期及真实订单归属，并原子绑定一次。

### 分享路径与接入边界

`sharePath`使用配置`yudao.gym.mobile.quotes.share-page`（默认预留`/pages/course-quote/detail`），只允许`/pages/`下由字母、数字、下划线、连字符及分段斜线组成的路径；追加`?quoteId=<id>`，不携带金额或审批信息。该默认路径是**前端对接约定，未验证前端已有该页面**，部署时须指向实际注册页面。本任务没有修改前端，也不声称已验证小程序分享打开、下单、支付或真实设备流程。

部署先migrate/validate至V019，再更新全部审批及报价服务实例并按需授予gym:courses:quote；目前68张gym表，无历史报价回填或角色授权。20–26订单结算仍待完成，第三方真实支付等继续暂缓。
