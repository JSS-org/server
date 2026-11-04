package cn.iocoder.yudao.module.member.job.feedback;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.member.service.feedback.MemberFeedbackService;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Registered through the existing managed scheduler; no automatic job/data registration. */
@Component
public class MemberFeedbackAttachmentCleanupJob implements JobHandler {
    public static final int DEFAULT_RETENTION_HOURS = 168;
    public static final int BATCH_SIZE = 20;
    public static final int MAX_BATCHES = 5;
    @Resource private MemberFeedbackService feedbackService;

    @Override
    @TenantJob
    public String execute(String param) {
        int hours =
                param == null || param.isBlank()
                        ? DEFAULT_RETENTION_HOURS
                        : Integer.parseInt(param.trim());
        if (hours < 24 || hours > 8760) throw new IllegalArgumentException("反馈附件保留时间需为24至8760小时");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int removed = feedbackService.cleanUnclaimedAttachments(cutoff, BATCH_SIZE);
            total += removed;
            // Partial deletions can reflect concurrent claims. Stop on no progress;
            // a later scheduled run can continue without spinning in this execution.
            if (removed == 0) break;
        }
        return "清理未提交反馈图片 " + total + " 张，保留 " + hours + " 小时";
    }
}
