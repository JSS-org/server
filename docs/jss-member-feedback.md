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
