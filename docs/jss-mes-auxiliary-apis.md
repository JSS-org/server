# MES 原辅助 API 补齐

原移动端保留的三个辅助函数现接入实际 Controller/Service。原 MES schema、库存写入流程、角色授予和既有页面不变。

| 接口 | 参数 | 权限及行为 |
| --- | --- | --- |
| GET /admin-api/mes/wm/misc-issue/check-quantity | id | mes:wm-misc-issue:query；读取主单、全部行和全部分配明细，逐行精确比较 BigDecimal 数量 |
| GET /admin-api/mes/wm/misc-issue-line/list-by-issue-id | issueId | mes:wm-misc-issue:query；使用已有行查询 Service，沿用原物料、计量单位及仓库名称拼装 |
| GET /admin-api/mes/wm/product-receipt-detail/list | receiptId 或 lineId，至少一个 | mes:wm-product-receipt:query；两个编号均传时取交集，沿用原明细与关联名称拼装 |

数量校验为只读事务，不扣减或预留库存，也不能替代真正出库时的库存校验。主单不存在按原错误码拒绝；无行、行数量为空/非正、明细缺失/非正、孤立明细、任一行合计不一致返回 false。分拆分配和不同小数位用 BigDecimal 比较，不以全单汇总相等掩盖行级错配。

列表查询的编号用 Long 处理。产品收货明细通过 Bean Validation 要求至少一个正数父编号，避免不带条件读取全部明细；查询继续使用原 Mapper 的租户与逻辑删除处理，不增加忽略租户的路径。

6 项定向检查通过（数量 5 项、列表参数 1 项），覆盖分拆小数、超过 JavaScript 安全整数的编号、总量相同但行级不符、缺少/孤立/非正明细、无单/无行和父编号约束。证据 .runtime/mes-auxiliary-focused-result.json；这些是 Mockito 和参数校验检查，不是实际数据库、HTTP、RBAC、租户或库存事务验收。完整 server 编译记录单列于 .runtime/mes-auxiliary-server-compile.log。

标准 jss-mall 的 yudao-server reactor compile（跳过测试）已退出 0，.runtime/mes-auxiliary-server-compile.exit 为 0；未启动业务服务、未操作开发数据库、未发起真实库存操作。
