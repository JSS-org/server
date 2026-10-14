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
    ErrorCode FILE_SIZE_EXCEED_LIMIT = new ErrorCode(1_070_000_004, "文件大小超出限制：{} 最大 {} 字节，当前 {} 字节");
    ErrorCode FILE_CONTENT_EMPTY = new ErrorCode(1_070_000_005, "文件内容为空");
    ErrorCode FILE_STORE_NOT_EXISTS = new ErrorCode(1_070_000_006, "门店（部门）不存在：{}");
    ErrorCode FILE_STORE_NO_PERMISSION = new ErrorCode(1_070_000_007, "无权访问门店（部门）{} 的文件");
    ErrorCode FILE_STORE_REQUIRED = new ErrorCode(1_070_000_008, "门店编号不能为空：请传入 storeId，或确认当前账号已归属门店");
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
    ErrorCode EMPLOYEE_ASSIGNMENT_INVALID = new ErrorCode(1_070_002_004, "门店不能重复且须有一个主门店，任职日期不能早于入职日期");
    ErrorCode EMPLOYEE_USER_INVALID = new ErrorCode(1_070_002_005, "系统账号不存在、已停用或部门与主门店不一致");
    ErrorCode EMPLOYEE_USER_NO_PERMISSION = new ErrorCode(1_070_002_006, "关联系统账号需要 system:user:update 权限");
    ErrorCode EMPLOYEE_SALARY_UNSUPPORTED = new ErrorCode(1_070_002_007, "工资配置尚未接入，请单独处理");

}
