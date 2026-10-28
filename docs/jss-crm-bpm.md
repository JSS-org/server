# CRM与BPM统一后端接入

当前新增可选Maven profile `jss-crm`，根reactor同时包含yudao-module-bpm和yudao-module-crm，yudao-server同时依赖两个原模块。CRM原POM对BPM的依赖保持，BPM沿用依赖管理中的Flowable8.0.0实际process/actuator starter，没有删除审批依赖或加入空实现；仍使用原同一YudaoServerApplication和统一身份链。

与已接入商城一起构建：

```powershell
.runtime/maven-integration.cmd -B -ntp '-Pjss-mall,jss-crm' -pl yudao-server -am compile '-DskipTests=true' '-Dspotless.skip=true'
```

2026-10-28首次真实编译成功，32模块全部SUCCESS，38.481秒；BPM23.691秒、CRM6.241秒，二者均实际在reactor中编译，服务器也因依赖变化重新编译。日志.runtime/crm-bpm-compile-first.log、退出标记0。此阶段只验证完整依赖编译，不是应用启动、数据库、租户/角色/业务归属、审批执行或前端验收。

下一步需审阅并提供CRM/BPM业务表及Flowable引擎的迁移边界，现有application.yaml中database-schema-update:true不能替代可审阅迁移；旧JSS V001–V015保持不可变。只用任务归属MySQL13316的随机库和专用Redis16379验证组合profile的真实上下文、SQL、审核流与身份/租户/RBAC/负责人/团队边界。前端CRM40及依赖的完整BPM27原页应一起接通；完整原API、表单/审批/转派/回退/撤销不通过裁剪或只复制详情绕过。

当前没有迁移开发数据库、重新启动开发服务或部署，也没有将该编译记为真实业务通过。e78c46b536的171页工作台组合仍是单列的已验证基线；新profile与新迁移必须有自己的实际证据。
