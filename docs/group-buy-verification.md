# 健身团购验券与美团服务零售接入

## 当前交付

健身业务使用美团 **服务零售 → 团购核销 API**。本轮已按该目录全部16项实现 Controller → Service → 平台 Client → 共享表单 POST 传输，包括验券准备、核销、撤销、历史/商品/手机号查询、次卡、退款相关请求。逐项官方来源、路径、参数、类型和文档差异见 [完整 API 清单](meituan-service-retail-api.md)。

后台16项交付范围是请求框架：不改前端，不要求真实跑通，不写核销结果、发卡、退款财务或其他业务表；真实商户授权、平台联调、回调、状态补查调度和本地履约未接入。默认关闭接口及状态变更请求，未配置时明确报错，不返回模拟成功。

先前已提交的 `/admin-api/gym/group-buy/meituan/coupon-history/query` 是餐饮历史接口，现已在 OpenAPI 标记 deprecated，保留原结构用于兼容，且仅接受明确配置的 businessId=1。新服务零售路径位于 `/admin-api/gym/group-buy/meituan/retail`，业务编号固定58。服务端核对授权业务编号，混用时拒绝；不能只换编号调用餐饮路径。

## 本地 POST 请求和响应

调用沿用 Bearer 登录、tenant-id 和 CommonResult。有平台业务参数时：

```json
{
  "storeId": "12",
  "biz": {
    "receiptCode": "0012345678901"
  }
}
```

上例用于 `POST /admin-api/gym/group-buy/meituan/retail/receipts/prepare`。核销接口使用相同外层结构，biz 中明确提供 receiptCode、count、requestId；次卡、退款等按各自 DTO 提交。查询标品接口的本地请求仅 `{ "storeId": "12" }`。

成功响应为 `CommonResult<MeituanResponse<T>>`：外层 code=0 表示本地成功取得平台接口响应，内层包含平台 code、msg、traceId、data。批量验券须逐条查看 singleConsumeCode / singleConsumeMsg 和 consumeDetailList，不能据平台 OP_SUCCESS 就生成本地已核销或已退款记录。空列表只来自真实平台成功响应，不用作异常回退。

## 分层及参数规范

- Controller：16个明确的 POST 方法和类型化请求，校验店ID与嵌套 biz，配置功能权限；请求和响应均不记录访问日志，避免券码、手机号进入业务访问日志。
- Service：先调用 GymStoreService.requireStore 校验当前租户真实门店及员工门店范围；然后取 TenantContextHolder 的租户ID，填充可信操作人并调用对应 Client。SELF 负责会员关系不扩展为整店平台权限。
- Client：16个明确的类型化平台方法，只选择固定 MeituanApi 枚举地址，不允许 HTTP 调用方传入 URL、授权令牌、业务编号或任意参数 Map。
- DTO：平台业务参数及 MeituanCommonRequest 均用 getter/setter；biz 只序列化一次，公共 DTO 在签名和表单边界通过 toParameters 转换为排序 Map。MeituanRetailResponses 保存各接口及嵌套响应类型。
- Transport：共享表单编码、SHA1 签名、超时、业务编号核对和错误转换；旧餐饮 Client 也复用传输层，避免两套签名实现。

appShopAccount / appShopAccountName / operatorId，以及审核的 operatorName / operatorIp，只由服务端当前登录身份和请求上下文填充；忽略请求中的伪造值。operatorId 使用后台员工用户ID，昵称为空时回退ID。当前身份上下文无手机号，不上传调用方自填的 operatorMobile。平台将这些字段用于操作记录，本系统仍独立做权限校验。

## 平台 POST、签名和配置

平台固定域名：`https://api-open-cater.meituan.com`，服务零售具体路径见清单。Content-Type 为 `application/x-www-form-urlencoded;charset=utf-8`；公共字段包含 appAuthToken、businessId=58、charset=utf-8、developerId、秒级timestamp、version=2、biz（有业务参数时）、可选realOpBizCode、sign。

签名在URL编码前计算：排除sign和空值，将参数名自然排序，拼接key/value，前置signKey，以UTF-8计算SHA1小写十六进制。签名和发送使用同一份biz JSON原文。signKey只在服务端用于签名，不放入DTO或表单；凭据DTO不生成toString。连接超时默认3秒，读取超时10秒，不跟随重定向，所有请求均不自动重试。

