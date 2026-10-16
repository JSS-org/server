package cn.iocoder.yudao.module.gym.service.contract;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCoursePackMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberService;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymContractService {
    @Resource private GymContractMapper gymContractMapper;
    @Resource private GymContractEventMapper gymContractEventMapper;
    @Resource private GymContractReminderMapper gymContractReminderMapper;
    @Resource private GymContractTemplateMapper gymContractTemplateMapper;
    @Resource private GymFileMapper gymFileMapper;
    @Resource private GymMemberCardMapper gymMemberCardMapper;
    @Resource private GymCoursePackMapper gymCoursePackMapper;

    @Resource
    private cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper gymSubscriptionMapper;

    @Resource private GymMemberService members;
    @Resource private GymStoreService stores;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock gymBusinessClock;
    @Resource private FileApi fileApi;

    @org.springframework.beans.factory.annotation.Value("${gym.file.storage-config-id:#{null}}")
    private Long storageConfigId;

    @Resource private Validator validator;

    public PageResult<GymContractRespVO> page(GymBusinessPageReqVO req) {
        if (req.getStoreId() != null) members.checkMemberListStore(req.getStoreId());
        if (req.getMemberId() != null) members.requireMember(req.getMemberId(), false);
        var range = GymDateRange.of(req);
        var q =
                new LambdaQueryWrapper<GymContractDO>()
                        .apply(
                                members.memberPredicate(
                                        "gym_contract.member_id", "gym_contract.store_id"))
                        .eq(req.getStoreId() != null, GymContractDO::getStoreId, req.getStoreId())
                        .eq(
                                req.getMemberId() != null,
                                GymContractDO::getMemberId,
                                req.getMemberId())
                        .eq(req.getStatus() != null, GymContractDO::getStatus, req.getStatus())
                        .and(
                                req.getKeyword() != null && !req.getKeyword().isBlank(),
                                w ->
                                        w.like(GymContractDO::getContractNo, req.getKeyword())
                                                .or()
                                                .like(GymContractDO::getName, req.getKeyword()))
                        .ge(range.from() != null, GymContractDO::getIssuedAt, range.from())
                        .lt(range.to() != null, GymContractDO::getIssuedAt, range.to())
                        .orderByDesc(GymContractDO::getId);
        var page = gymContractMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::response).toList(), page.getTotal());
    }

    public GymContractDO require(Long id, boolean lock) {
        var row = lock ? gymContractMapper.selectForUpdate(id) : gymContractMapper.selectById(id);
        if (row == null) throw exception(CONTRACT_NOT_EXISTS);
        members.checkMemberScope(row.getMemberId(), row.getStoreId());
        members.requireMember(row.getMemberId(), false);
        return row;
    }

    public GymContractRespVO get(Long id) {
        return response(require(id, false));
    }

    /** Internal hook: issuing an entitlement uses the latest published template of its type, if one is configured. */
    @Transactional(rollbackFor = Exception.class)
    public GymContractDO issueForEntitlement(String type, Long id) {
        return issue(type, id, null, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public GymContractDO requireForEntitlement(String type, Long id) {
        return issue(type, id, null, null, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public GymContractRespVO create(GymContractCreateReqVO req) {
        validate(req);
        return response(
                issue(
                        req.getSourceType(),
                        req.getSourceId(),
                        req.getTemplateId(),
                        req.getDueAt(),
                        true));
    }

    private GymContractDO issue(
            String type, Long id, Long templateId, Instant dueAt, boolean required) {
        Long memberId, storeId;
        String applicable, name;
        Instant effective;
        Object entitlement;
        if ("member-cards".equals(type)) {
            var card = gymMemberCardMapper.selectForUpdate(id);
            if (card == null) throw exception(CARD_NOT_EXISTS);
            memberId = card.getMemberId();
            storeId = card.getIssuedStoreId();
            applicable = "card";
            name = card.getName();
            effective = card.getStartsAt();
            entitlement = card;
        } else if ("course-packs".equals(type)) {
            var pack = gymCoursePackMapper.selectForUpdate(id);
            if (pack == null) throw exception(COURSE_PACK_NOT_EXISTS);
            memberId = pack.getMemberId();
            storeId = pack.getStoreId();
            applicable = "course";
            name = pack.getName();
            effective = pack.getStartsAt();
            entitlement = pack;
        } else if ("subscriptions".equals(type)) {
            var subscription = requireUnsignedSubscription(id);
            memberId = subscription.getMemberId();
            storeId = subscription.getStoreId();
            applicable = "card";
            name = "连续包月签约";
            effective = subscription.getRequestedAt();
            entitlement = subscriptionTerms(subscription);
        } else throw exception(CONTRACT_SOURCE_INVALID);
        scope.check(storeId);
        members.checkMemberStore(memberId, storeId);
        var existing =
                gymContractMapper.selectList(
                        new LambdaQueryWrapper<GymContractDO>()
                                .eq(GymContractDO::getSourceType, type)
                                .eq(GymContractDO::getSourceId, id)
                                .last("FOR UPDATE"));
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if ((templateId != null && !templateId.equals(row.getTemplateId()))
                    || (dueAt != null
                            && !dueAt.truncatedTo(ChronoUnit.MICROS).equals(row.getDueAt())))
                throw exception(CONTRACT_SOURCE_CONFLICT);
            return row;
        }
        var query =
                new LambdaQueryWrapper<GymContractTemplateDO>()
                        .eq(templateId != null, GymContractTemplateDO::getId, templateId)
                        .eq(GymContractTemplateDO::getApplicableType, applicable)
                        .eq(GymContractTemplateDO::getStatus, "published")
                        .orderByDesc(GymContractTemplateDO::getId)
                        .last("LIMIT 1 FOR UPDATE");
        var templates = gymContractTemplateMapper.selectList(query);
        if (templates.isEmpty()) {
            if (required) throw exception(CONTRACT_TEMPLATE_NOT_EXISTS);
            return null;
        }
        var template = templates.getFirst();
        var file = gymFileMapper.selectById(template.getFileId());
        if (file == null
                || !"contract".equals(file.getCategory())
                || !"application/pdf".equals(file.getMimeType()))
            throw exception(CONTRACT_TEMPLATE_FILE_INVALID);
        var content = fileApi.getFileContentByUrl(file.getUrl());
        if (content == null || content.length == 0) throw exception(FILE_CONTENT_NOT_EXISTS);
        if (content.length > 20 * 1024 * 1024
                || cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils.detect(
                                content)
                        != cn.iocoder.yudao.module.gym.enums.file.GymFileMimeTypeEnum.PDF)
            throw exception(CONTRACT_TEMPLATE_FILE_INVALID);
        var privateFile = new GymFileDO();
        privateFile.setStoreId(storeId);
        privateFile.setCategory("contract");
        privateFile.setName("contract-" + UUID.randomUUID() + ".pdf");
        privateFile.setMimeType("application/pdf");
        privateFile.setSize((long) content.length);
        privateFile.setUploaderId(SecurityFrameworkUtils.getLoginUserId());
        privateFile.setUrl(
                storageConfigId == null
                        ? fileApi.createFile(
                                content, privateFile.getName(), "gym/contract", "application/pdf")
                        : fileApi.createFile(
                                content,
                                privateFile.getName(),
                                "gym/contract",
                                "application/pdf",
                                storageConfigId));
        gymFileMapper.insert(privateFile);
        var row = new GymContractDO();
        row.setContractNo(UUID.randomUUID().toString());
        row.setName(name + "合同");
        row.setStoreId(storeId);
        row.setMemberId(memberId);
        row.setTemplateId(template.getId());
        row.setTemplateSnapshot(JsonUtils.toJsonString(template));
        var snapshot = new LinkedHashMap<String, Object>();
        var member = members.requireMember(memberId, false);
        snapshot.put("memberId", memberId.toString());
        snapshot.put("memberName", member.getName());
        snapshot.put("memberPhone", member.getPhone());
        snapshot.put(
                "entitlement",
                entitlement instanceof Map
                        ? entitlement
                        : cn.hutool.core.bean.BeanUtil.beanToMap(entitlement));
        row.setBusinessSnapshot(JsonUtils.toJsonString(snapshot));
        row.setSourceType(type);
        row.setSourceId(id);
        row.setUnsignedFileId(privateFile.getId());
        row.setStatus("unsigned");
        row.setApprovalStatus("unrequested");
        row.setSigningStatus("unconfigured");
        row.setDueAt((dueAt == null ? effective : dueAt).truncatedTo(ChronoUnit.MICROS));
        row.setIssuedAt(now());
        gymContractMapper.insert(row);
        row.setContractNo("GYM-CT-" + String.format(Locale.ROOT, "%010d", row.getId()));
        gymContractMapper.updateById(row);
        event(row.getId(), "issued", "合同原件与业务快照已保存，尚未签署");
        return row;
    }

    /** A signing review authorizes only the frozen local contract, never a debit mandate. */
    @Transactional(rollbackFor = Exception.class)
    public GymContractDO subscriptionSigningSource(Long id, Long storeId) {
        var subscription = requireUnsignedSubscription(id);
        if (!storeId.equals(subscription.getStoreId())) throw exception(APPROVAL_REQUEST_INVALID);
        return issue("subscriptions", id, null, null, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public GymContractDO approveSubscriptionSigning(
            Long contractId, Long subscriptionId, Long approvalId, Long originalFileId) {
        var subscription = requireUnsignedSubscription(subscriptionId);
        var contract = require(contractId, true);
        if (!"subscriptions".equals(contract.getSourceType())
                || !subscriptionId.equals(contract.getSourceId())
                || !subscription.getStoreId().equals(contract.getStoreId())
                || !subscription.getMemberId().equals(contract.getMemberId()))
            throw exception(CONTRACT_SOURCE_CONFLICT);
        return approveSigning(contractId, approvalId, originalFileId);
    }

    private cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO
            requireUnsignedSubscription(Long id) {
        var row =
                gymSubscriptionMapper.selectOne(
                        new LambdaQueryWrapper<
                                        cn.iocoder.yudao.module.gym.dal.dataobject.sales
                                                .GymSubscriptionDO>()
                                .eq(
                                        cn.iocoder.yudao.module.gym.dal.dataobject.sales
                                                        .GymSubscriptionDO
                                                ::getId,
                                        id)
                                .last("FOR UPDATE"));
        if (row == null) throw exception(SUBSCRIPTION_NOT_EXISTS);
        scope.check(row.getStoreId());
        members.checkMemberStore(row.getMemberId(), row.getStoreId());
        if (!Set.of("pending", "pending_authorization", "unconfigured", "failed")
                        .contains(row.getStatus())
                || row.getSignedAt() != null
                || row.getCancelledAt() != null) throw exception(CONTRACT_STATE_INVALID);
        return row;
    }

    private Map<String, Object> subscriptionTerms(
            cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO row) {
        var terms = new LinkedHashMap<String, Object>();
        terms.put("id", row.getId().toString());
        terms.put("memberId", row.getMemberId().toString());
        terms.put("storeId", row.getStoreId().toString());
        terms.put("cardTemplateId", row.getCardTemplateId().toString());
        terms.put("amount", row.getAmount());
        terms.put("billingDay", row.getBillingDay());
        terms.put("channel", row.getChannel());
        terms.put("requestedAt", row.getRequestedAt());
        return terms;
    }

    /** Approval authorizes signing, but never manufactures an e-signature or a provider delivery receipt. */
    @Transactional(rollbackFor = Exception.class)
    public GymContractDO approveSigning(Long id, Long approvalId, Long originalFileId) {
        var row = require(id, true);
        if (!"unsigned".equals(row.getStatus())
                || row.getSignedAt() != null
                || row.getCancelledAt() != null
                || !row.getUnsignedFileId().equals(originalFileId))
            throw exception(CONTRACT_STATE_INVALID);
        if (row.getApprovedApprovalId() != null) {
            if (row.getApprovedApprovalId().equals(approvalId)) return row;
            throw exception(CONTRACT_STATE_INVALID);
        }
        if ("subscriptions".equals(row.getSourceType())) {
            var subscription = requireUnsignedSubscription(row.getSourceId());
            var snapshot = JsonUtils.parseObject(row.getBusinessSnapshot(), Map.class);
            if (!subscription.getStoreId().equals(row.getStoreId())
                    || !subscription.getMemberId().equals(row.getMemberId())
                    || !JsonUtils.parseObject(
                                    JsonUtils.toJsonString(subscriptionTerms(subscription)),
                                    Map.class)
                            .equals(snapshot.get("entitlement")))
                throw exception(CONTRACT_SOURCE_CONFLICT);
        }
        row.setApprovalStatus("approved");
        row.setApprovedApprovalId(approvalId);
        row.setApprovedAt(now());
        row.setSigningStatus("unconfigured");
        gymContractMapper.updateById(row);
        // TODO-INTEGRATION-ESIGN: dispatch and verify the provider signature receipt after
        // configuration.
        event(id, "signing_approved", "签约审核已通过，电子签服务未配置，尚未发送或签署");
        return row;
    }

    public PageResult<GymContractReminderRespVO> reminders(Long id, GymBusinessPageReqVO req) {
        require(id, false);
        var page =
                gymContractReminderMapper.selectPage(
                        req,
                        new LambdaQueryWrapper<GymContractReminderDO>()
                                .eq(GymContractReminderDO::getContractId, id)
                                .orderByDesc(GymContractReminderDO::getId));
        return new PageResult<>(
                page.getList().stream()
                        .map(r -> BeanUtils.toBean(r, GymContractReminderRespVO.class))
                        .toList(),
                page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public GymContractReminderRespVO remind(Long id, GymContractReminderReqVO req, String key) {
        validate(req);
        if (key != null && (key.isBlank() || key.length() > 128 || key.startsWith("auto:")))
            throw exception(CONTRACT_REMINDER_INVALID);
        var row = require(id, true);
        if (key == null) key = UUID.randomUUID().toString();
        var old =
                gymContractReminderMapper.selectList(
                        new LambdaQueryWrapper<GymContractReminderDO>()
                                .eq(GymContractReminderDO::getContractId, id)
                                .eq(GymContractReminderDO::getRequestKey, key)
                                .last("FOR UPDATE"));
        if (!old.isEmpty()) {
            if (!req.getChannel().equals(old.getFirst().getChannel()))
                throw exception(CONTRACT_SOURCE_CONFLICT);
            return BeanUtils.toBean(old.getFirst(), GymContractReminderRespVO.class);
        }
        if (!Set.of("unsigned", "sent", "viewed").contains(row.getStatus()))
            throw exception(CONTRACT_STATE_INVALID);
        var reminder = new GymContractReminderDO();
        reminder.setContractId(id);
        reminder.setChannel(req.getChannel());
        reminder.setRequestKey(key);
        reminder.setScheduledAt(now());
        reminder.setCompletedAt(now());
        reminder.setRequestedBy(SecurityFrameworkUtils.getLoginUserId());
        // TODO-INTEGRATION-NOTIFICATION: connect configured channels and persist genuine delivery
        // results.
        reminder.setStatus("failed");
        reminder.setFailureReason(
                Map.of("sms", "短信", "miniapp", "小程序", "app", "App").get(req.getChannel())
                        + "提醒渠道未配置，未发送");
        gymContractReminderMapper.insert(reminder);
        event(id, "reminder_failed", reminder.getFailureReason());
        return BeanUtils.toBean(reminder, GymContractReminderRespVO.class);
    }

    private void event(Long id, String type, String detail) {
        var event = new GymContractEventDO();
        event.setContractId(id);
        event.setType(type);
        event.setOccurredAt(now());
        event.setActorUserId(SecurityFrameworkUtils.getLoginUserId());
        event.setDetail(detail);
        gymContractEventMapper.insert(event);
    }

    private Instant now() {
        return gymBusinessClock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private GymContractRespVO response(GymContractDO row) {
        var result = new GymContractRespVO();
        org.springframework.beans.BeanUtils.copyProperties(
                row,
                result,
                "id",
                "storeId",
                "memberId",
                "templateId",
                "sourceId",
                "unsignedFileId",
                "signedFileId",
                "approvedApprovalId",
                "businessSnapshot");
        result.setId(row.getId().toString());
        result.setStoreId(row.getStoreId().toString());
        result.setMemberId(row.getMemberId().toString());
        result.setTemplateId(row.getTemplateId().toString());
        result.setSourceId(row.getSourceId().toString());
        result.setUnsignedFileId(row.getUnsignedFileId().toString());
        result.setSignedFileId(
                row.getSignedFileId() == null ? null : row.getSignedFileId().toString());
        result.setApprovedApprovalId(
                row.getApprovedApprovalId() == null
                        ? null
                        : row.getApprovedApprovalId().toString());
        result.setStoreName(members.memberStoreName(row.getMemberId(), row.getStoreId()));
        result.setMemberName(members.requireMember(row.getMemberId(), false).getName());
        var publicSnapshot = JsonUtils.parseObject(row.getBusinessSnapshot(), Map.class);
        if (scope.readableStoreIds() != null) publicSnapshot.remove("entitlement");
        else if (publicSnapshot.get("entitlement") instanceof Map entitlement) {
            for (Object key : new ArrayList<>(entitlement.keySet())) {
                if (("id".equals(key) || key.toString().endsWith("Id"))
                        && entitlement.get(key) != null)
                    entitlement.put(key, entitlement.get(key).toString());
            }
        }
        result.setBusinessSnapshot(publicSnapshot);
        result.setEvents(
                gymContractEventMapper
                        .selectList(
                                new LambdaQueryWrapper<GymContractEventDO>()
                                        .eq(GymContractEventDO::getContractId, row.getId())
                                        .orderByAsc(GymContractEventDO::getId))
                        .stream()
                        .map(e -> BeanUtils.toBean(e, GymContractRespVO.Event.class))
                        .toList());
        return result;
    }
}
