# JSS 版本迁移

独立 Flyway 13.10.3 发布工具，历史表为 `flyway_jss_schema_history`，与现有 gym 迁移历史分开。Java 应用启动不运行迁移，也不执行开发 seed。

| 版本 | 内容 |
| --- | --- |
| V001 | 当前 11 张会员表；以仓库历史会员 DDL 为基础，补齐现有模型、社交注册空手机号及有效登录标识唯一约束 |
| V002 | 会员员工关联、绑定审计和三项 JSS 权限定义；不创建绑定、账号或角色授权 |
| V003 | 商品的九张当前模型表，含 SKU 属性/图片 JSON、逗号分隔配送类型、浏览记录和有效收藏唯一键 |
| V004 | 支付、退款、转账、通知、钱包、充值及支付演示 API 所需的 14 张表；不创建渠道、凭证或余额 |
| V005 | 装修模板/页面、文章/分类、轮播和客服会话/消息的七张表；不发布内容或创建会话 |
| V006 | 优惠券、满减、折扣、秒杀、拼团、砍价和积分活动的 16 张表；不创建活动或发券 |
| V007 | 前向补齐商品评价 `sku_properties` JSON；保留已有评价与 V003 校验和 |
| V008 | 购物车、订单/明细/日志、售后/日志、配送与交易配置、佣金的 15 张表；不创建业务数据或启用配置 |
| V009 | 商品与交易的日统计表，按租户/日期及商品建立有效快照唯一键；不生成统计数据 |
| V010 | 框架 system_notice 增加200字符备注，旧公告默认空备注；保留原标题/正文和历史迁移校验和 |
| V011 | infra_api_error_log处理人编号从int扩为可空bigint，保持默认0/NULL及原日志正文，匹配Long管理员编号 |
| V012 | 会员经验事件键按租户/会员/业务类型/编号唯一；保留旧经验记录并封存旧事件，自动奖励/撤销重投不重复变动 |
| V013 | 示例订单/提现的查询、创建、退款/转账六项隐藏按钮权限定义；不自动授予角色、员工或租户套餐，不重启用已有停用节点 |
| V014 | 收银台原提交动作的独立隐藏权限定义，不自动授予角色或套餐 |
| V015 | 客服未指定接收人两列允许NULL，保留既有会话消息与旧迁移校验和 |
| V016 | 全部21张CRM、8张BPM业务表；保留Long/CSV/JSON/原表单与流程ID，空业务数据 |
| V017 | Flowable8.0.0原生45表及原版本/计数元数据，不部署流程或创建引擎用户 |

先准备已有 system/infra 框架基座，再显式指定迁移连接：

示例订单/提现的交互接口现分别要求pay:demo-order:query/create/refund及pay:demo-withdraw:query/create/transfer。部署前先审阅并迁移/校验V013，再将经批准的节点加入对应租户套餐并用现有角色UI授予实际所需动作；工作台访问资格、查询能力均不自动包含资金动作。旧已停用或自定义同权限节点保留，逻辑删除历史不复活其旧角色关联。通知回调不要求交互权限，继续由原服务核验业务通知。不在开发库手工重放初始化SQL，也不自动扩大员工授权。

**当前经验幂等代码部署前须先备份并migrate/validate到V012。** 新表member_experience_event与实际经验变动同事务提交；不修改会员余额或旧经验记录，包括重复/已逻辑删除的旧记录。旧非ADMIN且非空业务编号按租户/会员/类型/区分大小写编号回填一次，requested_experience为NULL表示原始请求未知，只封存、不猜金额；ADMIN交互调级不按固定编号去重。新请求记录带符号原始值，重复且值不同拒绝，失败/外层回滚不会消费事件键。日志清理不应删除对应事件键，否则会重新开放已消费事件。V001–V011不改写，应用启动不自动迁移，本批没有升级开发库或运行jar。

包含 V010 的服务器代码读写公告时需要 remark 列，须先备份并 `migrate`/`validate` 到 V010，再部署新运行包。框架基座必须含 system_notice；此迁移不重放初始化SQL，不修改既有公告正文，也不在应用启动时自动执行。当前开发库/运行jar未由本批升级。

包含本批API日志处理代码须先备份并迁移/校验到V011；框架基座须含infra_api_access_log/infra_api_error_log。处理人DO本已使用Long，旧MySQL列和管理VO仍为int/Integer，V011只扩大处理人列，保留可空/默认0以及所有既有日志内容，VO同步Long。V001–V010不改写，实际演练含受管V010升级V011、旧最大int/NULL/0/负值保留及超JS安全整数的Long写入/重复迁移/validate。没有对开发库或运行服务执行此升级。

