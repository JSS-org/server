# JSS 统一身份与工作台会话

此模块实现 `JSS-uniapp/docs/integration-plan-v1.md` 的会员主身份、受控员工绑定和工作台授权链路。不会按手机号、会员等级、会员分组或相同用户 ID 自动赋予后台权限。完整商城模块和真实环境验收仍在实施。

## 部署前提

1. 使用 JDK 25，构建包含 `yudao-module-jss` 的 `yudao-server`。该模块经 API 依赖 member/system，不让 system 反向依赖 JSS。
2. 在测试数据库核验会员模块所需表和配置。本地框架初始化 SQL 不含会员业务表，现已补齐 `sql/jss/migration/mysql/V001__member_schema.sql`。不要将 gym 会员表视为商城会员表。
3. 按 `sql/jss/README.md` 显式运行 JSS Flyway 迁移，使用 V001 会员表和 V002 绑定/审计/权限定义。不会创建账号、绑定或批量授权。表约束已在隔离 MySQL 8.4.9 的随机测试库验证，未在业务数据库执行；原独立 `001-identity.sql` 已移入 V002。
4. 使用现有后台角色/租户套餐管理为审核人员配置 `jss:identity:query`、`jss:identity:bind`；为获准员工配置 `jss:workspace:access` 和具体业务权限。新增节点也需纳入适用的租户套餐。不要给普通会员分组授予此权限。
5. 通过环境配置设置 `jss.workspace.enabled=true`（环境变量 `JSS_WORKSPACE_ENABLED=true`）。默认关闭，关闭后已有工作台会话在下一次请求时被拒绝。
6. Redis 使用现有服务的单实例/主从连接；当前两键 Lua 实现尚未验收 Redis Cluster 跨槽部署。

后台账号、会员登录方式的认证强度须满足所分配权限；如需财务/超管 MFA，仍需补充二次验证，当前模块不声明已实现 MFA。

## API

所有接口返回现有 `{code,data,msg}`。会员和管理端用户类型校验保留。

| 方法和路径 | 凭证/权限 | 行为 |
| --- | --- | --- |
| `GET /app-api/jss/auth/context` | MEMBER Token | 查询当前会员能力；无绑定或无权限时成功返回 `workspace=false` |
| `POST /app-api/jss/auth/workspace-session` | MEMBER Token | 根据服务端绑定重新校验并签发五分钟以内的 ADMIN 工作台访问凭证，无请求体、无刷新令牌 |
| `POST /app-api/jss/auth/logout` | 当前 MEMBER Token | 幂等撤销父会员会话，使所有关联工作台请求失败 |
| `GET /admin-api/jss/identity/get-by-member?memberUserId=…` | `jss:identity:query` | 查询同租户关联 |
| `POST /admin-api/jss/identity/bind` | `jss:identity:bind` | 审核绑定，必填 `memberUserId`、`adminUserId`、`reason` |
| `POST /admin-api/jss/identity/revoke` | `jss:identity:bind` | 撤销绑定，必填 `id`、`reason` |

新增 context 契约的主体 ID 为十进制字符串；既有接口协议不变。工作台会话响应为 `{accessToken,expiresTime}`，`expiresTime` 为毫秒时间戳。员工完整资料、菜单和业务权限继续通过 `GET /admin-api/system/auth/get-permission-info` 获取。

绑定是管理员身份审核行为；`reason` 应记录核验依据。会员在当前租户保留一条版本化关联记录，生效员工关联在数据库内保持一对一。生效关联不能直接覆盖；先撤销，再经审核重新绑定/更换员工，版本递增。撤销后员工可绑定其他已核验会员。`jss_identity_audit` 记录每次绑定/撤销的双方 ID、版本、操作人、时间和原因；不要物理删除关联或用直接 SQL 绕过版本与审计流程。

## 请求验证与撤权

JSS 使用 `jssw_` 前缀的不透明随机凭证，Redis 键保存凭证的 SHA-256 摘要。仅服务器 Redis 会话记录保存父会员访问凭证，禁止将完整会话对象输出到日志或 API。凭证最长五分钟，且不晚于父访问令牌到期；同一会员访问会话的新工作台凭证替换旧凭证。

`OAuth2TokenExtension` 使 JSS 凭证进入现有 OAuth Token 校验 API。每次校验都核验当前租户、有效父会员会话、会员状态、绑定状态/版本、员工状态和现有 RBAC 入口权限。业务接口随后继续执行原来的方法权限与数据权限。旧 JSS 凭证不能因前端篡改导航或本地缓存而获得权限。普通后台 OAuth Token 不经过 JSS 绑定判定，仍使用原有实现。

原 `/member/auth/logout` 也通过现有 OAuth API 删除父凭证，所以其关联工作台会话同样无法继续使用。服务端会话物理数据按 Redis TTL 过期；失效判定不等待 TTL。绑定撤销后再重绑不能恢复旧版本凭证。角色/菜单修改须走现有管理 API 以触发 RBAC 缓存失效，直接 SQL 修改需另行按现有运维流程清理缓存。

关闭开关将拒绝现存工作台请求；如需关停后重新开放也永久销毁关停前的所有凭证，应维持关闭至少五分钟，或在已核验的 Redis 环境中清除 `jss:workspace:*`（不要使用全库清空）。数据库回退应保留绑定和审计表，先关闭能力再回退应用，不应删除已有会员或后台数据。

## 已完成的本地验证与未完成门槛

已通过新增模块测试：17 项会话授权/撤权场景、6 项绑定审核场景及 4 项原 OAuth 路径回归；完整 server 容器已成功打包。测试使用替身隔离 system/member/Redis，不证明真实数据库授权链、Redis Lua 或租户拦截器已完成集成验收。

另已通过 `script/jss/verify_identity_schema.py` 的 8 项真实 MySQL 约束检查，涵盖重复迁移、同租户有效一对一、跨租户独立键、撤销与重绑、审计快照、非法版本/状态、逻辑删除和并发审核。工具与复现步骤见 `script/jss/README.md`。

`script/jss/verify_workspace_redis.py` 的四项实际 DAO 集成测试已在独立 Redis 8.10.2 上通过，使用生产 `JssWorkspaceSessionDAO`、Redisson/Spring Data Redis 和实际 Lua，不使用 Redis 替身。覆盖摘要键和双键 TTL、同父会话新凭证替换、旧会话删除保留新指针、租户指针独立、实际过期与已过期输入拒绝。隔离实例为 `127.0.0.1:16379`，测试仅清理自己创建的随机会话，不操作现有 Redis 服务；不代表 OAuth/RBAC/绑定 HTTP 组合验收已完成。

仍需：有效/禁用多角色授权、同租户与跨租户绑定 API、租户过期、跨门店业务权限、真实会员登出与工作台撤权的组合链路、原后台并用回归，以及真实 API 的 A01–A16 验收。不得将源码存在、约束测试、DAO 集成测试或打包成功登记为完整端到端完成。
