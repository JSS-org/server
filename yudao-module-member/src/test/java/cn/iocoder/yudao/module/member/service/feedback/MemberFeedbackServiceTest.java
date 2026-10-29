package cn.iocoder.yudao.module.member.service.feedback;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.controller.app.feedback.vo.AppMemberFeedbackCreateReqVO;
import cn.iocoder.yudao.module.member.dal.mysql.feedback.MemberFeedbackMapper;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

@Import(MemberFeedbackServiceImpl.class)
class MemberFeedbackServiceTest extends BaseDbUnitTest {
    private static final long USER = 9007199254740993L;
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10,0,1,2,3};
    @Resource private MemberFeedbackService service;
    @Resource private MemberFeedbackMapper mapper;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    private Long upload() throws Exception {
        return service.uploadImage(USER, new MockMultipartFile("file", "fixture.png", "image/png", PNG));
    }
    private AppMemberFeedbackCreateReqVO request(Long... ids) {
        return new AppMemberFeedbackCreateReqVO().setContent("这是一条用于实际持久化验证的反馈内容").setAttachmentIds(List.of(ids));
    }

    @Test void storesFeedbackAndPrivateBytesInTheRequestedImageOrder() throws Exception {
        Long first = upload(), second = upload();
        Long id = service.createFeedback(USER, request(second, first));
        var saved = service.getFeedback(USER, id);
        assertEquals(request().getContent(), saved.getContent());
        assertEquals(List.of(second, first), saved.getAttachmentIds());
        assertArrayEquals(PNG, service.getAttachment(USER, first).getContent());
        assertEquals(USER, mapper.selectById(id).getUserId());
        assertEquals(1L, mapper.selectById(id).getTenantId());
    }

    @Test void anotherMemberCannotReadOrClaimAnAttachment() throws Exception {
        Long image = upload();
        assertServiceException(() -> service.createFeedback(USER + 2, request(image)), FEEDBACK_ATTACHMENT_INVALID);
        assertServiceException(() -> service.getAttachment(USER + 2, image), FEEDBACK_ATTACHMENT_INVALID);
        assertEquals(0, mapper.selectCount());
        Long id = service.createFeedback(USER, request(image));
        assertServiceException(() -> service.getFeedback(USER + 2, id), FEEDBACK_NOT_EXISTS);
    }

    @Test void theSameMemberIdInAnotherTenantCannotReadOrClaimData() throws Exception {
        Long image = upload(), id = service.createFeedback(USER, request(image));
        Long unclaimed = upload();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> service.getFeedback(USER, id), FEEDBACK_NOT_EXISTS);
        assertServiceException(() -> service.getAttachment(USER, image), FEEDBACK_ATTACHMENT_INVALID);
        assertServiceException(() -> service.createFeedback(USER, request(unclaimed)), FEEDBACK_ATTACHMENT_INVALID);
    }

    @Test void duplicateOrPreviouslyClaimedImagesDoNotCreateAnotherFeedback() throws Exception {
        Long image = upload();
        assertServiceException(() -> service.createFeedback(USER, request(image,image)), FEEDBACK_ATTACHMENT_INVALID);
        assertEquals(0, mapper.selectCount());
        service.createFeedback(USER, request(image));
        assertServiceException(() -> service.createFeedback(USER, request(image)), FEEDBACK_ATTACHMENT_INVALID);
        assertEquals(1, mapper.selectCount());
    }

    @Test void refusesHtmlDisguisedAsPngAndOversizeUploads() {
        assertServiceException(() -> service.uploadImage(USER,
                new MockMultipartFile("file", "fake.png", "image/png", "<svg onload='alert(1)'/>".getBytes())), FEEDBACK_IMAGE_INVALID);
        assertServiceException(() -> service.uploadImage(USER,
                new MockMultipartFile("file", "large.png", "image/png", PNG) {
                    @Override public long getSize() { return MemberFeedbackServiceImpl.MAX_IMAGE_SIZE + 1L; }
                }), FEEDBACK_IMAGE_INVALID);
    }

    @Test void requiresAMemberAndRealFeedbackContent() {
        assertServiceException(() -> service.createFeedback(null, request()), UNAUTHORIZED);
        assertServiceException(() -> service.createFeedback(USER, request().setContent("          a          ")), FEEDBACK_CONTENT_INVALID);
        assertEquals(0, mapper.selectCount());
    }
}