基础配置位于 yudao-server/application.yaml；下面为外部配置示意，真实凭据不得提交：

```yaml
yudao:
  gym:
    group-buy:
      meituan:
        enabled: false
        mutations-enabled: false
        developer-id: ${GYM_MEITUAN_DEVELOPER_ID}
        sign-key: ${GYM_MEITUAN_SIGN_KEY}
        connect-timeout-millis: 3000
        read-timeout-millis: 10000
        tenants:
          1:
            stores:
              12:
                app-auth-token: ${GYM_MEITUAN_T1_S12_TOKEN}
                business-id: 58
                # real-op-biz-code: 多业务授权实体ID，按实际授权填写
```

enabled 控制平台调用；mutations-enabled 额外控制6项状态变更请求：验券、撤销验券、次卡批量验券、退款申请、退款审核、批量撤销验券。两项开关均默认false。查询及退款预校验也需要有效授权和各自功能权限。配置按租户/门店精确选择，不借用其他门店或租户授权。当前没有授权页面、动态刷新或热更新配置。

## 权限与迁移

普通验券/商品/历史查询使用既有 gym:sales:read；核销和撤销使用既有 gym:verifications:manage；四项退款接口使用独立 gym:group-buy:refund。均同时检查门店范围。

新增 `sql/gym/migration/mysql/V004__gym_group_buy_refund_permission.sql` 只注册退款按钮权限，不授予任何角色，不新增前端页面。按 [数据库操作手册](../script/gym/DATABASE.md) 在目标环境显式迁移并按职责授权后，普通角色才可调用退款入口。本轮未执行目标数据库迁移或修改角色授权。

## 错误与结果未知

| 本地业务码 | 含义 |
| --- | --- |
| 1070022001 | 接口关闭、开发者凭据缺失或当前租户门店授权未配置 |
| 1070022002 | 查询请求网络/HTTP失败 |
| 1070022003 | 查询被平台拒绝，或变更请求被明确的公共参数校验拒绝 |
| 1070022004 | 查询响应不是有效JSON或缺少必要结果 |
| 1070022005 | 门店授权业务编号与接口场景不匹配 |
| 1070022006 | 状态变更请求未启用 |
| 1070022007 | 变更请求的网络/格式异常或可能部分执行的业务错误，结果待确认 |

本地无效参数、未登录、功能权限拒绝和门店越权沿用框架及门店错误。平台原始异常和失败msg不透传，避免泄漏凭据或券码。

核销和退款可能已在平台完成，但响应未收到。遇到1070022007，先在平台或对应查询接口核实结果，不能直接再次核销/退款。consume、batchconsume 的 requestId，以及退款审核 sequenceId，沿用同一业务请求值；本系统不随机生成它们。退款申请官方sequenceId为可选流水号，本系统为追溯要求显式提供，但不宣称该字段保证平台幂等。撤销接口没有文档外的幂等字段，不自行添加。未知退款申请若拿不到refundId，需要平台侧人工确认，本框架未实现自动补偿。

## 验证和后续

本轮逐项本地契约、MVC、权限及异常回归见 [testing.md](testing.md)。还需在正确的服务零售行业权限、门店授权和平台测试条件下完成真实联调，再设计本地核销流水、凭证留存、结果补查、权益映射与幂等发放、退款审批衔接和对账。本次没有把上述后续能力当作请求框架已经实现。

