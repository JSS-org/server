package cn.iocoder.yudao.module.member.controller.app.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Schema(description = "用户 App - 提交意见反馈")
@Data
public class AppMemberFeedbackCreateReqVO {
    @NotBlank @Size(min = 10, max = 500)
    private String content;

    @Size(max = 9)
    private List<@NotNull @Positive Long> attachmentIds;
}
