package cn.iocoder.yudao.module.member.service.feedback;

import cn.iocoder.yudao.module.member.controller.app.feedback.vo.AppMemberFeedbackCreateReqVO;
import cn.iocoder.yudao.module.member.controller.app.feedback.vo.AppMemberFeedbackRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.feedback.MemberFeedbackAttachmentDO;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

public interface MemberFeedbackService {
    Long uploadImage(Long userId, MultipartFile file) throws IOException;

    Long createFeedback(Long userId, AppMemberFeedbackCreateReqVO request);

    AppMemberFeedbackRespVO getFeedback(Long userId, Long id);

    MemberFeedbackAttachmentDO getAttachment(Long userId, Long id);

    /** Trusted tenant job only; no member-facing deletion API. Cutoff must be at least 24 hours old. */
    int cleanUnclaimedAttachments(LocalDateTime cutoff, int limit);
}
