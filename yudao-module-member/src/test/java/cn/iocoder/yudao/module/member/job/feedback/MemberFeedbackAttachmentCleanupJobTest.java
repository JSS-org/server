package cn.iocoder.yudao.module.member.job.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.member.service.feedback.MemberFeedbackService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MemberFeedbackAttachmentCleanupJobTest {
    @Test void tenantJobKeepsOneCutoffAndBoundsEveryRunEvenWithConcurrentClaims() throws Exception {
        MemberFeedbackService service = mock(MemberFeedbackService.class);
        MemberFeedbackAttachmentCleanupJob job = new MemberFeedbackAttachmentCleanupJob();
        ReflectionTestUtils.setField(job, "feedbackService", service);
        when(service.cleanUnclaimedAttachments(any(), eq(20))).thenReturn(1);
        assertEquals("清理未提交反馈图片 5 张，保留 168 小时", job.execute(null));
        ArgumentCaptor<LocalDateTime> cutoffs = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(service, times(5)).cleanUnclaimedAttachments(cutoffs.capture(), eq(20));
        assertEquals(1, cutoffs.getAllValues().stream().distinct().count());
        assertTrue(cutoffs.getValue().isBefore(LocalDateTime.now().minusHours(167)));
        assertTrue(MemberFeedbackAttachmentCleanupJob.class.getMethod("execute", String.class).isAnnotationPresent(TenantJob.class));
    }

    @Test void validatesRetentionBeforeWorkAndStopsAnEmptyTenantBatch() {
        MemberFeedbackService service = mock(MemberFeedbackService.class);
        MemberFeedbackAttachmentCleanupJob job = new MemberFeedbackAttachmentCleanupJob();
        ReflectionTestUtils.setField(job, "feedbackService", service);
        for (String input : new String[]{"0", "23", "8761", "-1", "all"}) assertThrows(IllegalArgumentException.class, () -> job.execute(input));
        verifyNoInteractions(service);
        assertEquals("清理未提交反馈图片 0 张，保留 48 小时", job.execute("48"));
        verify(service).cleanUnclaimedAttachments(any(), eq(20));
    }
}
