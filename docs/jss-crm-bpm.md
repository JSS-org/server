# CRM与BPM统一后端接入

当前新增可选Maven profile `jss-crm`，根reactor同时包含yudao-module-bpm和yudao-module-crm，yudao-server同时依赖两个原模块。CRM原POM对BPM的依赖保持，BPM沿用依赖管理中的Flowable8.0.0实际process/actuator starter，没有删除审批依赖或加入空实现；仍使用原同一YudaoServerApplication和统一身份链。

与已接入商城一起构建：

```powershell
.runtime/maven-integration.cmd -B -ntp '-Pjss-mall,jss-crm' -pl yudao-server -am compile '-DskipTests=true' '-Dspotless.skip=true'
```

2026-10-28首次真实编译成功，32模块全部SUCCESS，38.481秒；BPM23.691秒、CRM6.241秒，二者均实际在reactor中编译，服务器也因依赖变化重新编译。日志.runtime/crm-bpm-compile-first.log、退出标记0。此阶段只验证完整依赖编译，不是应用启动、数据库、租户/角色/业务归属、审批执行或前端验收。

下一步需审阅并提供CRM/BPM业务表及Flowable引擎的迁移边界，现有application.yaml中database-schema-update:true不能替代可审阅迁移；旧JSS V001–V015保持不可变。只用任务归属MySQL13316的随机库和专用Redis16379验证组合profile的真实上下文、SQL、审核流与身份/租户/RBAC/负责人/团队边界。前端CRM40及依赖的完整BPM27原页应一起接通；完整原API、表单/审批/转派/回退/撤销不通过裁剪或只复制详情绕过。

当前没有迁移开发数据库、重新启动开发服务或部署，也没有将该编译记为真实业务通过。e78c46b536的171页工作台组合仍是单列的已验证基线；新profile与新迁移必须有自己的实际证据。

## 显式数据库迁移

V016增加全部21张CRM和8张BPM业务表，逐一对应原模型全部持久化字段及审计/tenant_id；BaseDO也受实际TenantDatabaseInterceptor过滤，不能因没有TenantBaseDO继承就漏列。LongListTypeHandler/StringListTypeHandler沿用逗号分隔LONGTEXT，Jackson3TypeHandler使用JSON；流程配置/表单原字符串使用LONGTEXT，流程ID为保留冒号/UUID的字符串列。BigDecimal列采用DECIMAL(38,12)，明确是新JSS存储规格，不冒称上游原DDL或前端大金额已全部验收。字段类型、处理器、来源SHA与实际引擎SQL来源见jss-crm-bpm-schema.json。

V017按顺序完整保留Flowable8.0.0官方Maven制品内common/identity/eventregistry/engine/history的五份MySQL建表SQL，共45表，未改写原SQL；来源资源路径、制品与脚本SHA均记录，附Apache2.0许可证。仅原生版本/计数元数据，不创建用户、流程模型、定义、业务实例、账号或授权。旧V001–V015保持不可变。应用当前schema-update配置仍须在实际引擎验证后关闭，不能把自动建表当迁移证据。

mysql_sandbox归属MySQL8.4.9:13316实际执行6组结构/存储检查全部通过：29模型精确列集、空表、45引擎表、四类8.0.0.0元数据、Long/CSV/FIND_IN_SET、Jackson字段字符串数组/规则JSON、UUID/定义版本、DECIMAL(38,12)与可空公海负责人。成功随机库jss_crm_bpm_verify_a7dfdae580b8清理残留0；首轮测试只因手写SQL嵌套JSON引号出错，改为原生JSON_ARRAY/JSON_OBJECT构造，失败日志crm-bpm-schema-first.log保留，所有该模式随机库残留0。

完整真实Flyway生命周期25组通过，日志.runtime/crm-bpm-flyway-first.log。含fresh V017、V015→V017、V017重复migrate/validate保留CRM和BPM Long/JSON记录，既有会员/经验/菜单/停用节点/套餐/授权及所有旧校验和断言保持；三个随机库均清理，模式残留0。旧V014专项现在用隔离资产migrate_through(14)，保留原maxVersion=14断言，避免把未来版本误当该阶段；没有删旧断言。init同时拒绝未托管CRM/BPM和大小写Flowable表，不自动接管。

本节证明数据库结构与升级生命周期，尚不证明真实MyBatis映射、Flowable实际启动/部署/运行、CRM合同回款联动、认证HTTP/租户/负责人/团队或设备。仅隔离测试库执行，没有升级开发库、启动开发服务或部署。