```powershell
$env:JSS_DB_HOST = '127.0.0.1'
$env:JSS_DB_PORT = '3306'
$env:JSS_DB_NAME = '明确的目标库名'
$env:JSS_DB_USER = '迁移账号'
$env:JSS_DB_PASSWORD = '通过安全环境配置提供的密码'
$env:JSS_MYSQL_HOME = 'C:\Program Files\MySQL\MySQL Server 8.4'
# Maven 不在 PATH 时，可用 JSS_MAVEN_COMMAND 指向本机 mvn/mvn.cmd。
python script/jss/database.py info
python script/jss/database.py init
python script/jss/database.py migrate
python script/jss/database.py validate
```

`init` 仅接管已存在框架基座、尚无会员/JSS/商城/支付表且没有 JSS 迁移历史的 schema，显式 baseline 到 0 后执行版本脚本。重复初始化、缺框架表或已有未托管业务表都会拒绝；已有会员/JSS 数据应先核对结构、历史和备份，不能靠自动 baseline、重放原初始化 SQL 或手工跳过版本接管。

`migrate`/`validate` 要求已有历史。自动 baseline、乱序迁移、clean 均禁用；CLI 不提供 repair、clean 或 seed。MySQL DDL 可能隐式提交，失败后应检查当前结构和 Flyway 历史，按审核后的前向迁移或备份恢复处理。后续追加新版本，不修改已应用的版本文件。

原 `sql/jss/001-identity.sql` 已移入 V002，Git 保留原文件历史；请统一通过本工具部署，避免业务库出现无版本记录的表。

## V018：完整ERP基础表

标准jss-mall构建现包含原ERP模块。V018按33份原ERP DO保留全部持久字段及继承审计/tenant_id，精确列集合见docs/jss-erp-schema.json。原BigDecimal用DECIMAL(38,12)，这是JSS的新建存储选择；不冒称上游DDL或完整业务精度验收。Long保持BIGINT，单据明细与关联编号添加租户前导索引。init拒绝未托管erp_表；V001–V017未改。归属MySQL随机库的33表实际DDL和全部列集合检查通过，随机库残留0；标准compile通过，未运行测试。按用户快速迁移优先级，完整Flyway生命周期、服务启动和ERP业务/RBAC/租户/库存财务深度回归待后续验收；未部署开发库。

## V019：完整HRM基础表

标准jss-mall构建纳入完整原HRM模块及已存在的BPM依赖。V019按50份原DO的544个顶层持久字段保留全部列及继承审计/tenant_id；嵌套Java记录按原Jackson3处理器使用JSON，原LongList/StringList处理器使用LONGTEXT保留CSV语义，不将内层字段展开为数据库列。时间域分别保留DATE/TIME/DATETIME，BigDecimal使用JSS明确选择的DECIMAL(38,12)。来源及字段/处理器/SQL类型完整对照见docs/jss-hrm-schema.json。归属MySQL随机库50表实际DDL与全部精确列集合、标准compile通过，随机库残留0，未运行单测或大型回归。init拒绝未托管hrm_表，V001–V018不变；完整生命周期、服务上下文和考勤/工资/社保/招聘/绩效流程、角色/租户、设备待后续验收。未部署到开发库。

## V020：完整FMS基础表

标准jss-mall已纳入原FMS及其锁定Aviator依赖，未升级版本。V020按30份DO的306个顶层持久字段保留全部列及审计/租户，原Jackson3列表使用JSON，不展开内层分录/辅助核算记录；BigDecimal使用JSS选择的DECIMAL(38,12)。完整来源、字段与处理器映射见docs/jss-fms-schema.json。归属MySQL随机库的30表DDL/精确列集合及标准compile通过，随机库残留0；未运行单测或大型回归。init拒绝未托管fms_表，V001–V019未改。完整生命周期/服务上下文、账套成员授权与账务全过程、外部文件及设备后续单列验收；未部署开发库。

## V021：完整 MES 原生业务结构（快速迁移批次）

标准 jss-mall 构建与 yudao-server 已纳入完整 yudao-module-mes。V021 保留 133 张原生 DO 表、1379 个外层持久字段以及 BaseDO 审计、租户字段；逐表来源哈希及类型映射见 docs/jss-mes-schema.json。BigDecimal 使用 DECIMAL(38,12)；IntegerListTypeHandler 按原生 CSV LONGTEXT 保留。

本批 reactor compile（跳过测试）退出 0；归属 MySQL 8.4.9:13316 的随机隔离库 jss_mes_verify_837d4a91a21a 实际建表与全部列核对通过，finally 删除后残留 0。未改开发库；完整 Flyway、应用上下文、真实生产/质量/库存流程及权限业务验收按用户快速迁移优先级另列后续任务。

## V022：完整 WMS 独立业务结构（快速迁移批次）

标准 jss-mall 构建与 yudao-server 已纳入完整 yudao-module-wms。V022 保留 16 张原生 DO 表、134 个持久字段以及 BaseDO 审计和租户列；来源哈希与 SQL 类型映射见 docs/jss-wms-schema.json，BigDecimal 为 DECIMAL(38,12)。WMS 的库存/入出库/调拨/盘点域和 MES 仓储域独立保留，不合并表、API 或状态流转。

