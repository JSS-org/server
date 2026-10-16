package cn.iocoder.yudao.module.gym.service.member;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class GymMemberRecordsService {
    @Resource private GymBlacklistMapper gymBlacklistMapper;
    @Resource private GymMeasurementMapper gymMeasurementMapper;
    @Resource private GymFileMapper gymFileMapper;
    @Resource private GymMemberService members;
    @Resource private GymStoreService stores;
    @Resource private GymDataScope scope;
    @Resource private Validator validator;

    public PageResult<GymBlacklistRespVO> blacklists(GymBusinessPageReqVO req) {
        if (req.getStoreId() != null) members.checkMemberListStore(req.getStoreId());
        if (req.getMemberId() != null) members.requireMember(req.getMemberId(), false);
        var q =
                new LambdaQueryWrapper<GymBlacklistDO>()
                        .eq(
                                req.getMemberId() != null,
                                GymBlacklistDO::getMemberId,
                                req.getMemberId());
        String relation =
                "SELECT 1 FROM gym_member_store m WHERE m.member_id=gym_blacklist.member_id AND m.deleted=0 AND m.tenant_id={0} AND "
                        + members.memberPredicate("gym_blacklist.member_id", "m.store_id");
        if (req.getStoreId() != null)
            q.exists(
                    relation + " AND m.store_id={1}",
                    TenantContextHolder.getRequiredTenantId(),
                    req.getStoreId());
        else q.exists(relation, TenantContextHolder.getRequiredTenantId());
        var range = GymDateRange.of(req);
        q.ge(range.from() != null, GymBlacklistDO::getStartsAt, range.from())
                .lt(range.to() != null, GymBlacklistDO::getStartsAt, range.to());
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            q.like(GymBlacklistDO::getReason, req.getKeyword());
        if (req.getStatus() != null) {
            Instant now = Instant.now();
            switch (req.getStatus()) {
                case "revoked" -> q.isNotNull(GymBlacklistDO::getRevokedAt);
                case "active" ->
                        q.isNull(GymBlacklistDO::getRevokedAt)
                                .le(GymBlacklistDO::getStartsAt, now)
                                .and(
                                        w ->
                                                w.isNull(GymBlacklistDO::getEndsAt)
                                                        .or()
                                                        .gt(GymBlacklistDO::getEndsAt, now));
                case "expired" ->
                        q.isNull(GymBlacklistDO::getRevokedAt).le(GymBlacklistDO::getEndsAt, now);
                case "pending" ->
                        q.isNull(GymBlacklistDO::getRevokedAt).gt(GymBlacklistDO::getStartsAt, now);
                default -> throw exception(BLACKLIST_INVALID);
            }
        }
        q.orderByDesc(GymBlacklistDO::getId);
        var page = gymBlacklistMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::blacklistResponse).toList(), page.getTotal());
    }

    public GymBlacklistRespVO blacklist(Long id) {
        return blacklistResponse(requireBlacklist(id, false));
    }

    private GymBlacklistDO requireBlacklist(Long id, boolean lock) {
        var row = lock ? gymBlacklistMapper.selectForUpdate(id) : gymBlacklistMapper.selectById(id);
        if (row == null) throw exception(BLACKLIST_NOT_EXISTS);
        members.requireMember(row.getMemberId(), false);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public GymBlacklistRespVO createBlacklist(GymBlacklistSaveReqVO req) {
        scope.requireHeadquarters();
        validateBlacklist(req);
        members.requireMember(req.getMemberId(), true);
        if (Boolean.TRUE.equals(req.getRevoked())) throw exception(BLACKLIST_INVALID);
        var row = BeanUtils.toBean(req, GymBlacklistDO.class);
        gymBlacklistMapper.insert(row);
        return blacklistResponse(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public GymBlacklistRespVO patchBlacklist(Long id, GymBlacklistSaveReqVO patch) {
        scope.requireHeadquarters();
        var row = requireBlacklist(id, true);
        if (row.getRevokedAt() != null) {
            if (Boolean.TRUE.equals(patch.getRevoked())
                    && patch.getMemberId() == null
                    && patch.getStartsAt() == null
                    && patch.getBlockEntry() == null
                    && patch.getBlockPurchase() == null
                    && patch.getReason() == null
                    && !patch.isEndsAtPresent()) return blacklistResponse(row);
            throw exception(BLACKLIST_REVOKED);
        }
        if ((patch.getMemberId() != null && !patch.getMemberId().equals(row.getMemberId()))
                || (patch.getStartsAt() != null && !patch.getStartsAt().equals(row.getStartsAt())))
            throw exception(BLACKLIST_INVALID);
        var req = BeanUtils.toBean(row, GymBlacklistSaveReqVO.class);
        GymPatchUtils.merge(patch, req);
        if (patch.isEndsAtPresent()) req.setEndsAt(patch.getEndsAt());
        validateBlacklist(req);
        var update = BeanUtils.toBean(req, GymBlacklistDO.class);
        update.setId(id);
        if (Boolean.TRUE.equals(patch.getRevoked())) update.setRevokedAt(Instant.now());
        gymBlacklistMapper.updateById(update);
        return blacklist(id);
    }

    private void validateBlacklist(GymBlacklistSaveReqVO req) {
        validate(req);
        if ((!req.getBlockEntry() && !req.getBlockPurchase())
                || (req.getEndsAt() != null && !req.getEndsAt().isAfter(req.getStartsAt())))
            throw exception(BLACKLIST_INVALID);
    }

    /** Business services call this after authorizing the member; tenant filtering is mandatory. */
    public void checkEntry(Long memberId) {
        checkBlocked(memberId, true);
    }

    public void checkPurchase(Long memberId) {
        checkBlocked(memberId, false);
    }

    private void checkBlocked(Long memberId, boolean entry) {
        Instant now = Instant.now();
        long count =
                gymBlacklistMapper.selectCount(
                        new LambdaQueryWrapper<GymBlacklistDO>()
                                .eq(GymBlacklistDO::getMemberId, memberId)
                                .isNull(GymBlacklistDO::getRevokedAt)
                                .eq(
                                        entry
                                                ? GymBlacklistDO::getBlockEntry
                                                : GymBlacklistDO::getBlockPurchase,
                                        true)
                                .le(GymBlacklistDO::getStartsAt, now)
                                .and(
                                        w ->
                                                w.isNull(GymBlacklistDO::getEndsAt)
                                                        .or()
                                                        .gt(GymBlacklistDO::getEndsAt, now)));
        if (count > 0) throw exception(entry ? MEMBER_ENTRY_BLOCKED : MEMBER_PURCHASE_BLOCKED);
    }

    private GymBlacklistRespVO blacklistResponse(GymBlacklistDO row) {
        var result = BeanUtils.toBean(row, GymBlacklistRespVO.class);
        var member = members.requireMember(row.getMemberId(), false);
        result.setMemberName(member.getName());
        result.setStoreName(members.get(member.getId()).getStoreNames());
        Instant now = Instant.now();
        result.setStatus(
                row.getRevokedAt() != null
                        ? "revoked"
                        : row.getEndsAt() != null && !row.getEndsAt().isAfter(now)
                                ? "expired"
                                : row.getStartsAt().isAfter(now) ? "pending" : "active");
        return result;
    }

    public PageResult<GymMeasurementRespVO> measurements(GymBusinessPageReqVO req) {
        if (req.getStoreId() != null) members.checkMemberListStore(req.getStoreId());
        if (req.getMemberId() != null) members.requireMember(req.getMemberId(), false);
        var range = GymDateRange.of(req);
        var q =
                new LambdaQueryWrapper<GymMeasurementDO>()
                        .apply(
                                members.memberPredicate(
                                        "gym_measurement.member_id", "gym_measurement.store_id"))
                        .eq(
                                req.getStoreId() != null,
                                GymMeasurementDO::getStoreId,
                                req.getStoreId())
                        .eq(
                                req.getMemberId() != null,
                                GymMeasurementDO::getMemberId,
                                req.getMemberId())
                        .ge(range.from() != null, GymMeasurementDO::getMeasuredAt, range.from())
                        .lt(range.to() != null, GymMeasurementDO::getMeasuredAt, range.to())
                        .like(
                                req.getKeyword() != null && !req.getKeyword().isBlank(),
                                GymMeasurementDO::getNote,
                                req.getKeyword())
                        .orderByDesc(GymMeasurementDO::getMeasuredAt)
                        .orderByDesc(GymMeasurementDO::getId);
        var page = gymMeasurementMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::measurementResponse).toList(), page.getTotal());
    }

    public GymMeasurementRespVO measurement(Long id) {
        return measurementResponse(requireMeasurement(id, false));
    }

    private GymMeasurementDO requireMeasurement(Long id, boolean lock) {
        var row =
                lock
                        ? gymMeasurementMapper.selectForUpdate(id)
                        : gymMeasurementMapper.selectById(id);
        if (row == null) throw exception(MEASUREMENT_NOT_EXISTS);
        members.checkMemberScope(row.getMemberId(), row.getStoreId());
        members.requireMember(row.getMemberId(), false);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public GymMeasurementRespVO createMeasurement(GymMeasurementSaveReqVO req) {
        validateMeasurement(req);
        var row = BeanUtils.toBean(req, GymMeasurementDO.class);
        gymMeasurementMapper.insert(row);
        return measurementResponse(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public GymMeasurementRespVO patchMeasurement(Long id, GymMeasurementSaveReqVO patch) {
        var row = requireMeasurement(id, true);
        if ((patch.getStoreId() != null && !patch.getStoreId().equals(row.getStoreId()))
                || (patch.getMemberId() != null && !patch.getMemberId().equals(row.getMemberId()))
                || (patch.getMeasuredAt() != null
                        && !patch.getMeasuredAt().equals(row.getMeasuredAt())))
            throw exception(MEASUREMENT_INVALID);
        var req = BeanUtils.toBean(row, GymMeasurementSaveReqVO.class);
        GymPatchUtils.merge(patch, req);
        validateMeasurement(req);
        var update = BeanUtils.toBean(req, GymMeasurementDO.class);
        update.setId(id);
        gymMeasurementMapper.updateById(update);
        return measurement(id);
    }

    private void validateMeasurement(GymMeasurementSaveReqVO req) {
        validate(req);
        var member = members.requireMember(req.getMemberId(), false);
        try {
            members.checkMemberStore(member.getId(), req.getStoreId());
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            throw exception(MEASUREMENT_INVALID);
        }
        if (req.getReportFileId() != null) {
            var file = gymFileMapper.selectById(req.getReportFileId());
            var broad = scope.readableStoreIds();
            boolean selfOnly = broad != null && !broad.contains(req.getStoreId());
            if (file == null
                    || !req.getStoreId().equals(file.getStoreId())
                    || !"body_report".equals(file.getCategory()))
                throw exception(MEASUREMENT_FILE_INVALID);
            if (selfOnly) {
                if (gymMeasurementMapper.selectCount(
                                new LambdaQueryWrapper<GymMeasurementDO>()
                                        .eq(GymMeasurementDO::getReportFileId, file.getId())
                                        .ne(GymMeasurementDO::getMemberId, req.getMemberId()))
                        > 0) throw exception(MEASUREMENT_FILE_INVALID);
                boolean ownUpload =
                        java.util.Objects.equals(
                                file.getUploaderId(),
                                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils
                                        .getLoginUserId());
                // Preserve/reuse an already-bound report for this same member, including an
                // administrator's upload.
                if (!ownUpload
                        && gymMeasurementMapper.selectCount(
                                        new LambdaQueryWrapper<GymMeasurementDO>()
                                                .eq(GymMeasurementDO::getReportFileId, file.getId())
                                                .eq(
                                                        GymMeasurementDO::getMemberId,
                                                        req.getMemberId())
                                                .eq(GymMeasurementDO::getStoreId, req.getStoreId()))
                                == 0) throw exception(MEASUREMENT_FILE_INVALID);
            }
        }
    }

    private GymMeasurementRespVO measurementResponse(GymMeasurementDO row) {
        var result = BeanUtils.toBean(row, GymMeasurementRespVO.class);
        result.setMemberName(members.requireMember(row.getMemberId(), false).getName());
        result.setStoreName(members.memberStoreName(row.getMemberId(), row.getStoreId()));
        return result;
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
}
