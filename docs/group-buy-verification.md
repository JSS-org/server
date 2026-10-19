# 团购验券业务与美团历史查询

## 本次交付边界

本次按用户指定的[美团「门店验券历史」接口](https://developer.meituan.com/docs/api/tuangou-coupon-queryListByDate)，搭建后端 Controller → Service → 美团 Client 的 POST 请求链路。前端不变，不要求真实联通；接口默认关闭，未配置授权时明确返回业务错误。

这个接口查询已发生的验券记录，本身不执行验券、核销或撤销，也不能用来判断任意一张未使用券是否可以消费。美团页面所属方案是「到店餐饮 / 团购 API」，正式用于健身行业之前，需要确认平台开通的业务包、businessId 和授权是否适用，不能把餐饮接口直接视为到综行业权限。

代码已具备出站请求能力，但真实平台授权与联调**未接入、未验证**。当前不写入 `gym_verification`，不发放会员卡或权益，不产生财务收入。

## 团购验券在业务中的位置

完整业务通常包括：商户授权、平台门店与本地门店关联、查询/准备验券、核对套餐与数量、执行核销、记录平台凭证、查询核销结果、允许时撤销、日后对账。本次只实现其中的「按日查询平台历史」，为后续追溯、核对提供请求入口。

| 能力 | 当前状态 |
| --- | --- |
| 美团按日验券历史 | POST 链路、类型、签名、响应处理已搭建；真实联调未接入 |
| 美团验券准备、执行、撤销、异常补查 | 未实现，须按对应接口另行接入 |
| 抖音、快手等平台 | 未接入；后续增加各自的 Client 与业务参数类型 |
| 商户授权流程、令牌更新与门店绑定管理 | 未接入；当前仅从外部服务端配置读取已取得的授权 |
| 自动同步、落库、权益发放与结算对账 | 未实现，不把查询结果直接认定为收入或开卡依据 |

## 本系统 POST 入口

`POST /admin-api/gym/group-buy/meituan/coupon-history/query`

请求使用 `application/json`，沿用登录 Bearer token、`tenant-id` 和统一 `CommonResult`。功能权限复用现有 `gym:sales:read`，不新增权限迁移或修改角色授权。服务层先通过 `GymStoreService.requireStore` 检查当前租户的真实门店和员工门店范围，再选择该租户、该门店的美团凭据。SELF 负责会员关系不扩展为整店平台查询权限。

```json
{
  "storeId": "12",
  "date": "2026-10-19",
  "offset": 0,
  "limit": 20
}
```

| 字段 | 本系统约束 | 说明 |
| --- | --- | --- |
| storeId | 必填，正整数 ID | 本地门店，服务端使用；不会发给美团 |
| date | 必填，合法的 yyyy-MM-dd 日期 | 明确传入目标日期，不做客户端时区推算 |
| offset | 默认 0，非负整数 | 记录偏移量，不是页码 |
| limit | 默认 20，1–200 | 美团参数范围为 0–999，但官方建议不超过 200；本系统采用更小范围 |

只查询一批，不自动翻页或重试。需要下一批时由调用方按偏移量再次查询。

成功时外层为 `{ "code": 0, "data": ... , "msg": "" }`，`data` 保留美团的 `code / msg / traceId / data` 信封；平台业务内容为 `total` 和 `couponCodes`。这不是现有 `GET /gym/verifications` 的本地分页模型，也不会改变其返回结构。

券记录包含券码、状态描述、购买价格、项目售卖价、使用时间、是否可撤销、验券方式及商企通信息等。券码使用 String 保留前导零，金额使用 BigDecimal，时间保留平台原始字符串；金额不推算佣金、到账或本地收益。字段以 Java 类型及 OpenAPI 模型为准。

## 美团出站 POST

固定地址：`https://api-open-cater.meituan.com/tuangou/coupon/queryListByDate`。

发送 `application/x-www-form-urlencoded;charset=utf-8`，不是直接发送 JSON body。业务类型 `MeituanCouponHistoryRequest` 仅有 `date / offset / limit`，通过 setter 赋值，再序列化为一次性的 `biz` 字符串。Controller 的请求 VO 负责本地参数校验，与平台 DTO 分开。

公共参数由 `MeituanCommonRequest` DTO 承载，开发者、授权、时间戳等均通过 setter 赋值；`charset / version` 提供默认值。只在签名和表单传输边界调用 `toParameters()`，转为按参数名排序的 Map；空值不发送，biz 保留同一份 JSON 原文，签名密钥不进入 DTO。公共字段如下：

| 参数 | 来源 |
| --- | --- |
| appAuthToken | 当前租户、门店对应的服务端授权配置 |
| developerId / signKey | 服务端配置；signKey 仅用于签名，不发送 |
| businessId | 门店授权配置，默认按本接口示例取 1，实际以开通方案为准 |
| timestamp | 服务端生成的 Unix 秒级时间戳 |
| charset / version | 固定 utf-8 / 2 |
| biz | 平台 DTO 序列化得到的 JSON 字符串 |
| realOpBizCode | 可选，多业务授权模式下的授权实体 ID |
| sign | 按官方规则计算的 SHA1 小写十六进制摘要 |

签名在 URL 编码前计算：排除 sign 和空值，参数名自然排序，拼接 key/value，前置 signKey，按 UTF-8 计算 SHA1。签名和发送使用完全相同的 biz 字符串；最终由表单转换器编码，避免包含 `+ / & / = / 中文` 时破坏参数。

连接超时默认 3 秒，读取超时默认 10 秒，不自动跟随重定向。凭据类型不生成包含秘密的 toString；接口响应不写访问日志，异常不透传上游原始响应。

## 配置方式

基础配置位于 `yudao-server/src/main/resources/application.yaml`，默认 `enabled: false`、空授权。开发者标识与签名密钥可用 `GYM_MEITUAN_DEVELOPER_ID`、`GYM_MEITUAN_SIGN_KEY` 提供。门店授权通过外部配置文件或配置中心注入，不将真实凭据提交到仓库。

以下为外部配置结构示意，`${...}` 是待提供的环境变量，不是实际凭据：

```yaml
yudao:
  gym:
    group-buy:
      meituan:
        enabled: true
        developer-id: ${GYM_MEITUAN_DEVELOPER_ID}
        sign-key: ${GYM_MEITUAN_SIGN_KEY}
        connect-timeout-millis: 3000
        read-timeout-millis: 10000
        tenants:
          1:                       # 本系统租户 ID
            stores:
              12:                  # 本系统门店 ID
                app-auth-token: ${GYM_MEITUAN_T1_S12_TOKEN}
                business-id: 1
                # real-op-biz-code: 按平台授权结果填写，普通授权可省略
```

调用方不能在请求中指定平台地址或选择其他门店的令牌。当前租户/门店未配置时直接报错，不借用其他租户或门店授权。更换配置后按部署环境重启加载；本次没有实现动态授权刷新。

## 错误与后续联调

| 本地业务码 | 含义 |
| --- | --- |
| 1070022001 | 接口未启用、开发者凭据缺失或当前租户门店授权未配置 |
| 1070022002 | 上游 HTTP 或网络调用失败 |
| 1070022003 | 美团返回非 OP_SUCCESS 业务码，需核查平台授权及接口权限 |
| 1070022004 | 响应不是有效 JSON，或缺少必要业务结果 |

本地参数错误、未登录、无功能权限和门店越权沿用现有框架/门店业务错误。平台失败不转换为成功空列表。当前上游失败只提供稳定本地错误，不透传平台 msg 或原始响应；未来联调可补充经过脱敏的 code/traceId 诊断记录。

联调前需取得正确行业的接口能力、developerId、signKey，以及每家门店的真实 appAuthToken，确认 businessId 和多业务授权模式；再测试授权失效、有数据/无数据分页、超时和平台业务错误。后续真正执行核销时，还需设计重复请求控制、未知结果补查、撤销约束，以及平台券与本地权益的映射和发放幂等。

验证命令和实际结果见 [testing.md](testing.md) 的 2026-10-19 条目。当前所有新增测试均为本地模拟，不访问美团生产接口。

## 官方文档

- [门店验券历史](https://developer.meituan.com/docs/api/tuangou-coupon-queryListByDate)
- [公共请求参数](https://developer.meituan.com/docs/biz/biz-comm-sys-param-req)
- [签名规则](https://developer.meituan.com/docs/biz/comm-dev-isv-sign-rule)

核对日期：2026-10-19。文档正文通过浏览器读取；请求地址、HTTP 方法、表单结构和参数约束以官方页面为依据。
