# Windows 本地开发数据库（2026-10-24）

## 当前运行环境

- MySQL：`127.0.0.1:3306`，应用数据库为 **`jss_dev`**。
- Redis：`127.0.0.1:6379`，当前应用独立使用 **数据库 15**，未清空其他 Redis 数据库。
- 后端：`http://127.0.0.1:48080`，接口文档 `/doc.html`。
- 启动入口：`%LOCALAPPDATA%\JSS\start-backend.ps1`。脚本显式覆盖主、从数据源 URL；仓库内 application-local.yaml 的默认库名未修改。
- 旧 `ruoyi-vue-pro` 库仍保留。此次新建开发库，而非覆盖或全量重放旧业务库。

```powershell
& "$env:LOCALAPPDATA\JSS\start-backend.ps1"
```

启动脚本不重复启动已监听 48080 的服务；不会自动切换一个正在运行的旧进程。数据库切换应先确认并停止目标后端，再用脚本启动。

2026-10-26 已重新构建并更新本地后端，包含访问日志凭证脱敏及菜单停用对 JSS 请求的限制。六项真实 HTTP 与一项实际 H5 联合验收通过。构建使用 `jss-mall` 与独立 final-name，核对包内七个安全/菜单/日志修复 class 与当前编译产物一致后，备份旧 jar、核验目标进程路径，再替换默认 jar 并通过现有脚本启动。没有重放数据库或改变账号授权。

- 当前 jar SHA-256：`0e366db44ea9e0ddc340ef4a208ca6d9c20df28ac156cbf49cba121d1fa2844a`。
- 上一版 jar：仓库忽略目录 `.runtime/jss-local-development/yudao-server-before-20261026-140037.jar`；更早的 `yudao-server-before-20261026-133302.jar` 仍保留。数据库及 Redis 未替换。

更新后核对运行路径的 jar 哈希一致、应用正常启动，48080 分类接口 HTTP 200/业务码 0，9000 H5 预览 HTTP 200。菜单停用的授权行为证据来自上述隔离数据库的真实 HTTP/H5 验收；没有在开发库改变角色或菜单以重复试验。

## 数据库组成与迁移

新库共 196 张表：框架基座、11 张空 Quartz 表、Gym V001～V003、JSS V001～V009，以及两套独立 Flyway 历史。核对当前启用业务模块的 134 个数据模型、2,200 个持久化字段，没有缺失列。

Gym 演示账号/角色通过显式 development seed 创建。商城商品、活动、订单、资金和统计未伪造；表结构完整不代表已有演示商品、真实支付渠道或所有业务均完成端到端验收。

部署使用的 SQL/POM 快照、SHA-256 清单、结构检查结果及旧库完整备份位于：

```text
%LOCALAPPDATA%\JSS\backups\rebuild-20261024-203010\
  ruoyi-vue-pro.sql
  manifest.json
  schema-verification.json
  start-backend.before.ps1
  sql\
```

备份通过 mysqldump 成功退出及文件 SHA-256 校验，大小 1,228,016 字节；未声明已经执行整库备份恢复演练。工作区存在并行开发时，已部署版本以该 SQL 快照和 Flyway 历史为准，不得修改历史文件、删除迁移记录或直接 repair 来掩盖校验和差异。后续正常升级参考 `../sql/jss/README.md` 和 `../script/gym/DATABASE.md`；Windows Python 使用 `-X utf8` 以避免 GBK 解码开发 seed。

## 本地短信登录

- 会员测试手机号：`13800000001`。
- 点击“获取验证码”，再填写 **`9999`**。
- 这是本地固定生成的测试验证码，不是免校验万能码；有一分钟发送间隔、十分钟有效期和单次使用校验。
- 所有短信渠道已在新开发库禁用真实发送；接口仍生成验证码并记录短信日志，不调用短信供应商。
- 本地启动禁用 mock token 绕过；登录使用真实 MySQL、Redis 和 OAuth Token 链路。
- 会员不会因手机号与后台演示账号相同自动绑定员工。工作台绑定与授权仍须按 `jss-auth.md` 审核配置，本轮不自动开放。

文件存储在新开发库使用配置 4（数据库存储），避免调用框架导出中的云存储示例。Quartz 自动启动关闭，没有重放导出文件中的历史定时任务。

## 实际验证及边界

`%LOCALAPPDATA%\JSS\verify-development-login.py` 通过真实 HTTP 执行 33 项检查，测试令牌仅保留在内存并最终注销：

- 未登录保护、错误验证码拒绝、固定测试码登录、验证码不可重放、会员资料与退出后令牌失效。
- 会员地址、商品分类/分页、购物车、订单分页/计数、未使用优惠券数量。
- 未绑定会员没有工作台权限，会员令牌不能读取管理员权限。
- gymadmin/gymstore 真实登录、角色和门店读取；门店角色不含总部设置权限。

```powershell
python -X utf8 "$env:LOCALAPPDATA\JSS\verify-development-login.py"
```

脚本会生成并消耗验证码、写入真实登录日志；重复运行受短信频率及每日次数限制。浏览器旧会话不会迁入新库，需要退出旧会话后重新登录。未执行外部短信、微信、支付渠道联调，也未宣称空库查询通过等于下单支付全流程通过。
