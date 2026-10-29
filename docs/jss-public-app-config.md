# 应用公开展示配置

GET /app-api/infra/config/app-public 匿名读取当前租户的固定公开展示字段。服务端从TenantContextHolder确定租户，调用既有ConfigService；不接受配置键参数，不返回ConfigDO、权限或其他参数。只有visible=true的值可返回，不可见和不存在的键均视为未提供。

通过现有“基础设施 → 参数配置”维护，继续使用原infra:config:create/update等权限，不增加授权或样例数据。infra_config仍是平台共享参数表；当前租户公开字段使用下列固定键名，T为租户编号：

| 键名（前缀均为jss.app.public.T.） | 值 |
| --- | --- |
| name | 应用名称，最多80字符，未提供时为JSS |
| logo | HTTP(S)图片地址或本地/static/路径 |
| support-phone | 客服电话，支持数字、+、空格、括号和短横线 |
| support-email | 客服邮箱文本 |
| support-qr | 客服二维码图片地址 |
| service-hours | 对外服务时间文本 |
| copyright / copytime | 两行版权展示文本 |
| static-base-url | HTTP(S)静态资源基地址，未提供时为空 |
| banner.1.image / banner.2.image / banner.3.image | 最多三个轮播图地址；空值不展示 |
| banner.1.link / banner.2.link / banner.3.link | 注册的/pages/内部地址或HTTPS地址 |
| agreement / privacy | 正式协议的HTTPS链接；或明确填写source-template以展示保留的原版模板 |
| agreement-updated-at / privacy-updated-at | 启用原版模板时的更新日期展示文本 |

每个值继续受既有参数500字符上限约束。图片链接拒绝用户密码嵌入和活动脚本协议；正式协议链接只接受HTTPS。外链在前端还受既有VITE_WEBVIEW_HOSTS域名白名单约束。source-template是配置人员主动选用原内容，不表示本次迁移已提供或审批正式协议。

没有写入实际企业名称、电话、二维码、图片或协议；缺少这些数据时前端显示暂未提供/发布。无需新DDL、角色授予或第三方服务。本次未部署或修改开发库。

4项定向Mockito检查及标准jss-mall完整server compile通过：固定公开字段、不可见数据拒绝、URI/电话与协议形态、租户前缀隔离。证据.runtime/public-app-config-focused-result.json及public-app-config-server-compile.log。实际参数存取与HTTP/租户拦截器和设备展示仍需分别验收，检查不替代这些证据。
