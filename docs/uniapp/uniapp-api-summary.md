# 健身门店小程序接口总表

> 统计日期：2026-10-25。依据 [接口设计文档](./uniapp-api.md) 汇总；参数、响应和权限细节见原文。

**合计 135 个已明确方法和路径的接口：小程序侧 133 个，设备集成侧 2 个。**

## 统计口径

- 按“HTTP 方法 + 完整路径”去重；同一路径的 GET 和 POST 分别计数。
- 员工、管理者、总部共用的工作台接口只计一次；示例请求及文中重复引用不重复计数。
- 包含拟新增接口、已有加盟接口及 3 个可复用的认证源码接口；不代表全部已实现或上线。
- 未列具体路径的 system 后台认证、支付/自动续费通知、电子合同通知不计入本次总数；作为后续集成补充项。
- 已有管理后台接口在设计文档中仅作为复用参考，不额外计入小程序接口总数。
- 公开素材辅助读取 `GET /app-api/gym/public-media/{id}/content` 支撑总表7–11的Media URL，不新增135个目标条目；实际业务引用授权与普通会员文件入口分开。

## 数量汇总

| 分类 | 数量 | 说明 |
| --- | ---: | --- |
| 认证 | 3 | 微信登录、刷新令牌、退出登录；模块已装配，真实微信登录待联调 |
| 公共及会员端 | 77 | 门店展示、会员业务、互动、文件及通知，包含已有加盟接口 |
| 工作台 | 53 | 员工、管理者及总部共用，按权限限定数据范围 |
| 设备集成 | 2 | 服务端接收门禁事件和设备执行回执，小程序不直接调用 |
| **合计** | **135** | **按方法与路径去重** |

当前实现状态：新增实现并通过本地测试 **83** 个，复用源码路由 **4** 个（认证 3、加盟 1），尚无目标路由 **48** 个。源码映射共 **87 / 135**，不代表全部上线；详细证据与暂缓范围见 [实施进度与第三方边界](./uniapp-implementation.md)。

请求方法：GET **81** 个，POST **50** 个，PATCH **2** 个，PUT **1** 个，DELETE **1** 个。

## 接口总表

