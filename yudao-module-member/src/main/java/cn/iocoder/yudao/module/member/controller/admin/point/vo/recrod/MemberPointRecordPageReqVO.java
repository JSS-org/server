package cn.iocoder.yudao.module.member.controller.admin.point.vo.recrod;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 用户积分记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemberPointRecordPageReqVO extends PageParam {

    @Schema(description = "用户昵称", example = "张三")
    private String nickname;

    @Schema(description = "用户编号", example = "123")
    private Long userId;

    @Schema(description = "业务类型", example = "1")
    private Integer bizType;

    @Schema(description = "积分标题", example = "呵呵")
    private String title;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