跳过测试的 reactor compile 退出 0；归属 MySQL 8.4.9:13316 随机库 jss_wms_verify_eb431e51e274 实际建表和全部列核对通过，finally 删除后残留 0。未改开发库；完整 Flyway/应用上下文、真实单据和库存数量价格链、权限/租户及设备验收后续单列。

## V023：完整 OA 原生业务结构（快速迁移准备）

标准 jss-mall 与 yudao-server 已纳入完整 OA 模块及原 system/infra/BPM 依赖。V023保留48原生表、506外层持久字段、审计/租户列与32个Jackson3 JSON字段；连接配置、邮件附件、报销及报告的内层DTO继续保存在父JSON中，不展开为额外SQL列。来源哈希与逐字段映射见 docs/jss-oa-schema.json；BigDecimal 明确使用 DECIMAL(38,12)。

跳过测试的 reactor compile 退出0；归属 MySQL8.4.9:13316 随机库 jss_oa_verify_f875e51f7551 实际建表和全部列核对通过，finally删除后残留0。开发库未改。OA106前端页尚在下一批，完整 Flyway/应用上下文、真实审批/共享云盘/邮箱提供商、权限归属/租户和设备仍待验。

## V024：完整 PMS 原生业务结构（快速迁移准备）

标准 jss-mall 与 yudao-server 纳入完整 PMS 模块及原 system/infra 依赖。V024保留33原生表、220外层持久字段与审计/租户列，8个Jackson3字段为JSON，knowledge_document.labelIds按原LongListTypeHandler保留CSV LONGTEXT。原内层文档/状态/看板模板留在父JSON，不扩展为SQL列。来源哈希与逐字段映射见docs/jss-pms-schema.json。

跳过测试的reactor compile退出0；归属MySQL8.4.9:13316随机库jss_pms_verify_822607eb00da实际DDL/全部列核对通过，finally删除后残留0，未改开发库。前端PMS33页仍在后续批次，完整Flyway/context、实际项目/工时/事项与知识库权限及分享、租户和设备另列待验。

## V025：完整 IM 原生业务结构（快速迁移准备）

标准jss-mall与yudao-server纳入完整IM原模块，保留system/infra/Redis、原敏感词和拼音依赖。V025是17张@TableName持久表、146外层持久字段和审计/租户列；29个dataobject文件中的12种消息内容DTO继续随原String消息内容使用，不扩成表。ImGroupMessageDO.readCount明确exist=false，保持为动态回算字段，未错误建列；四个LongListTypeHandler按原CSV LONGTEXT存储。来源哈希及排除清单见docs/jss-im-schema.json。

跳过测试的reactor compile退出0；归属MySQL8.4.9:13316随机库jss_im_verify_979484c87e64实际DDL/全部列核对通过，finally清理后残留0，开发库未改。IM44前端页仍在后续批次；完整Flyway/context、实时协议/未读撤回/群与频道/离线缓存、RTC和LiveKit、归属/租户及设备另列待验。

## V026：完整 IoT 原生关系结构（快速迁移准备）

标准jss-mall聚合纳入iot-biz、iot-core和独立iot-gateway，yudao-server只依赖原业务模块，网关部署边界保持。V026包含15张原@TableName表、141外层持久字段、8个Jackson3 JSON字段及5个原CSV LongList/LongSet/IntegerList字段。原设备消息/属性继续由TDengine或IoTDB存储，20个物模型/规则配置内层类继续留在父JSON，不误扩成MySQL表。来源SHA和逐字段映射见docs/jss-iot-schema.json。

server与原IoT gateway reactor跳过测试的compile退出0；归属MySQL8.4.9:13316随机库jss_iot_verify_d52bc0169af1实际DDL、全部列/列类型核对通过，finally删除后残留0，开发库未改。前端43源页正在下一完整批次迁入。完整Flyway/context、时序库提供商、消息/网关协议、真实设备及OTA/权限归属/租户仍待验，没有启动或部署网关。

## V027 / V028：完整 AI 与公众号原生结构（快速迁移准备）

标准jss-mall和yudao-server纳入完整原yudao-module-ai与yudao-module-mp，保留模型/向量库/工作流及微信提供商依赖。V027保留14张原AI表、149外层持久字段、4JSON和5CSV；V028保留8张原公众号表、118外层持久字段、3JSON和1CSV。内层工具/模型/文章配置继续使用原类型处理器，不展开为SQL列；来源SHA与逐字段映射见docs/jss-ai-schema.json、docs/jss-mp-schema.json。

包含完整AI/MP的server reactor跳过测试compile退出0。归属MySQL8.4.9:13316随机库jss_ai_verify_ef5402488627和jss_mp_verify_c0840cb62861实际DDL、全部列与类型核对通过，finally清理后均残留0。开发库未改，没有启动模型生成、消息发送或公众号发布；完整Flyway/context、实际模型/向量库/SSE、微信素材/消息/发布与归属/租户及设备另列待验。前端下一62源页整组正在迁入，仅做必要类型/资源/来源检查。