| 序号 | 分类 | 方法 | 完整接口路径 | 功能描述 | 状态 |
| ---: | --- | --- | --- | --- | --- |
| 1 | 认证 | POST | `/app-api/member/auth/weixin-mini-app-login` | 微信手机号授权登录，获取会员访问令牌和刷新令牌 | 已装配，渠道待联调 |
| 2 | 认证 | POST | `/app-api/member/auth/refresh-token` | 刷新会员登录令牌 | 复用认证源码，待运行联调 |
| 3 | 认证 | POST | `/app-api/member/auth/logout` | 退出会员登录 | 复用认证源码，待运行联调 |
| 4 | 公共及会员端 | POST | `/app-api/gym/me/profile-initializations` | 首次登录关联或创建健身会员档案，记录潜客来源和邀请关系 | 已实现，本地测试通过 |
| 5 | 公共及会员端 | GET | `/app-api/gym/me/context` | 获取当前会员业务身份和默认门店 | 已实现，本地测试通过 |
| 6 | 工作台 | GET | `/admin-api/gym/mobile/context` | 获取员工身份、角色、权限及可访问门店 | 已实现，本地测试通过 |
| 7 | 公共及会员端 | GET | `/app-api/gym/stores` | 查询公开营业门店，支持关键词和位置筛选 | 已实现，本地测试通过 |
| 8 | 公共及会员端 | GET | `/app-api/gym/stores/{id}` | 获取门店介绍、地址、营业时间、环境素材及客服信息 | 已实现，本地测试通过 |
| 9 | 公共及会员端 | GET | `/app-api/gym/stores/{id}/home` | 获取首页聚合数据，包括门店、人数、功能开关和公告 | 已实现，本地测试通过 |
| 10 | 公共及会员端 | GET | `/app-api/gym/stores/{id}/occupancy` | 获取实时在店人数、当天入场次数及数据更新时间 | 已实现，本地测试通过 |
| 11 | 公共及会员端 | GET | `/app-api/gym/client-settings` | 获取入口封面、App 下载福利和加盟展示配置 | 已实现，本地测试通过 |
| 12 | 公共及会员端 | POST | `/app-api/gym/stores/{id}/camera-play-sessions` | 为具备资格的会员创建监控短时播放会话 | 待开发；外部接入暂缓 |
| 13 | 公共及会员端 | POST | `/app-api/gym/franchise-inquiries` | 提交加盟意向，返回匿名受理回执 | 已有接口 |
| 14 | 公共及会员端 | GET | `/app-api/gym/card-products` | 查询门店可售会员卡商品 | 已实现，本地测试通过 |
| 15 | 公共及会员端 | GET | `/app-api/gym/course-products` | 查询门店或教练可售私教商品 | 已实现，本地测试通过 |
| 16 | 公共及会员端 | GET | `/app-api/gym/products/{id}` | 获取商品详情、价格及购买限制 | 已实现，本地测试通过 |
| 17 | 公共及会员端 | GET | `/app-api/gym/me/cards` | 查询本人的会员卡列表 | 已实现，本地测试通过 |
| 18 | 公共及会员端 | GET | `/app-api/gym/me/cards/{id}` | 获取本人会员卡详情、有效期和剩余次数 | 已实现，本地测试通过 |
| 19 | 公共及会员端 | GET | `/app-api/gym/me/course-packs` | 查询本人的私教课包及剩余、预占、可用课时 | 已实现，本地测试通过 |
| 20 | 公共及会员端 | POST | `/app-api/gym/orders` | 按商品或有效报价创建待支付订单 | 设计待开发 |
| 21 | 公共及会员端 | GET | `/app-api/gym/me/orders` | 查询本人的订单列表 | 设计待开发 |
| 22 | 公共及会员端 | GET | `/app-api/gym/me/orders/{id}` | 获取本人订单支付及权益履约状态 | 设计待开发 |
| 23 | 公共及会员端 | POST | `/app-api/gym/orders/{id}/payment-sessions` | 创建订单支付会话，返回小程序支付参数 | 待开发；外部接入暂缓 |
| 24 | 公共及会员端 | POST | `/app-api/gym/orders/{id}/cancellations` | 取消本人未支付订单 | 设计待开发 |
| 25 | 公共及会员端 | POST | `/app-api/gym/orders/{id}/refund-applications` | 提交订单退款申请，由服务端计算可退金额 | 设计待开发 |
| 26 | 公共及会员端 | GET | `/app-api/gym/me/refund-applications/{id}` | 查询本人退款申请审核和退款进度 | 设计待开发 |
| 27 | 公共及会员端 | GET | `/app-api/gym/subscription-products` | 查询门店连续包月商品及续费说明 | 已实现，本地测试通过 |
| 28 | 公共及会员端 | POST | `/app-api/gym/subscriptions` | 创建连续包月订阅并发起渠道签约 | 待开发；外部接入暂缓 |
| 29 | 公共及会员端 | GET | `/app-api/gym/me/subscriptions` | 查询本人的连续包月订阅列表 | 设计待开发 |
| 30 | 公共及会员端 | GET | `/app-api/gym/me/subscriptions/{id}` | 获取订阅详情、扣费周期及解约状态 | 设计待开发 |
| 31 | 公共及会员端 | POST | `/app-api/gym/subscriptions/{id}/termination-requests` | 提交连续包月解约请求 | 待开发；外部接入暂缓 |
| 32 | 公共及会员端 | POST | `/app-api/gym/coupon-verification-previews` | 校验团购券并生成核销预览，不直接消耗券 | 设计待开发 |
| 33 | 公共及会员端 | POST | `/app-api/gym/coupon-verifications` | 确认核销团购券，渠道成功后发放权益 | 设计待开发 |
| 34 | 公共及会员端 | GET | `/app-api/gym/me/coupon-verifications/{id}` | 查询本人团购券核销与权益发放结果 | 设计待开发 |
| 35 | 公共及会员端 | GET | `/app-api/gym/coaches` | 查询门店公开教练列表 | 已实现，本地测试通过 |
| 36 | 公共及会员端 | GET | `/app-api/gym/coaches/{id}` | 获取教练介绍、照片和公开资质 | 已实现，本地测试通过 |
| 37 | 公共及会员端 | GET | `/app-api/gym/coaches/{id}/sessions` | 查询教练可预约课程时段及剩余名额 | 已实现，本地测试通过 |
| 38 | 公共及会员端 | POST | `/app-api/gym/bookings` | 创建本人私教预约并预占课时 | 已实现，本地测试通过 |
| 39 | 公共及会员端 | GET | `/app-api/gym/me/bookings` | 查询本人的预约列表 | 已实现，本地测试通过 |
| 40 | 公共及会员端 | GET | `/app-api/gym/me/bookings/{id}` | 获取本人预约详情和签到签课状态 | 已实现，本地测试通过 |
| 41 | 公共及会员端 | POST | `/app-api/gym/bookings/{id}/cancellations` | 在允许期限内取消本人预约并释放预占课时 | 已实现，本地测试通过 |
| 42 | 公共及会员端 | GET | `/app-api/gym/me/checkins` | 查询本人的入场签到记录 | 已实现，本地测试通过 |
| 43 | 公共及会员端 | GET | `/app-api/gym/me/attendance-summary` | 获取本人月度签到次数、活跃天数及门店排名 | 已实现，本地测试通过 |
| 44 | 公共及会员端 | POST | `/app-api/gym/lesson-confirmations` | 会员扫码签课，校验签到后幂等扣减课时 | 已实现，本地测试通过 |
| 45 | 公共及会员端 | GET | `/app-api/gym/me/lesson-records` | 查询本人的签课和课消记录 | 已实现，本地测试通过 |
| 46 | 公共及会员端 | GET | `/app-api/gym/me/profile` | 获取本人昵称、头像、脱敏手机号和人脸状态 | 已实现，本地测试通过 |
| 47 | 公共及会员端 | PATCH | `/app-api/gym/me/profile` | 修改本人昵称和头像，不允许修改手机号 | 已实现，本地测试通过 |
| 48 | 公共及会员端 | POST | `/app-api/gym/me/face-enrollments` | 提交经身份核验的人脸照片登记或更新 | 待开发；外部接入暂缓 |
| 49 | 公共及会员端 | GET | `/app-api/gym/me/face-enrollments/{id}` | 查询本人人脸登记审核及同步结果 | 待开发；外部接入暂缓 |
| 50 | 公共及会员端 | GET | `/app-api/gym/me/contracts` | 查询本人待签和历史合同列表 | 已实现，本地测试通过 |
| 51 | 公共及会员端 | GET | `/app-api/gym/me/contracts/{id}` | 获取本人合同详情及原件、签署件信息 | 已实现，本地测试通过 |
| 52 | 公共及会员端 | POST | `/app-api/gym/contracts/{id}/sign-sessions` | 为本人合同创建电子签署会话 | 待开发；外部接入暂缓 |
| 53 | 公共及会员端 | GET | `/app-api/gym/me/body-measurements` | 查询本人的历史体测记录 | 已实现，本地测试通过 |
| 54 | 公共及会员端 | GET | `/app-api/gym/me/body-measurements/{id}` | 获取本人体测指标及报告详情 | 已实现，本地测试通过 |
| 55 | 公共及会员端 | GET | `/app-api/gym/me/training-plans` | 查询本人的当前及历史训练计划 | 已实现，本地测试通过 |
| 56 | 公共及会员端 | GET | `/app-api/gym/me/training-plans/{id}` | 获取本人训练计划正文和附件 | 已实现，本地测试通过 |
| 57 | 公共及会员端 | GET | `/app-api/gym/me/fitness-summary` | 获取本人运动档案摘要、最新体测和累计运动数据 | 已实现，本地测试通过 |
| 58 | 公共及会员端 | GET | `/app-api/gym/me/points` | 获取本人积分余额及累计收支 | 设计待开发 |
| 59 | 公共及会员端 | GET | `/app-api/gym/me/point-records` | 查询本人积分获取、消费和返还流水 | 设计待开发 |
| 60 | 公共及会员端 | POST | `/app-api/gym/me/invitations` | 生成本人邀请推广码和分享路径 | 设计待开发 |
| 61 | 公共及会员端 | GET | `/app-api/gym/point-products` | 查询门店可用积分兑换的商品 | 设计待开发 |
| 62 | 公共及会员端 | POST | `/app-api/gym/point-redemptions` | 提交积分兑换，扣减积分并发起会员卡发放 | 设计待开发 |
| 63 | 公共及会员端 | GET | `/app-api/gym/me/point-redemptions/{id}` | 查询本人积分兑换及发卡结果 | 设计待开发 |
| 64 | 公共及会员端 | POST | `/app-api/gym/feedbacks` | 提交对门店匿名的反馈、评分或建议 | 已实现，本地测试通过 |
| 65 | 公共及会员端 | GET | `/app-api/gym/me/feedbacks` | 查询本人反馈和门店回复 | 已实现，本地测试通过 |
| 66 | 公共及会员端 | GET | `/app-api/gym/activities` | 查询门店公开活动列表 | 已实现，本地测试通过 |
| 67 | 公共及会员端 | GET | `/app-api/gym/activities/{id}` | 获取活动详情、名额及报名截止时间 | 已实现，本地测试通过 |
| 68 | 公共及会员端 | POST | `/app-api/gym/activities/{id}/registrations` | 为当前会员报名活动 | 已实现，本地测试通过 |
| 69 | 公共及会员端 | DELETE | `/app-api/gym/activities/{id}/registrations/me` | 取消本人活动报名并释放名额 | 已实现，本地测试通过 |
| 70 | 公共及会员端 | GET | `/app-api/gym/me/activity-registrations` | 查询本人活动报名记录 | 已实现，本地测试通过 |
| 71 | 公共及会员端 | POST | `/app-api/gym/qr-resolutions` | 解析服务端认可的器械二维码，识别门店和器械 | 已实现，本地测试通过 |
| 72 | 公共及会员端 | GET | `/app-api/gym/equipment/{id}/tutorials` | 获取器械图文和视频教学 | 已实现，本地测试通过 |
| 73 | 公共及会员端 | POST | `/app-api/gym/files` | 上传会员头像、人脸或反馈图片等受控文件 | 已实现，本地测试通过 |
| 74 | 工作台 | POST | `/admin-api/gym/mobile/files` | 上传教练资料、体测报告、训练计划或合同文件 | 已实现，本地测试通过 |
| 75 | 公共及会员端 | GET | `/app-api/gym/files/{id}/content` | 下载本人有权访问的文件内容 | 已实现，本地测试通过 |
| 76 | 工作台 | GET | `/admin-api/gym/mobile/files/{id}/content` | 下载工作台业务权限范围内的文件内容 | 已实现，本地测试通过 |
| 77 | 工作台 | GET | `/admin-api/gym/mobile/dashboard` | 获取本人或授权门店的业绩、签到、签课和待办概览 | 已实现，本地测试通过 |
| 78 | 工作台 | GET | `/admin-api/gym/mobile/bookings` | 查询本人授课或授权门店的预约列表 | 已实现，本地测试通过 |
| 79 | 工作台 | GET | `/admin-api/gym/mobile/bookings/{id}` | 获取权限范围内的会员预约详情 | 已实现，本地测试通过 |
| 80 | 工作台 | GET | `/admin-api/gym/mobile/checkins` | 查询负责会员或授权门店的入场签到记录 | 已实现，本地测试通过 |
| 81 | 工作台 | GET | `/admin-api/gym/mobile/lesson-records` | 查询本人授课或授权范围内的签课记录 | 已实现，本地测试通过 |
| 82 | 工作台 | POST | `/admin-api/gym/mobile/lesson-qr-codes` | 为指定课程生成短时有效的签课二维码 | 已实现，本地测试通过 |
| 83 | 工作台 | GET | `/admin-api/gym/mobile/me/coach-profile` | 获取教练本人的公开资料 | 已实现，本地测试通过 |
| 84 | 工作台 | PATCH | `/admin-api/gym/mobile/me/coach-profile` | 修改教练本人的照片、介绍、特长和资质 | 已实现，本地测试通过 |
| 85 | 工作台 | POST | `/admin-api/gym/mobile/course-sessions` | 创建私教课程排期并校验教练时间冲突 | 已实现，本地测试通过 |
| 86 | 工作台 | POST | `/admin-api/gym/mobile/bookings` | 为负责会员或授权门店会员创建预约 | 已实现，本地测试通过 |
| 87 | 工作台 | GET | `/admin-api/gym/mobile/members` | 查询权限范围内会员，支持潜客、私教及临期权益筛选 | 已实现，本地测试通过 |
| 88 | 工作台 | GET | `/admin-api/gym/mobile/members/{id}` | 获取授权会员资料及当前门店权益摘要 | 已实现，本地测试通过 |
| 89 | 工作台 | GET | `/admin-api/gym/mobile/members/{id}/follow-ups` | 查询会员或潜在客户的跟进记录 | 已实现，本地测试通过 |
| 90 | 工作台 | POST | `/admin-api/gym/mobile/members/{id}/follow-ups` | 新增跟进记录和下次联系时间 | 已实现，本地测试通过 |
| 91 | 工作台 | PUT | `/admin-api/gym/mobile/members/{id}/assignments` | 分配或调整会员在指定门店的负责员工 | 已实现，本地测试通过 |
| 92 | 工作台 | POST | `/admin-api/gym/mobile/member-conversations` | 创建与授权会员的站内私信会话 | 设计待开发 |
| 93 | 工作台 | GET | `/admin-api/gym/mobile/member-conversations/{id}/messages` | 查询员工参与且有权访问的私信消息 | 设计待开发 |
| 94 | 工作台 | POST | `/admin-api/gym/mobile/member-conversations/{id}/messages` | 向授权会员发送站内私信 | 设计待开发 |
| 95 | 公共及会员端 | GET | `/app-api/gym/me/conversations` | 查询会员本人的站内会话及未读摘要 | 设计待开发 |
| 96 | 公共及会员端 | GET | `/app-api/gym/me/conversations/{id}/messages` | 查询本人会话消息 | 设计待开发 |
| 97 | 公共及会员端 | POST | `/app-api/gym/me/conversations/{id}/messages` | 会员在本人会话中发送消息 | 设计待开发 |
| 98 | 工作台 | GET | `/admin-api/gym/mobile/body-measurements` | 查询授权会员的体测记录 | 已实现，本地测试通过 |
| 99 | 工作台 | POST | `/admin-api/gym/mobile/body-measurements` | 为授权会员录入体测指标和报告 | 已实现，本地测试通过 |
| 100 | 工作台 | GET | `/admin-api/gym/mobile/training-plans` | 查询授权会员的训练计划 | 已实现，本地测试通过 |
| 101 | 工作台 | POST | `/admin-api/gym/mobile/training-plans` | 为授权会员创建训练计划并上传附件 | 已实现，本地测试通过 |
| 102 | 工作台 | GET | `/admin-api/gym/mobile/contracts` | 查询授权会员的合同记录 | 已实现，本地测试通过 |
| 103 | 工作台 | POST | `/admin-api/gym/mobile/contracts` | 基于会员真实卡或课包权益发放合同 | 已实现，本地测试通过 |
| 104 | 工作台 | POST | `/admin-api/gym/mobile/card-issue-applications` | 提交会员卡或私教卡发放审批申请 | 设计待开发 |
| 105 | 工作台 | GET | `/admin-api/gym/mobile/reports/sales` | 查询销售明细及会员卡、私教销售金额汇总 | 已实现，本地测试通过 |
| 106 | 工作台 | GET | `/admin-api/gym/mobile/reports/performance` | 按员工或门店统计业绩、排名、均值及课消 | 已实现，本地测试通过 |
| 107 | 工作台 | GET | `/admin-api/gym/mobile/reports/member-summary` | 统计授权门店会员、私教会员、新客和临期权益人数 | 已实现，本地测试通过 |
| 108 | 工作台 | GET | `/admin-api/gym/mobile/me/payrolls` | 查询本人已发布工资表列表 | 已实现，本地测试通过 |
| 109 | 工作台 | GET | `/admin-api/gym/mobile/me/payrolls/{id}` | 获取本人工资明细及计算项目 | 已实现，本地测试通过 |
| 110 | 工作台 | GET | `/admin-api/gym/mobile/payrolls` | 查询授权门店工资表，支持月份和员工筛选 | 已实现，本地测试通过 |
| 111 | 工作台 | GET | `/admin-api/gym/mobile/payrolls/{id}` | 获取授权范围内的员工工资明细 | 已实现，本地测试通过 |
| 112 | 工作台 | GET | `/admin-api/gym/mobile/approvals` | 查询本人申请、待审批或已处理审批列表 | 已实现，本地测试通过 |
| 113 | 工作台 | GET | `/admin-api/gym/mobile/approvals/{id}` | 获取审批详情、业务摘要及步骤记录 | 已实现，本地测试通过 |
| 114 | 工作台 | GET | `/admin-api/gym/mobile/approval-options` | 获取当前业务可用的审批类型和模板 | 已实现，本地测试通过 |
| 115 | 工作台 | POST | `/admin-api/gym/mobile/approvals/{id}/decisions` | 当前步骤指定审批人提交通过或拒绝决定 | 已实现，本地测试通过 |
| 116 | 工作台 | POST | `/admin-api/gym/mobile/course-price-applications` | 提交私教产品改价申请 | 设计待开发 |
| 117 | 工作台 | POST | `/admin-api/gym/mobile/course-quotes` | 依据商品现价或有效审批生成私教报价分享链接 | 设计待开发 |
| 118 | 公共及会员端 | GET | `/app-api/gym/course-quotes/{id}` | 会员查看适用于自己的私教报价和有效期 | 设计待开发 |
| 119 | 工作台 | GET | `/admin-api/gym/mobile/feedbacks` | 查询门店匿名反馈、评分和建议，不暴露会员身份 | 已实现，本地测试通过 |
| 120 | 工作台 | POST | `/admin-api/gym/mobile/feedbacks/{id}/replies` | 回复门店收到的反馈或评价 | 已实现，本地测试通过 |
| 121 | 工作台 | POST | `/admin-api/gym/mobile/activities` | 创建门店活动，设置名额和报名期限 | 已实现，本地测试通过 |
| 122 | 工作台 | GET | `/admin-api/gym/mobile/devices` | 查询授权门店的门禁和柜机设备 | 设计待开发 |
| 123 | 工作台 | GET | `/admin-api/gym/mobile/devices/{id}/compartments` | 查询指定柜机的柜格列表和状态 | 设计待开发 |
| 124 | 工作台 | POST | `/admin-api/gym/mobile/devices/{id}/commands` | 提交远程开门或开柜命令并保留操作记录 | 待开发；外部接入暂缓 |
| 125 | 工作台 | GET | `/admin-api/gym/mobile/device-commands/{id}` | 查询设备命令发送及实际执行结果 | 待开发；外部接入暂缓 |
| 126 | 工作台 | POST | `/admin-api/gym/mobile/action-confirmations` | 验证二次确认凭据，生成绑定设备和操作的短时令牌 | 设计待开发 |
| 127 | 工作台 | GET | `/admin-api/gym/mobile/notifications` | 查询员工本人可接收的工作台通知 | 设计待开发 |
| 128 | 公共及会员端 | GET | `/app-api/gym/me/notifications` | 查询会员本人预约、合同、权益等通知 | 设计待开发 |
| 129 | 工作台 | POST | `/admin-api/gym/mobile/notifications/{id}/read` | 将员工本人的通知标记为已读 | 设计待开发 |
| 130 | 公共及会员端 | POST | `/app-api/gym/me/notifications/{id}/read` | 将会员本人的通知标记为已读 | 设计待开发 |
| 131 | 公共及会员端 | POST | `/app-api/gym/me/notification-preferences` | 保存会员消息订阅意愿，不替代微信实际授权 | 设计待开发 |
| 132 | 设备集成 | POST | `/integration-api/gym/access-events` | 接收已认证门禁网关的进出事件，驱动签到及风控 | 待开发；外部接入暂缓 |
| 133 | 设备集成 | POST | `/integration-api/gym/device-command-results` | 接收设备真实执行回执，幂等更新命令状态 | 待开发；外部接入暂缓 |
| 134 | 工作台 | GET | `/admin-api/gym/mobile/risk-alerts` | 查询授权门店的门禁及异常行为告警 | 设计待开发 |
| 135 | 工作台 | POST | `/admin-api/gym/mobile/risk-alerts/{id}/resolutions` | 处理风控告警，记录确认违规或误报结论 | 设计待开发 |

## 尚未计数的集成项

| 项目 | 未计入原因 |
| --- | --- |
| system 后台登录、刷新及退出 | 原设计仅说明复用既有 system 认证流程，没有列出具体接口清单 |
| 支付及退款渠道通知 | 具体回调路径和协议由支付渠道适配确定 |
| 自动续费签约、扣款及解约通知 | 需按渠道能力确定独立或共用回调路径 |
| 电子合同签署通知 | 签约渠道确定后补充具体回调路径 |

这些事项明确方法和路径后，应同步更新总表及统计，避免将当前数量当作所有外部集成工作量。
