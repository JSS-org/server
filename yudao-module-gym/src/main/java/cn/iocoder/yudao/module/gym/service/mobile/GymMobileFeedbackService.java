package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFeedbackVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.*;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobileFeedbackService {
    @Resource private GymFeedbackMapper feedbacks;
    @Resource private GymFeedbackAttachmentMapper attachments;
    @Resource private GymMobileFileOwnerMapper owners;
    @Resource private GymFileMapper files;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBusinessClock clock;
    @Resource private GymFeedbackImages images;
    @Resource private FileApi storage;
    @Resource private Validator validator;

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private void positive(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
    }

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private String time(Instant value) {
        return value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Receipt submit(Submit req, String key) {
        Long member = identities.requireMemberId();
        validate(req);
        if ("review".equals(req.getType()) && req.getRating() == null)
            throw exception(FEEDBACK_INVALID);
        req.setContent(req.getContent().strip());
        req.setAllowCardContext(Boolean.TRUE.equals(req.getAllowCardContext()));
        req.setFileIds(req.getFileIds() == null ? List.of() : List.copyOf(req.getFileIds()));
        if (new HashSet<>(req.getFileIds()).size() != req.getFileIds().size())
            throw exception(FEEDBACK_INVALID);
        return idempotency.execute(
                "feedback-submit",
                key,
                new TreeMap<>(Map.of("memberId", member, "request", req)),
                Receipt.class,
                () -> {
                    Instant now = now();
                    var store = stores.selectById(req.getStoreId());
                    if (store == null || !"active".equals(store.getStatus()))
                        throw exception(MEMBER_STORE_INACTIVE);
                    if (memberships.selectCount(
                                            new LambdaQueryWrapper<GymMemberStoreDO>()
                                                    .eq(GymMemberStoreDO::getMemberId, member)
                                                    .eq(
                                                            GymMemberStoreDO::getStoreId,
                                                            req.getStoreId()))
                                    == 0
                            && checkins.selectCount(
                                            new LambdaQueryWrapper<GymCheckinDO>()
                                                    .eq(GymCheckinDO::getMemberId, member)
                                                    .eq(GymCheckinDO::getStoreId, req.getStoreId())
                                                    .eq(GymCheckinDO::getStatus, "succeeded")
                                                    .le(GymCheckinDO::getEnteredAt, now))
                                    == 0) throw exception(FEEDBACK_INVALID);
                    // Sorted original-file locks also serialize two different submissions reusing
                    // one image.
                    for (Long fileId : new TreeSet<>(req.getFileIds())) {
                        var file =
                                files.selectOne(
                                        new LambdaQueryWrapper<GymFileDO>()
                                                .eq(GymFileDO::getId, fileId)
                                                .last("FOR UPDATE"));
                        verifyFile(file, member, req.getStoreId());
                        if (attachments.selectCount(GymFeedbackAttachmentDO::getFileId, fileId) > 0)
                            throw exception(FEEDBACK_ATTACHMENT_INVALID);
                        images.sanitize(
                                storage.getFileContentByUrl(file.getUrl()), file.getMimeType());
                    }
                    var row = new GymFeedbackDO();
                    row.setStoreId(req.getStoreId());
                    row.setMemberId(member);
                    row.setType(req.getType());
                    row.setContent(req.getContent());
                    row.setRating(req.getRating());
                    row.setAllowCardContext(req.getAllowCardContext());
                    row.setStatus("received");
                    row.setSubmittedAt(now);
                    if (req.getAllowCardContext())
                        row.setCardContext(
                                JsonUtils.toJsonString(context(member, req.getStoreId(), now)));
                    feedbacks.insert(row);
                    for (Long fileId : req.getFileIds()) {
                        var link = new GymFeedbackAttachmentDO();
                        link.setFeedbackId(row.getId());
                        link.setFileId(fileId);
                        attachments.insert(link);
                    }
                    return new Receipt(row.getId().toString(), "received", time(now));
                });
    }

    private CardContext context(Long member, Long store, Instant now) {
        long cardCount =
                cards.selectCount(
                        new QueryWrapper<GymMemberCardDO>()
                                .eq("member_id", member)
                                .eq("status", "active")
                                .le("starts_at", now)
                                .and(w -> w.isNull("expires_at").or().gt("expires_at", now))
                                .and(w -> w.ne("kind", "count").or().gt("remaining_uses", 0))
                                .exists(
                                        "SELECT 1 FROM gym_member_card_store cs WHERE cs.card_id=gym_member_card.id AND cs.tenant_id={0} AND cs.deleted=0 AND cs.store_id={1}",
                                        TenantContextHolder.getRequiredTenantId(), store));
        long packCount =
                packs.selectCount(
                        new LambdaQueryWrapper<GymCoursePackDO>()
                                .eq(GymCoursePackDO::getMemberId, member)
                                .eq(GymCoursePackDO::getStoreId, store)
                                .eq(GymCoursePackDO::getStatus, "active")
                                .le(GymCoursePackDO::getStartsAt, now)
                                .and(
                                        w ->
                                                w.isNull(GymCoursePackDO::getExpiresAt)
                                                        .or()
                                                        .gt(GymCoursePackDO::getExpiresAt, now))
                                .gt(GymCoursePackDO::getRemainingSessions, 0));
        return new CardContext(cardCount, packCount);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<OwnFeedback> mine(OwnPage req) {
        Long member = identities.requireMemberId();
        validate(req);
        var page =
                feedbacks.selectPage(
                        req,
                        new LambdaQueryWrapper<GymFeedbackDO>()
                                .eq(GymFeedbackDO::getMemberId, member)
                                .eq(
                                        req.getStoreId() != null,
                                        GymFeedbackDO::getStoreId,
                                        req.getStoreId())
                                .orderByDesc(GymFeedbackDO::getSubmittedAt)
                                .orderByDesc(GymFeedbackDO::getId));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r ->
                                        new OwnFeedback(
                                                r.getId().toString(),
                                                r.getType(),
                                                r.getContent(),
                                                r.getRating(),
                                                r.getStatus(),
                                                r.getReply(),
                                                time(r.getSubmittedAt())))
                        .toList(),
                page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<StoreFeedback> store(StorePage req) {
        access.requireAdmin();
        validate(req);
        scope.check(req.getStoreId());
        var page =
                feedbacks.selectPage(
                        req,
                        new LambdaQueryWrapper<GymFeedbackDO>()
                                .eq(GymFeedbackDO::getStoreId, req.getStoreId())
                                .eq(req.getType() != null, GymFeedbackDO::getType, req.getType())
                                .eq(
                                        req.getStatus() != null,
                                        GymFeedbackDO::getStatus,
                                        req.getStatus())
                                .orderByDesc(GymFeedbackDO::getSubmittedAt)
                                .orderByDesc(GymFeedbackDO::getId));
        if (page.getList().isEmpty()) return new PageResult<>(List.of(), page.getTotal());
        var byFeedback =
                attachments
                        .selectList(
                                new LambdaQueryWrapper<GymFeedbackAttachmentDO>()
                                        .in(
                                                GymFeedbackAttachmentDO::getFeedbackId,
                                                page.getList().stream()
                                                        .map(GymFeedbackDO::getId)
                                                        .toList())
                                        .orderByAsc(GymFeedbackAttachmentDO::getId))
                        .stream()
                        .collect(Collectors.groupingBy(GymFeedbackAttachmentDO::getFeedbackId));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r ->
                                        new StoreFeedback(
                                                r.getId().toString(),
                                                r.getType(),
                                                r.getRating(),
                                                r.getContent(),
                                                byFeedback
                                                        .getOrDefault(r.getId(), List.of())
                                                        .stream()
                                                        .map(a -> a.getFileId().toString())
                                                        .toList(),
                                                r.getStatus(),
                                                time(r.getSubmittedAt()),
                                                Boolean.TRUE.equals(r.getAllowCardContext())
                                                                && r.getCardContext() != null
                                                        ? JsonUtils.parseObject(
                                                                r.getCardContext(),
                                                                CardContext.class)
                                                        : null,
                                                r.getReply()))
                        .toList(),
                page.getTotal());
    }

    private GymFeedbackDO workRow(Long id, boolean lock) {
        access.requireAdmin();
        positive(id);
        var q = new LambdaQueryWrapper<GymFeedbackDO>().eq(GymFeedbackDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = feedbacks.selectOne(q);
        if (row == null) throw exception(FEEDBACK_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Replied reply(Long id, Reply req, String key) {
        workRow(id, false);
        validate(req);
        req.setContent(req.getContent().strip());
        return idempotency.execute(
                "feedback-reply",
                key,
                new TreeMap<>(Map.of("feedbackId", id, "content", req.getContent())),
                Replied.class,
                () -> {
                    var row = workRow(id, true);
                    if ("replied".equals(row.getStatus())) {
                        if (!req.getContent().equals(row.getReply()))
                            throw exception(FEEDBACK_ALREADY_REPLIED);
                        return new Replied(row.getId().toString(), "replied", row.getReply());
                    }
                    row.setStatus("replied");
                    row.setReply(req.getContent());
                    row.setRepliedBy(access.requireAdmin());
                    row.setRepliedAt(now());
                    feedbacks.updateById(row);
                    return new Replied(row.getId().toString(), "replied", row.getReply());
                });
    }

    private void verifyFile(GymFileDO file, Long member, Long store) {
        if (file == null
                || !"member_feedback".equals(file.getCategory())
                || !store.equals(file.getStoreId())) throw exception(FEEDBACK_ATTACHMENT_INVALID);
        var owner = owners.selectOne(GymMobileFileOwnerDO::getFileId, file.getId());
        if (owner == null
                || !member.equals(owner.getMemberId())
                || !"feedback".equals(owner.getPurpose()))
            throw exception(FEEDBACK_ATTACHMENT_INVALID);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymFileContentDTO attachment(Long id, Long fileId) {
        var row = workRow(id, false);
        positive(fileId);
        if (attachments.selectCount(
                        new LambdaQueryWrapper<GymFeedbackAttachmentDO>()
                                .eq(GymFeedbackAttachmentDO::getFeedbackId, id)
                                .eq(GymFeedbackAttachmentDO::getFileId, fileId))
                == 0) throw exception(FILE_NOT_EXISTS);
        var file = files.selectById(fileId);
        verifyFile(file, row.getMemberId(), row.getStoreId());
        return images.sanitize(storage.getFileContentByUrl(file.getUrl()), file.getMimeType());
    }
}