官方共同协议：[公共请求参数](https://developer.meituan.com/docs/biz/biz-comm-sys-param-req)、[签名规则](https://developer.meituan.com/docs/biz/comm-dev-isv-sign-rule)。


## 2026-10-26 会员预览增量（V033）

移动总表32新增`POST /app-api/gym/coupon-verification-previews`，复用真实服务零售prepare及必要的querybymobile，检查当前会员手机号归属、门店设置、普通单券及具体卡模板映射后加密持久化预览。详细契约与暂缓边界见[小程序接口文档](uniapp/uniapp-api.md)末尾V033章节，配置见[数据库手册](../script/gym/DATABASE.md)。平台prepare和手机号查询的platform枚举相反，显式转换，不沿用同一个数字。

此增量只写gym_coupon_preview，不改变原16项后台请求框架，不核销、不发权益、不生成财务或签到；33–34核销与本人结果仍待实现。当前普通单券/美团以外场景不宣称已支持，真实授权和实券联调尚未完成，移动开关默认关闭。

## 2026-10-26 会员核销适配准备（尚未接通33–34）

新增内部`GymMemberCouponConsumption`，复用真实Client/Transport构造单券consume与getConsumed。它没有HTTP入口、持久化核销流水或生产调用者；不检查会员身份、不占券、不发卡。将其连接到会员API前，必须由业务编排完成本人资格检查、持久化唯一占券、固定requestId、只发送一次及本地发卡事务。当前路由覆盖仍122/135，不因适配器而增加接口数。

consume固定count=1，使用业务层事先保存的requestId；适配器不生成、不修改、不自动重发该值。平台操作账号来自当前租户/门店显式merchant-accounts配置，不把会员ID伪装为商家员工。后台16项入口仍使用原有当前员工身份，不改变其权限或请求行为。appShopAccount字段的OpenAPI说明补充了平台商家系统操作账号含义，客户端仍不可注入。

平台OP_SUCCESS还必须包含恰好一条结果：原券码、dealId、dealGroupId完全相同，bizType=0、tgTimesCardFlag=false、dealQuantity为空或1，无关联券，且flowId/orderId非空。否则抛1070022007结果未知；网络/格式/业务异常同样不能当成确定失败，更不能发卡。仅原传输层明确的发送前配置拒绝或公共参数拒绝保留其确定错误码。成功确认只保留HMAC券摘要、商品、固定requestId、授权指纹、流水/订单/追踪标识；不保留手机号或明文券码。标识限长和无控制字符是本地防御约束，不声称是官方长度上限。

查询已验券仅返回Observation（券摘要、商品、订单、traceId），与Confirmation使用不同类型；即使券码/商品一致，也不能据此认定是本次requestId消费，更不能自动发卡或重复核销。官方getconsumed未提供与本次requestId直接关联的凭据；未来未知态应继续处理中并交由受信核实流程，不以“已验”或一次查询错误自动收敛为成功/失败。

新增独立consumption-enabled默认false；启用预览不会开启核销。消费另需移动总开关、平台enabled/mutations-enabled、精确租户门店授权、businessId=58、商家审计账号及32字节密钥。授权指纹使用独立用途HMAC，覆盖租户/店、开发者、签名密钥、令牌、业务编号、realOpBizCode及商家审计账号/名称；不保存凭据原文。配置变化后旧scope拒绝发送/查询。关闭移动或mutation开关仍可只读查询已有scope，但平台查询授权必须可用且一致。部署配置目前无热更新，不能把进程内属性并发改写当作受支持的换权流程。

网络方法显式NEVER事务，禁止在数据库事务内调用。49项真实HTTP组装/模拟响应及2项配置绑定增量测试通过；未发真实核销请求，未验证实券。持久化核销、未知结果恢复与33–34仍待后续小步实现。

## 2026-10-26 会员核销与履约增量（V034）

前文“内部适配准备”仅描述上一批；33–34现已接入GymCouponVerificationTransactions/Worker与真实CardService/ContractService。按本人预览和持久化请求占券，网络前后复核，提交sending后只发送一次；真实同步确认独立保存后，本地卡/合同/通知/核销记录/成功状态同事务提交。具体参数、状态表和幂等规则见[会员API V034](uniapp/uniapp-api.md#会员团购核销与本人结果v034总表3334)。

未知消费与已核销待发卡分开：前者最多6次只读观察后保留待核实，不从getconsumed推断成功；后者在当前会员资格有效时持续重试本地履约，不能释放券占用或再次consume。平台成功响应丢失/本地确认持久化始终失败的场景尚无自动关联解决能力；当前也没有人工任意改成功入口。实际渠道和实券未联调，不将模拟验证视作上线验收。原16项后台转发框架、退款审批/真实渠道/财务对账未因此完成。

V034只新增gym_coupon_verification，无历史核销回填或权限/角色变化。客户端结果不输出平台流水或私密信息。核销模板现只支持普通单张美团券换一张卡，无自动签到/现金收入/积分奖励。业务库及前端未改。
