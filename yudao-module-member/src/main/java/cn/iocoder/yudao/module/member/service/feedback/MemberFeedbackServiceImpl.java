package cn.iocoder.yudao.module.member.service.feedback;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.controller.app.feedback.vo.AppMemberFeedbackCreateReqVO;
import cn.iocoder.yudao.module.member.controller.app.feedback.vo.AppMemberFeedbackRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.feedback.*;
import cn.iocoder.yudao.module.member.dal.mysql.feedback.*;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
public class MemberFeedbackServiceImpl implements MemberFeedbackService {
    public static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    @Resource private MemberFeedbackMapper feedbackMapper;
    @Resource private MemberFeedbackAttachmentMapper attachmentMapper;

    private Long tenant(Long userId) {
        if (userId == null) throw exception(UNAUTHORIZED);
        return TenantContextHolder.getRequiredTenantId();
    }

    @Override
    public Long uploadImage(Long userId, MultipartFile file) throws IOException {
        Long tenantId = tenant(userId);
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) throw exception(FEEDBACK_IMAGE_INVALID);
        byte[] bytes = file.getBytes();
        String type = imageType(bytes);
        if (bytes.length > MAX_IMAGE_SIZE || type == null) throw exception(FEEDBACK_IMAGE_INVALID);
        MemberFeedbackAttachmentDO image = new MemberFeedbackAttachmentDO();
        image.setUserId(userId); image.setTenantId(tenantId); image.setSort(0);
        image.setContent(bytes); image.setContentType(type);
        attachmentMapper.insert(image);
        return image.getId();
    }

    static String imageType(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if (header.equals("GIF87a") || header.equals("GIF89a")) return "image/gif";
        }
        if (bytes.length >= 12 && new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFeedback(Long userId, AppMemberFeedbackCreateReqVO request) {
        Long tenantId = tenant(userId);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.length() < 10 || content.length() > 500) throw exception(FEEDBACK_CONTENT_INVALID);
        List<Long> ids = request.getAttachmentIds() == null ? List.of() : new ArrayList<>(request.getAttachmentIds());
        if (ids.size() > 9 || ids.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(ids).size() != ids.size()) throw exception(FEEDBACK_ATTACHMENT_INVALID);
        if (!ids.isEmpty()) {
            List<MemberFeedbackAttachmentDO> owned = attachmentMapper.lockOwned(ids, userId, tenantId);
            if (owned.size() != ids.size() || owned.stream().anyMatch(image -> image.getFeedbackId() != null)) throw exception(FEEDBACK_ATTACHMENT_INVALID);
        }
        MemberFeedbackDO feedback = new MemberFeedbackDO();
        feedback.setUserId(userId); feedback.setTenantId(tenantId); feedback.setContent(content);
        feedbackMapper.insert(feedback);
        for (int index = 0; index < ids.size(); index++) {
            if (attachmentMapper.attach(ids.get(index), userId, tenantId, feedback.getId(), index) != 1) throw exception(FEEDBACK_ATTACHMENT_INVALID);
        }
        return feedback.getId();
    }

    @Override
    public AppMemberFeedbackRespVO getFeedback(Long userId, Long id) {
        Long tenantId = tenant(userId);
        MemberFeedbackDO feedback = feedbackMapper.selectOwned(id, userId, tenantId);
        if (feedback == null) throw exception(FEEDBACK_NOT_EXISTS);
        AppMemberFeedbackRespVO response = new AppMemberFeedbackRespVO();
        response.setId(feedback.getId()); response.setContent(feedback.getContent()); response.setCreateTime(feedback.getCreateTime());
        response.setAttachmentIds(attachmentMapper.selectByFeedback(id,userId,tenantId).stream().map(MemberFeedbackAttachmentDO::getId).toList());
        return response;
    }

    @Override
    public MemberFeedbackAttachmentDO getAttachment(Long userId, Long id) {
        MemberFeedbackAttachmentDO image = attachmentMapper.selectOwned(id, userId, tenant(userId));
        if (image == null) throw exception(FEEDBACK_ATTACHMENT_INVALID);
        return image;
    }
}
