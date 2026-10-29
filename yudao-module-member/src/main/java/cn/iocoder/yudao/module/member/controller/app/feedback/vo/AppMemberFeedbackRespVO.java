package cn.iocoder.yudao.module.member.controller.app.feedback.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AppMemberFeedbackRespVO {
    private Long id;
    private String content;
    private List<Long> attachmentIds;
    private LocalDateTime createTime;
}
