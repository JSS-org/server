# 健身团购验券与美团服务零售接入

## 当前交付

健身业务使用美团 **服务零售 → 团购核销 API**。本轮已按该目录全部16项实现 Controller → Service → 平台 Client → 共享表单 POST 传输，包括验券准备、核销、撤销、历史/商品/手机号查询、次卡、退款相关请求。逐项官方来源、路径、参数、类型和文档差异见 [完整 API 清单](meituan-service-retail-api.md)。

交付范围是请求框架：不改前端，不要求真实跑通，不写核销结果、发卡、退款财务或其他业务表；真实商户授权、平台联调、回调、状态补查调度和本地履约未接入。默认关闭接口及状态变更请求，未配置时明确报错，不返回模拟成功。

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
