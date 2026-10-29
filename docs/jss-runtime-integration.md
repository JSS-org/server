# 完整运行组合与外部时序存储边界

当前 jss-mall profile 包含 system/infra/gym/member/JSS、商城 pay/product/promotion/trade/statistics，以及完整 CRM/BPM、ERP、HRM、FMS、MES、WMS、OA、PMS、IM、IoT biz、AI 和公众号模块。各模块原页面、API、持久化模型和外部提供商能力继续保留；一次应用启动不等于全部业务或提供商验收。

2026-10-29 在归属 MySQL 13316 的随机临时库执行 V001–V029 后首次启动，Spring/Tomcat 完成装配，但默认 IoT TDengine 未配置数据源，原 IotStorageInitRunner 调用 System.exit(1) 终止宿主 JVM。失败日志保留于 .runtime/jss-http/jss_http_verify_f26036117427，未运行 HTTP 用例，临时库残留0。

修复提交 fb2a32fca1 增加显式 yudao.iot.storage.type=disabled 模式：允许在尚未接入时序提供商时运行其他模块，时序 DAO 不访问任何数据源，所有属性历史、日志查询、统计、建表和写入均返回业务码1050000000“IoT 时序存储未启用”。不返回伪造空记录或成功，不回退主库。依赖时序存储的物模型等操作仍会明确失败；本模式不表示 IoT 功能已验收。

默认仍为 tdengine，也支持原 iotdb；两种真实模式继续在启动时检查对应数据源并执行原初始化，配置缺失或初始化失败会由 Spring 正常中止启动并清理资源，避免直接结束 JVM。启动真正 IoT 服务前必须提供所选时序数据源及真实提供商。没有修改当前开发/生产环境的模式或连接配置。

独立 IoT Quartz 调度器现在遵循 spring.quartz.auto-startup，关闭时可保留任务定义但不执行；应用关闭调用 SchedulerFactoryBean.destroy() 释放调度线程。未设置启动属性时仍保留原自动启动行为。

3项定向检查覆盖默认缺数据源中止、显式禁用不访问提供商，以及所有时序 DAO 读写明确拒绝。证据 .runtime/iot-explicit-storage-focused-result.json 和 .runtime/iot-explicit-storage-focused.log。没有真实 TDengine/IoTDB、网关、设备或定时任务执行证据。

现有隔离 HTTP 脚本增加 --smoke，仅选择一个最新整合入口，复用完整生产应用、真实 MySQL/Redis、认证及租户过滤链，不执行浏览器或原整组用例。它在独占随机库中初始化当前 JSS/Gym 迁移、建立测试身份，使用明确禁用时序存储及停用 Quartz 调度的测试配置；所有测试参数、反馈和私有图片只存在于随机库，结束后删除随机库并关闭归属 Redis。

```powershell
python script/jss/verify_workspace_http.py --smoke
```

必要本地前提和归属校验沿用原脚本；不允许把随机测试库、测试身份或测试配置当作部署配置。每次运行目录记录脱敏 Maven 输出、JUnit、验证信息及清理结果；失败记录与后续成功记录分别保留。最新入口结果以对应运行目录为准，不能用它覆盖历史浏览器组合验收或替代完整 Flyway 升级生命周期、各模块业务/角色/租户、真实外部提供商和原生设备验收。

2026-10-29最终定向入口通过：083fad595a，.runtime/jss-http/jss_http_verify_d4a3b3860663/verification.json；标准 jss-mall 完整 reactor 下只执行1项HTTP入口，78.052秒、失败/错误/跳过均0。已完成 V001–V029 新库初始化及配套 Gym 迁移，真实生产应用装配并启动；实际 IoT DAO 返回未启用，独立 Quartz 保持待机。匿名公开配置正确读取租户1/91001固定前缀并隐藏不可见电话；会员真实登录、multipart图片上传、反馈提交、本人读取和原字节私有下载通过；其他会员和租户不可读取，附件不能重复领取，匿名访问反馈被拒绝。

测试过程中的另一次失败记录 .runtime/jss-http/jss_http_verify_8fc51b90e914 保留：应用已启动，调度器探针误从全局 Quartz 仓库查找独立 SchedulerFactoryBean 的对象；改为检查实际 Spring Bean 的调度器，未放松待机断言。首轮提供商缺失和探针失败均不登记为HTTP通过。三次尝试的临时库均残留0，归属 Redis 进程已确认结束；不重启现有服务、不部署、不录入生产公开参数，不运行浏览器或整组回归。证据不包含真实 TDengine/IoTDB、最新前端三端资源、完整 Flyway 升级/回滚或所有业务流程。
