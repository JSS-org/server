# 会员反馈与私有图片

原移动端反馈表单已接真实会员反馈服务。四个接口均使用现有会员认证：

| 接口 | 行为 |
| --- | --- |
| POST /app-api/member/feedback/upload | multipart 的 file 字段上传图片，返回附件编号 |
| POST /app-api/member/feedback/create | content 与 attachmentIds，返回已持久化的反馈编号 |
| GET /app-api/member/feedback/get?id= | 返回本人反馈及图片编号顺序 |
| GET /app-api/member/feedback/attachment?id= | 经本人及租户校验后返回图片字节，private/no-store、nosniff |

反馈内容去除首尾空白后须为10–500字符，最多9张图片。每张图片最多5MiB，根据内容签名接受PNG、JPEG、GIF、WebP，不信任客户端文件名/MIME。图片使用独立的会员业务表保存二进制内容；会员编号来自认证上下文，租户来自TenantContextHolder。所有读取、领取查询均显式带会员和租户条件。反馈提交在事务内锁定图片元数据，检查未使用状态，再按原顺序关联；同一图片不能被另一反馈重复领取。未提交图片继续归原会员所有。

发布需要执行新增受管迁移V029__member_feedback.sql，增加member_feedback与member_feedback_attachment两表/21列，无样例数据、员工授权或已有数据覆盖。已有infra_file保持原行为；反馈图片不产生公开存储URL，也不依赖第三方文件提供商配置。本次没有操作开发库或部署迁移。

6项实际H2 Service/Mapper检查通过，覆盖反馈和字节持久化、原图片顺序、精确Long会员编号、另一会员/租户的读取和领取拒绝、重复/已领取图片、伪造图片类型/超限、匿名及空白内容。标准jss-mall完整server compile退出0。归属MySQL8.4.9:13316随机库核对V029重复DDL、全部列、二进制与Long往返、一次领取、回滚及清理后库残留0，见.runtime/member-feedback-schema.json；H2结果见.runtime/member-feedback-focused-result.json。

这些证据没有替代真实HTTP令牌过滤器、完整Flyway/context、生产并发/容量、终端图片选择/展示或真实设备验收。未提交图片的保留/清理策略尚未纳入后台定时清理；本次没有添加自动删除业务数据的任务。

## 2026-10-29 未提交图片清理增量

代码d5bf96a5fd090d0dc5e87d66854a677257a272d9补入`memberFeedbackAttachmentCleanupJob`，实现上文待处理的保留策略。沿用现有受管Quartz任务和`@TenantJob`，不新增HTTP删除入口，不自动注册、启用任务或执行数据库维护。部署时在既有任务管理中配置该处理器；参数为保留小时数，空值默认168小时（7天），允许24–8760小时。调度频率按环境配置，本轮没有改任何调度记录或运行服务。

每租户每批仅选择最多20个、创建时间严格早于统一截止时刻且`feedback_id IS NULL`的附件编号，不加载BLOB；每次最多5批/100张。删除语句同时重新核验租户、创建时间和未关联反馈状态，采用物理删除以清除未使用的二进制行。选择后已被反馈提交领取的图片不会删除；提交路径仍在事务中锁定元数据。已提交附件、较新的上传、其他租户和反馈主记录均保留。部分候选被领取时可继续有限批次，零删除或达到上限后结束本次运行，下次调度继续。

11项定向通过：原6项反馈业务检查保持，新增3项实际H2 Service/Mapper检查验证按批和租户清除、物理行确实移除、已提交/新图片保留、选中后领取的条件复核、缺租户/不安全截止时刻/无界批量拒绝；另2项任务检查验证租户注解、同一截止时刻、最大调用次数和参数范围。证据`.runtime/feedback-cleanup-focused-result.json`、`.runtime/feedback-cleanup-focused.log`；首轮测试夹具缺JdbcTemplate Bean的失败保留`feedback-cleanup-focused-first.log`，改为从既有DataSource构造检查器，没有改产品逻辑或放宽断言。member及其上游reactor编译/测试编译随定向命令通过，未重跑完整server、HTTP或浏览器组合。

本轮无DDL或业务库操作。该增量尚未在真实调度器、MySQL并发提交/清理或生产容量上验收，任务未部署/启用；已有V029隔离MySQL及真实反馈HTTP历史证据不作为本增量的调度或并发证明。
