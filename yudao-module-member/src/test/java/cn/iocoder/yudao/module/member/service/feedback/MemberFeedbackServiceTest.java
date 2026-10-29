package cn.iocoder.yudao.module.member.service.feedback;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.controller.app.feedback.vo.AppMemberFeedbackCreateReqVO;
import cn.iocoder.yudao.module.member.dal.mysql.feedback.MemberFeedbackMapper;
import cn.iocoder.yudao.module.member.dal.mysql.feedback.MemberFeedbackAttachmentMapper;
import jakarta.annotation.Resource;
import java.util.List;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Import(MemberFeedbackServiceImpl.class)
class MemberFeedbackServiceTest extends BaseDbUnitTest {
    private static final long USER = 9007199254740993L;
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10,0,1,2,3};
    @Resource private MemberFeedbackService service;
    @Resource private MemberFeedbackMapper mapper;
    @Resource private MemberFeedbackAttachmentMapper attachmentMapper;
    @Resource private DataSource dataSource;
    private JdbcTemplate jdbc;

    private void age(Long id) {
        jdbc.update("UPDATE member_feedback_attachment SET create_time = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(8)), id);
    }

    @Test void cleanupPhysicallyRemovesOnlyExpiredUnclaimedImagesInTheCurrentTenantAndBatch() throws Exception {
        Long first = upload(), second = upload(), claimed = upload(), fresh = upload();
        age(first); age(second); age(claimed);
        Long feedback = service.createFeedback(USER, request(claimed));
        TenantContextHolder.setTenantId(2L); Long otherTenant = upload(); age(otherTenant);
        TenantContextHolder.setTenantId(1L);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        assertEquals(1, service.cleanUnclaimedAttachments(cutoff, 1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_feedback_attachment WHERE id = ?", Integer.class, first));
        assertEquals(1, service.cleanUnclaimedAttachments(cutoff, 20));
        assertEquals(0, service.cleanUnclaimedAttachments(cutoff, 20));
        assertArrayEquals(PNG, service.getAttachment(USER, claimed).getContent());
        assertArrayEquals(PNG, service.getAttachment(USER, fresh).getContent());
        assertEquals(List.of(claimed), service.getFeedback(USER, feedback).getAttachmentIds());
        TenantContextHolder.setTenantId(2L); assertArrayEquals(PNG, service.getAttachment(USER, otherTenant).getContent());
    }

    @Test void cleanupRechecksClaimAndTenantWhenDeletingPreviouslySelectedCandidates() throws Exception {
        Long image = upload(); age(image);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Long> candidates = attachmentMapper.selectExpiredUnclaimedIds(1L, cutoff, 20);
        assertEquals(List.of(image), candidates);
        Long feedback = service.createFeedback(USER, request(image));
        assertEquals(0, attachmentMapper.deleteExpiredUnclaimedIds(candidates, 1L, cutoff));
        assertArrayEquals(PNG, service.getAttachment(USER, image).getContent());
        assertEquals(List.of(image), service.getFeedback(USER, feedback).getAttachmentIds());
        Long unclaimed = upload(); age(unclaimed);
        assertEquals(0, attachmentMapper.deleteExpiredUnclaimedIds(List.of(unclaimed), 2L, cutoff));
        assertArrayEquals(PNG, service.getAttachment(USER, unclaimed).getContent());
    }

    @Test void cleanupRejectsMissingTenantUnsafeRetentionAndUnboundedBatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        for (int limit : new int[]{0, -1, 101}) assertThrows(IllegalArgumentException.class, () -> service.cleanUnclaimedAttachments(cutoff, limit));
        assertThrows(IllegalArgumentException.class, () -> service.cleanUnclaimedAttachments(LocalDateTime.now(), 20));
        assertThrows(IllegalArgumentException.class, () -> service.cleanUnclaimedAttachments(null, 20));
        TenantContextHolder.clear(); assertThrows(NullPointerException.class, () -> service.cleanUnclaimedAttachments(cutoff, 20));
    }

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); jdbc = new JdbcTemplate(dataSource); }
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
