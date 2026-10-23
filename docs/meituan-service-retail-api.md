# 美团服务零售团购核销 API 清单

核对日期：2026-10-19。范围为美团技术服务合作中心 **生活服务 → 服务零售 → 团购核销 API** 目录的全部 16 项，含该目录内退款接口；不包含另一个目录的商家券发券/退券、预约、财务或授权管理 API。当前交付为后端 POST 请求框架，真实平台授权和联调未接入。

所有平台地址前缀为 `https://api-open-cater.meituan.com`，所有本地地址前缀为 `/admin-api/gym/group-buy/meituan/retail`，HTTP 方法均为 POST。

## 逐项对照

| 序号 | 官方文档 | 平台路径 | 本地路径 | 权限 |
| --- | --- | --- | --- | --- |
| 1 | [输码验券校验](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-prepare) | `/ddzh/tuangou/receipt/prepare` | `/receipts/prepare` | `gym:sales:read` |
| 2 | [验券](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-consume) | `/ddzh/tuangou/receipt/consume` | `/receipts/consume` | `gym:verifications:manage` |
| 3 | [撤销验券](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-reverseconsume) | `/ddzh/tuangou/receipt/reverseconsume` | `/receipts/reverse-consume` | `gym:verifications:manage` |
| 4 | [查询已验券信息](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-getconsumed) | `/ddzh/tuangou/receipt/getconsumed` | `/receipts/consumed/query` | `gym:sales:read` |
| 5 | [验券记录](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-querylistbydate) | `/ddzh/tuangou/receipt/querylistbydate` | `/receipts/history/query` | `gym:sales:read` |
| 6 | [获取团购信息](https://developer.meituan.com/docs/api/ddzh-tuangou-deal-queryshopdeal) | `/ddzh/tuangou/deal/queryshopdeal` | `/deals/query` | `gym:sales:read` |
| 7 | [查询门店商品信息](https://developer.meituan.com/docs/api/ddzh-tuangou-product-queryproductbytype) | `/ddzh/tuangou/product/queryproductbytype` | `/products/by-type/query` | `gym:sales:read` |
| 8 | [按商品类型查询门店商品信息](https://developer.meituan.com/docs/api/ddzh-tuangou-product-queryproduct) | `/ddzh/tuangou/product/queryproduct` | `/products/query` | `gym:sales:read` |
| 9 | [手机号查询可用团购券](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-querybymobile) | `/ddzh/tuangou/receipt/querybymobile` | `/receipts/by-mobile/query` | `gym:sales:read` |
| 10 | [次卡批量验券](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-batchconsume) | `/ddzh/tuangou/receipt/batchconsume` | `/receipts/batch-consume` | `gym:verifications:manage` |
| 11 | [查询标品信息](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-queryproduct) | `/ddzh/tuangou/receipt/queryproduct` | `/standard-products/query` | `gym:sales:read` |
| 12 | [团购退款预校验](https://developer.meituan.com/docs/api/ddzh-tuangou-pre-refund-valid) | `/ddzh/tuangou/pre/refund/valid` | `/refunds/precheck` | `gym:group-buy:refund` |
| 13 | [团购退款申请](https://developer.meituan.com/docs/api/ddzh-tuangou-apply-refund) | `/ddzh/tuangou/apply/refund` | `/refunds/apply` | `gym:group-buy:refund` |
| 14 | [团购退款信息查询](https://developer.meituan.com/docs/api/ddzh-tuangou-query-refund-info) | `/ddzh/tuangou/query/refund/info` | `/refunds/query` | `gym:group-buy:refund` |
| 15 | [团购退款审核](https://developer.meituan.com/docs/api/ddzh-tuangou-refund-audit) | `/ddzh/tuangou/refund/audit` | `/refunds/audit` | `gym:group-buy:refund` |
| 16 | [批量撤销验券](https://developer.meituan.com/docs/api/ddzh-tuangou-receipt-batch-reverseconsume) | `/ddzh/tuangou/receipt/batch/reverseconsume` | `/receipts/batch-reverse-consume` | `gym:verifications:manage` |

## 参数和类型

本地有业务参数的请求统一为 `{ "storeId": "12", "biz": { ... } }`。storeId 只用于本地权限及凭据选择，不进入平台 biz。公共字段由 MeituanCommonRequest 承载，各接口业务字段由下列独立类型承载，均通过 getter/setter 访问；MeituanResponse<T> 是公共响应信封，业务响应类型位于 MeituanRetailResponses。

| 序号 | 业务请求 DTO | 必填项及关键约束 | 响应数据类型 |
| --- | --- | --- | --- |
| 1 | `MeituanReceiptCodeRequest` | receiptCode | `Prepare` |
| 2 | `MeituanConsumeRequest` | receiptCode、count（1–100）、requestId；操作人由服务端填充 | `Consume` |
| 3 | `MeituanReverseConsumeRequest` | receiptCode、dealId；操作人由服务端填充 | `ReverseConsume` |
| 4 | `MeituanReceiptCodeRequest` | receiptCode | `Consumed` |
| 5 | `MeituanReceiptHistoryRequest` | date、offset（从0开始）、type（0核销/1撤销）、limit（1–300）；bizType可选 | `History` |
| 6 | `MeituanShopDealsRequest` | offset（从1开始的页码）、limit（1–100）、source（1点评/2美团） | `Deals` |
| 7 | `MeituanProductsByTypeRequest` | 必填type；pageNo、pageSize可选，pageSize最大100 | `ProductsByType` |
| 8 | `MeituanProductsRequest` | pageNo、pageSize可选；该接口实际没有type字段 | `Products` |
| 9 | `MeituanMobileReceiptsRequest` | mobile、dealGroupId、dealId、offset（从0开始）、limit（1–30）、platform（1点评/2美团） | `MobileReceipts` |
| 10 | `MeituanBatchConsumeRequest` | requestId、receiptCodeInfos[]；每项receiptCode、count（字符串1–100）；操作人由服务端填充 | `BatchConsume` |
| 11 | `无业务 DTO` | 无平台业务参数；本地请求仅storeId，出站不发送biz | `StandardProducts` |
| 12 | `MeituanRefundPrecheckRequest` | orderId与serialNumbers至少一个；sequenceId可选 | `RefundPrecheck` |
| 13 | `MeituanRefundApplyRequest` | orderId与serialNumbers至少一个；本系统额外要求sequenceId；部分退款须正数refundAmount | `RefundApply` |
| 14 | `MeituanRefundQueryRequest` | orderId、refundId、sequenceId | `RefundQuery` |
| 15 | `MeituanRefundAuditRequest` | sequenceId、orderId、refundId、auditResult（1同意/2拒绝）、refundFeeType、refundAmount | `RefundAudit` |
| 16 | `MeituanBatchReverseConsumeRequest` | receiptCodeList、dealId；操作人由服务端填充 | `ReverseConsume` |

consume 还支持平台文档中的 orderId，以及 thirdDeviceId、thirdDeviceLocation、thirdDeviceType、thirdDeviceCity、thirdVenueId、thirdLocationType 可选字段，其中设备字段官方标注未上线，本系统仅保留类型和透传，不宣称平台已支持。退款申请同时保留 receiptList、refundFeeType、refundAmount、refundReason。完整可选字段与嵌套类型见 Java DTO 和 OpenAPI。

## 必须注意的文档差异

- 官方菜单“查询门店商品信息”实际对应 `/product/queryproductbytype`，有必填 type；菜单“按商品类型查询门店商品信息”实际对应 `/product/queryproduct`，只有可选分页。实现按字段表与实际请求路径对应，本地命名按实际参数语义区分。
- 获取团购信息的 offset 是从 1 开始的页码；验券记录、手机号查券的 offset 是从 0 开始的记录偏移。不能统一当成页码。
- 次卡批量验券嵌套 count 的官方类型是 string，普通验券 count 为 int；代码分别建模。普通验券数量最多100，不能将次卡批量验券用于普通团购券。
- 查询标品信息没有业务参数，按官方示例不发送 biz，不能硬编码额外业务字段。
- 退款预校验的 receiptList / refundAmountItemList 字段表为 object；退款信息查询的对应字段为 array。两种响应分别建模，不强制共用一种结构。
- 官方示例部分字符串含重复转义引号、数组示例把多个券码拼在一项中。实现依据字段表中的 string / array<string> 类型，实际券码不加额外引号，每张券一项；测试使用符合字段定义的样例，不复制示例格式瑕疵。
- 券码使用 String，不限制为旧10位，保留13位和前导零；可能混淆的用户标识使用 String。平台返回的货币字段按文档保留 BigDecimal、String 或 Long，不统一换算单位。退款响应中 value、maxRefundAmount 为分；其余金额以平台具体字段定义为准。
- 部分核销响应使用 dealgroupId，部分使用 dealGroupId，响应模型使用 JsonAlias 接收并统一为 dealGroupId。批量接口保留 singleConsumeCode / singleConsumeMsg，不把 OP_SUCCESS 当作所有券都核销成功。
- 撤销接口受平台规则限制：仅当天核销且未超过10分钟的适用券；具体券型能否撤销由平台判定。本框架没有本地状态缓存，不用本地时间计算冒充平台校验。

## 业务边界与证据

平台共同业务编号按当前文档为58。平台授权、退款能力是否对当前商户行业开放，仍须实际申请；目录中有文档不表示当前应用已获权。公共签名、租户门店范围、身份填充、错误处理及配置见 [团购业务说明](group-buy-verification.md)。

`yudao-module-gym/src/test/resources/meituan/retail-contracts.json` 独立列出上述16个接口的文档来源、路径、参数和响应样例。客户端测试对每项检查真实组装的表单 POST；MVC 测试逐项覆盖路由、实际 Service 调用、权限及门店拒绝，并验证调用方不能伪造操作人。它们是本地模拟证据，不代表平台真实联调。具体命令和结果见 [testing.md](testing.md)。
