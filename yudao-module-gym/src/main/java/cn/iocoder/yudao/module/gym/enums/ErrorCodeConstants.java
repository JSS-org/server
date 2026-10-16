package cn.iocoder.yudao.module.gym.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * gym 模块错误码
 *
 * 错误码区间：1-070-000-000 ~ 1-070-999-999（避开其他模块已占用区间，见各模块 ErrorCodeConstants）
 *
 * 说明：yudao 统一返回 HTTP 200，业务错误通过 body 的 {@code code} 表达；
 * 旧 Mock（target/plan/10-backend-mock-api-inventory.md §2）用 HTTP 状态码表达，
 * 迁移后的对应关系见 script/gym/README.md。
 *
 * @author JSS
 */
public interface ErrorCodeConstants {

    // ========== 文件 1-070-000-000 ==========
    ErrorCode FILE_NOT_EXISTS = new ErrorCode(1_070_000_001, "文件不存在");
    ErrorCode FILE_CATEGORY_INVALID = new ErrorCode(1_070_000_002, "文件类别不正确，可选值：{}");

    /** 对应旧 Mock 的 HTTP 415：内容与声明类型不一致，或该类别不允许此类型 */
    ErrorCode FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_070_000_003, "文件类型与内容不匹配：{}");

    ErrorCode FILE_SIZE_EXCEED_LIMIT =
            new ErrorCode(1_070_000_004, "文件大小超出限制：{} 最大 {} 字节，当前 {} 字节");
    ErrorCode FILE_CONTENT_EMPTY = new ErrorCode(1_070_000_005, "文件内容为空");
    ErrorCode FILE_STORE_NOT_EXISTS = new ErrorCode(1_070_000_006, "门店（部门）不存在：{}");
    ErrorCode FILE_STORE_NO_PERMISSION = new ErrorCode(1_070_000_007, "无权访问门店（部门）{} 的文件");
    ErrorCode FILE_STORE_REQUIRED =
            new ErrorCode(1_070_000_008, "门店编号不能为空：请传入 storeId，或确认当前账号已归属门店");
    ErrorCode FILE_UPLOAD_FAILED = new ErrorCode(1_070_000_009, "文件保存失败：{}");
    ErrorCode FILE_CONTENT_NOT_EXISTS = new ErrorCode(1_070_000_010, "文件内容不存在，可能已被清理，请重新上传");

    // ========== 门店 / 员工 1-070-001/002 ==========
    ErrorCode STORE_NOT_EXISTS = new ErrorCode(1_070_001_001, "门店不存在");
    ErrorCode STORE_NO_PERMISSION = new ErrorCode(1_070_001_002, "无权访问该门店范围");
    ErrorCode STORE_CODE_DUPLICATE = new ErrorCode(1_070_001_003, "门店编码或部门已被使用");
    ErrorCode STORE_DEPT_INVALID = new ErrorCode(1_070_001_004, "部门不存在或已停用");
    ErrorCode STORE_MEDIA_INVALID = new ErrorCode(1_070_001_005, "媒体须为本门店图片/视频，文件不能重复，封面至多一张图片");
    ErrorCode EMPLOYEE_NOT_EXISTS = new ErrorCode(1_070_002_001, "员工不存在");
    ErrorCode EMPLOYEE_NO_DUPLICATE = new ErrorCode(1_070_002_002, "工号或系统账号已关联其他员工");
    ErrorCode EMPLOYEE_NO_IMMUTABLE = new ErrorCode(1_070_002_003, "工号不可修改");
    ErrorCode EMPLOYEE_ASSIGNMENT_INVALID =
            new ErrorCode(1_070_002_004, "门店不能重复且须有一个主门店，任职日期不能早于入职日期");
    ErrorCode EMPLOYEE_USER_INVALID = new ErrorCode(1_070_002_005, "系统账号不存在、已停用或部门与主门店不一致");
    ErrorCode EMPLOYEE_USER_NO_PERMISSION =
            new ErrorCode(1_070_002_006, "关联系统账号需要 system:user:update 权限");
    ErrorCode EMPLOYEE_SALARY_UNSUPPORTED = new ErrorCode(1_070_002_007, "工资配置尚未接入，请单独处理");

    // ========== 薪资 1-070-003 ==========
    ErrorCode SALARY_TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_003_001, "薪资模板不存在");
    ErrorCode SALARY_TEMPLATE_IMMUTABLE = new ErrorCode(1_070_003_002, "已发布薪资模板不可修改，请创建新版本");
    ErrorCode SALARY_TEMPLATE_STATE_INVALID = new ErrorCode(1_070_003_003, "薪资模板状态不合法");
    ErrorCode SALARY_RULE_INVALID = new ErrorCode(1_070_003_004, "薪资规则类型、金额或提成区间不合法");
    ErrorCode SALARY_INTERVAL_INVALID = new ErrorCode(1_070_003_005, "结束日期必须晚于生效日期（结束日期不含）");
    ErrorCode SALARY_ASSIGNMENT_INVALID =
            new ErrorCode(1_070_003_006, "发薪门店须属于员工任职门店，日期须在已发布模板有效期及任职期内");
    ErrorCode SALARY_ASSIGNMENT_OVERLAP = new ErrorCode(1_070_003_007, "员工薪资配置日期重叠");
    ErrorCode SALARY_ASSIGNMENT_NOT_EXISTS = new ErrorCode(1_070_003_008, "员工薪资配置不存在");
    ErrorCode BUSINESS_DATE_INVALID = new ErrorCode(1_070_004_001, "时间筛选不合法或存在冲突，结束日期不含");
    ErrorCode MEMBER_NOT_EXISTS = new ErrorCode(1_070_004_002, "会员不存在");
    ErrorCode MEMBER_PHONE_DUPLICATE = new ErrorCode(1_070_004_003, "会员手机号已建档");
    ErrorCode MEMBER_STORE_INACTIVE = new ErrorCode(1_070_004_004, "建档门店已停用");
    ErrorCode MEMBER_STORE_IMMUTABLE = new ErrorCode(1_070_004_005, "建档门店不可直接变更");
    ErrorCode MEMBER_COACH_INVALID = new ErrorCode(1_070_004_007, "负责员工须为本门店在职且关联有效后台账号，不允许重复");
    ErrorCode MEMBER_RIGHTS_FILTER_UNAVAILABLE = new ErrorCode(1_070_004_006, "会员权益筛选尚未接入");
    ErrorCode BLACKLIST_NOT_EXISTS = new ErrorCode(1_070_005_001, "黑名单记录不存在");
    ErrorCode BLACKLIST_INVALID = new ErrorCode(1_070_005_002, "黑名单须至少限制一项；会员及开始时间不可变，结束时间必须晚于开始");
    ErrorCode BLACKLIST_REVOKED = new ErrorCode(1_070_005_003, "已撤销黑名单记录不可修改");
    ErrorCode MEMBER_ENTRY_BLOCKED = new ErrorCode(1_070_005_004, "会员处于禁止入场黑名单");
    ErrorCode MEMBER_PURCHASE_BLOCKED = new ErrorCode(1_070_005_005, "会员处于禁止购卡黑名单");
    ErrorCode MEASUREMENT_NOT_EXISTS = new ErrorCode(1_070_006_001, "体测记录不存在");
    ErrorCode MEASUREMENT_INVALID = new ErrorCode(1_070_006_002, "体测会员须属于该门店，门店/会员/测量时间不可变");
    ErrorCode MEASUREMENT_FILE_INVALID = new ErrorCode(1_070_006_003, "体测报告须为本门店受控 body_report 文件");
    ErrorCode CARD_TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_007_001, "卡模板不存在");
    ErrorCode CARD_TEMPLATE_IMMUTABLE = new ErrorCode(1_070_007_002, "已发布或停用的卡模板内容不可修改，请新建版本");
    ErrorCode CARD_TEMPLATE_INVALID = new ErrorCode(1_070_007_003, "卡模板类型、次数/天数或适用门店不合法");
    ErrorCode CARD_NOT_EXISTS = new ErrorCode(1_070_007_004, "会员卡不存在");
    ErrorCode CARD_RIGHT_INVALID = new ErrorCode(1_070_007_005, "会员卡归属、状态、门店或有效期不符合本次业务");
    ErrorCode CARD_RIGHT_INSUFFICIENT = new ErrorCode(1_070_007_006, "会员卡可用次数不足");
    ErrorCode CARD_SOURCE_CONFLICT = new ErrorCode(1_070_007_007, "业务来源已经发卡且请求内容不一致");
    ErrorCode COURSE_PRODUCT_NOT_EXISTS = new ErrorCode(1_070_008_001, "私教产品不存在");
    ErrorCode COURSE_COACH_INVALID = new ErrorCode(1_070_008_002, "教练员工须在该门店任职且处于在职状态");
    ErrorCode COURSE_PRODUCT_INVALID = new ErrorCode(1_070_008_003, "私教产品门店/教练不可变或产品已停用");
    ErrorCode COURSE_PRICE_APPROVAL_REQUIRED = new ErrorCode(1_070_008_004, "修改私教价格须通过改价审批");
    ErrorCode COURSE_SESSION_NOT_EXISTS = new ErrorCode(1_070_008_005, "私教排期不存在");
    ErrorCode COURSE_SESSION_INVALID =
            new ErrorCode(1_070_008_006, "私教排期须匹配产品、教练、时长和容量1；已有预约不得变更时间");
    ErrorCode COURSE_SESSION_CONFLICT = new ErrorCode(1_070_008_007, "教练在该时间已有排期");
    ErrorCode COURSE_PACK_NOT_EXISTS = new ErrorCode(1_070_008_008, "私教权益不存在");
    ErrorCode COURSE_PACK_INVALID = new ErrorCode(1_070_008_009, "私教权益门店、产品、教练、状态或有效期不符合预约");
    ErrorCode COURSE_PACK_INSUFFICIENT = new ErrorCode(1_070_008_010, "私教可用课时不足");
    ErrorCode BOOKING_NOT_EXISTS = new ErrorCode(1_070_009_001, "预约不存在");
    ErrorCode BOOKING_INVALID = new ErrorCode(1_070_009_002, "预约状态、会员、排期或权益不合法");
    ErrorCode BOOKING_CAPACITY_FULL = new ErrorCode(1_070_009_003, "该私教排期已被预约");
    ErrorCode LESSON_CHECKIN_REQUIRED = new ErrorCode(1_070_009_004, "须先签到入场，才能确认签课扣课");
    ErrorCode LESSON_TIME_INVALID = new ErrorCode(1_070_009_005, "课程尚未开始或签到日期不属于本次课程");
    ErrorCode APPROVAL_TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_010_001, "审批模板不存在");
    ErrorCode APPROVAL_TEMPLATE_IMMUTABLE =
            new ErrorCode(1_070_010_002, "已发布或停用的审批模板内容不可修改，请创建新版本");
    ErrorCode APPROVAL_STEPS_INVALID = new ErrorCode(1_070_010_003, "审批步骤须从1连续编号且审批员工不能重复");
    ErrorCode APPROVAL_APPROVER_INVALID =
            new ErrorCode(1_070_010_004, "审批员工不存在，或发布模板时员工/关联账号未处于可用状态");
    ErrorCode APPROVAL_NOT_EXISTS = new ErrorCode(1_070_010_005, "审批记录不存在");
    ErrorCode APPROVAL_REQUEST_INVALID = new ErrorCode(1_070_010_006, "审批类型、模板、来源、门店或申请内容不合法");
    ErrorCode APPROVAL_IDEMPOTENCY_CONFLICT = new ErrorCode(1_070_010_007, "相同提交键已经用于不同的审批申请");
    ErrorCode APPROVAL_ATTACHMENT_INVALID = new ErrorCode(1_070_010_008, "附件须为本门店受控审批或合同材料且不能重复");
    ErrorCode APPROVAL_SOURCE_UNAVAILABLE = new ErrorCode(1_070_010_009, "该审批来源的实际业务执行尚未接入");
    ErrorCode APPROVAL_DECISION_FORBIDDEN = new ErrorCode(1_070_010_010, "仅指定步骤审批人可以处理申请");
    ErrorCode APPROVAL_DECISION_INVALID = new ErrorCode(1_070_010_011, "审批步骤尚未轮到、已结束或重复处理结果冲突");
    ErrorCode CONTRACT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_011_001, "合同模板不存在");
    ErrorCode CONTRACT_TEMPLATE_IMMUTABLE =
            new ErrorCode(1_070_011_002, "已发布或停用的合同模板内容及PDF不可修改，请创建新版本");
    ErrorCode CONTRACT_TEMPLATE_FILE_INVALID =
            new ErrorCode(1_070_011_003, "合同模板须关联同租户受控contract类别PDF文件");
    ErrorCode CONTRACT_NOT_EXISTS = new ErrorCode(1_070_011_004, "合同记录不存在");
    ErrorCode CONTRACT_SOURCE_INVALID = new ErrorCode(1_070_011_005, "合同须关联真实会员卡或私教权益");
    ErrorCode CONTRACT_SOURCE_CONFLICT = new ErrorCode(1_070_011_006, "业务来源或提交键已经用于不同合同内容");
    ErrorCode CONTRACT_STATE_INVALID = new ErrorCode(1_070_011_007, "合同状态、审批来源或原件已变化，不能执行本次操作");
    ErrorCode CONTRACT_REMINDER_INVALID = new ErrorCode(1_070_011_008, "合同提醒提交键不合法");
    ErrorCode PAYROLL_NOT_EXISTS = new ErrorCode(1_070_012_001, "工资表不存在");
    ErrorCode PAYROLL_FROZEN = new ErrorCode(1_070_012_002, "已确认工资表不可重算或覆盖");
    ErrorCode PAYROLL_MONTH_INVALID = new ErrorCode(1_070_012_003, "工资月份不合法");
    ErrorCode PAYROLL_ASSIGNMENT_INVALID =
            new ErrorCode(1_070_012_004, "员工{}缺少覆盖整月的唯一已发布工资配置，或任职/模板日期不满足整月要求");
    ErrorCode PAYROLL_INACTIVE_INTERVAL_REQUIRED =
            new ErrorCode(1_070_012_005, "员工{}已停用，请先明确工资配置结束日期");
    ErrorCode PAYROLL_SOURCE_NO_PERMISSION =
            new ErrorCode(1_070_012_006, "部分员工包含跨店计薪来源，请由具备全部来源门店权限的账号计算");
    ErrorCode PAYROLL_ASSIGNMENT_FROZEN =
            new ErrorCode(1_070_012_007, "该工资配置已用于确认工资，不能缩短已确认月份的覆盖期间");
    ErrorCode SALES_SETTINGS_INVALID =
            new ErrorCode(1_070_013_001, "售卡或核销设置不合法，请检查重复方案、签约审批依赖和时间范围");
    ErrorCode SALES_SETTINGS_CARD_INVALID =
            new ErrorCode(1_070_013_002, "方案卡种须适用于本店，启用时须已发布，包月须为28至31天期限卡");
    ErrorCode PAYMENT_ACCOUNT_NOT_EXISTS = new ErrorCode(1_070_014_001, "收款账户不存在");
    ErrorCode PAYMENT_ACCOUNT_DUPLICATE = new ErrorCode(1_070_014_002, "该归属下相同渠道、商户和脱敏账户已存在");
    ErrorCode PAYMENT_ACCOUNT_IMMUTABLE =
            new ErrorCode(1_070_014_003, "收款账户归属、渠道、商户、末四位与绑定结果不能直接修改");
    ErrorCode SETTLEMENT_RULE_NOT_EXISTS = new ErrorCode(1_070_014_004, "分账规则不存在");
    ErrorCode SETTLEMENT_RULE_INVALID =
            new ErrorCode(1_070_014_005, "分账规则不合法，请检查日期、接收账户和精确100%的比例合计");
    ErrorCode SETTLEMENT_RULE_IMMUTABLE = new ErrorCode(1_070_014_006, "已启用版本不可修改，请新增版本");
    ErrorCode SETTLEMENT_ACCOUNT_UNBOUND = new ErrorCode(1_070_014_007, "接收账户尚未完成真实渠道绑定，不能启用分账");
    ErrorCode SETTLEMENT_ACCOUNT_CHANNEL_INVALID =
            new ErrorCode(1_070_014_008, "同一分账规则的接收账户渠道必须一致");
    ErrorCode SETTLEMENT_RULE_OVERLAP =
            new ErrorCode(1_070_014_009, "启用区间冲突；新版本须从不早于今天且晚于旧版本开始的日期生效");
    ErrorCode SETTLEMENT_NOT_EXISTS = new ErrorCode(1_070_014_010, "分账结算记录不存在");
    ErrorCode PAYMENT_ACCOUNT_IN_USE =
            new ErrorCode(1_070_014_011, "账户正在有效分账规则中使用，请新建账户及规则版本替换，不能直接更换凭据引用");
    ErrorCode ACTIVITY_NOT_EXISTS = new ErrorCode(1_070_015_001, "活动不存在");
    ErrorCode ACTIVITY_INVALID = new ErrorCode(1_070_015_002, "活动门店或时间不合法，报名结束须不晚于活动开始");
    ErrorCode ACTIVITY_STATE_INVALID = new ErrorCode(1_070_015_003, "活动状态不可逆，发布须为有效活动且门店启用");
    ErrorCode ACTIVITY_CAPACITY_INVALID = new ErrorCode(1_070_015_004, "活动名额已满，或名额不能低于已确认报名人数");
    ErrorCode ACTIVITY_REGISTERED_IMMUTABLE = new ErrorCode(1_070_015_005, "已有报名记录，不能修改活动门店和时间安排");
    ErrorCode ACTIVITY_COVER_INVALID = new ErrorCode(1_070_015_006, "活动封面须为活动门店所属的受控图片文件");
    ErrorCode ACTIVITY_REGISTRATION_INVALID = new ErrorCode(1_070_015_007, "报名门店、会员、时间或活动状态不符合要求");
    ErrorCode FRANCHISE_NOT_EXISTS = new ErrorCode(1_070_016_001, "加盟咨询不存在");
    ErrorCode FRANCHISE_REQUEST_INVALID =
            new ErrorCode(1_070_016_002, "请提供8至128位有效的Idempotency-Key提交标识");
    ErrorCode FRANCHISE_REQUEST_CONFLICT = new ErrorCode(1_070_016_003, "相同提交标识已用于不同咨询内容");
    ErrorCode FRANCHISE_STATE_INVALID = new ErrorCode(1_070_016_004, "加盟咨询状态不可回退，已结束记录不能重新打开");
    ErrorCode DEVICE_NOT_EXISTS = new ErrorCode(1_070_017_001, "设备不存在");
    ErrorCode DEVICE_UID_DUPLICATE = new ErrorCode(1_070_017_002, "同厂商设备编号已登记，不能重复绑定门店");
    ErrorCode DEVICE_IDENTITY_IMMUTABLE =
            new ErrorCode(1_070_017_003, "设备归属、类型和标识不能修改，在线信息须由可信设备回执更新");
    ErrorCode DEVICE_COMPARTMENT_INVALID = new ErrorCode(1_070_017_004, "柜格须属于当前智能柜，编号不能重复");
    ErrorCode DEVICE_COMPARTMENT_BUSY = new ErrorCode(1_070_017_005, "已占用或预留柜格不能被移除、停用或重置");
    ErrorCode DEVICE_COMMAND_INVALID = new ErrorCode(1_070_017_006, "设备指令、柜格或Idempotency-Key不合法");
    ErrorCode DEVICE_COMMAND_CONFLICT = new ErrorCode(1_070_017_007, "相同指令提交标识已用于不同内容");
    ErrorCode EQUIPMENT_NOT_EXISTS = new ErrorCode(1_070_018_001, "器械不存在");
    ErrorCode EQUIPMENT_CODE_DUPLICATE = new ErrorCode(1_070_018_002, "同一门店的器械编码已存在");
    ErrorCode EQUIPMENT_IDENTITY_IMMUTABLE = new ErrorCode(1_070_018_003, "器械门店和编码不能修改");
    ErrorCode EQUIPMENT_TUTORIAL_FILE_INVALID =
            new ErrorCode(1_070_018_004, "教学文件须为器械门店所属的受控图片或MP4视频");
    ErrorCode EQUIPMENT_TUTORIAL_NOT_EXISTS = new ErrorCode(1_070_018_005, "器械教学记录不存在或不属于当前器械");
    ErrorCode CLIENT_CONTENT_INVALID = new ErrorCode(1_070_019_001, "展示配置不合法，请检查平台、区块顺序及HTTP(S)链接");
    ErrorCode CLIENT_CONTENT_FILE_INVALID =
            new ErrorCode(1_070_019_002, "展示媒体须使用有权引用的受控图片或视频，门店配置不可引用其他门店文件");
    ErrorCode QR_CODE_SCENE_INVALID = new ErrorCode(1_070_019_003, "场景码须匹配当前门店，课程场景必须选择本店真实排期");
    ErrorCode QR_CODE_NOT_EXISTS = new ErrorCode(1_070_019_004, "场景码记录不存在");
    ErrorCode SALES_ORDER_NOT_EXISTS = new ErrorCode(1_070_020_001, "自主办卡订单不存在");
    ErrorCode SUBSCRIPTION_NOT_EXISTS = new ErrorCode(1_070_020_002, "包月协议不存在");
    ErrorCode COMMERCE_PROVIDER_UNCONFIGURED = new ErrorCode(1_070_020_003, "支付渠道未配置，无法核验交易凭证");
    ErrorCode COMMERCE_REQUEST_INVALID = new ErrorCode(1_070_020_004, "订单请求不合法或当前不可售");
    ErrorCode COMMERCE_IDEMPOTENCY_CONFLICT = new ErrorCode(1_070_020_005, "交易幂等标识与原请求不一致");
    ErrorCode COMMERCE_RECEIPT_INVALID = new ErrorCode(1_070_020_006, "交易凭证与订单不一致或金额不合法");
    ErrorCode COMMERCE_FULFILLMENT_INVALID = new ErrorCode(1_070_020_007, "订单未满足开卡条件、已退款或已履约");
    ErrorCode REFUND_REVIEW_INVALID = new ErrorCode(1_070_020_008, "退款权益处理状态或所选会员卡不合法");
    ErrorCode REFUND_REVIEW_NOT_EXISTS = new ErrorCode(1_070_020_009, "退款人工处理记录不存在");
    ErrorCode TRAINING_PLAN_NOT_EXISTS = new ErrorCode(1_070_021_001, "训练计划不存在");
    ErrorCode TRAINING_PLAN_INVALID = new ErrorCode(1_070_021_002, "训练计划内容或状态不合法，会员及门店不可修改");
    ErrorCode TRAINING_PLAN_FILE_INVALID = new ErrorCode(1_070_021_003, "训练计划附件须为当前会员可关联的本店训练资料");
}
