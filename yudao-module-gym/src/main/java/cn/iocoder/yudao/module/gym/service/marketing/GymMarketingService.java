package cn.iocoder.yudao.module.gym.service.marketing;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.marketing.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.marketing.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberService;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMarketingService {
    @Resource private GymActivityMapper gymActivityMapper;
    @Resource private GymActivityStoreMapper gymActivityStoreMapper;
    @Resource private GymActivityRegistrationMapper gymActivityRegistrationMapper;
    @Resource private GymFranchiseInquiryMapper gymFranchiseInquiryMapper;
    @Resource private GymFileMapper gymFileMapper;
    @Resource private GymStoreService stores;
    @Resource private GymMemberService members;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private List<Long> activityStores(Long id) {
        return gymActivityStoreMapper
                .selectList(
                        new LambdaQueryWrapper<GymActivityStoreDO>()
                                .eq(GymActivityStoreDO::getActivityId, id)
                                .orderByAsc(GymActivityStoreDO::getStoreId))
                .stream()
                .map(GymActivityStoreDO::getStoreId)
                .toList();
    }

    private List<Long> visibleStores(Long id) {
        var ids = activityStores(id);
        var allowed = scope.readableStoreIds();
        return ids.stream().filter(s -> allowed == null || allowed.contains(s)).toList();
    }

    private GymActivityDO requireActivity(Long id, boolean lock) {
        var q = new LambdaQueryWrapper<GymActivityDO>().eq(GymActivityDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = gymActivityMapper.selectOne(q);
        if (row == null) throw exception(ACTIVITY_NOT_EXISTS);
        if (visibleStores(id).isEmpty()) throw exception(STORE_NO_PERMISSION);
        return row;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<GymActivityRespVO> activities(GymBusinessPageReqVO req) {
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return new PageResult<>(List.of(), 0L);
        var range = GymDateRange.of(req);
        String relation =
                "SELECT 1 FROM gym_activity_store ast WHERE ast.activity_id=gym_activity.id AND ast.deleted=0 AND ast.tenant_id={0}";
        var q =
                new LambdaQueryWrapper<GymActivityDO>()
                        .eq(req.getStatus() != null, GymActivityDO::getStatus, req.getStatus())
                        .like(
                                req.getKeyword() != null && !req.getKeyword().isBlank(),
                                GymActivityDO::getName,
                                req.getKeyword())
                        .ge(range.from() != null, GymActivityDO::getStartsAt, range.from())
                        .lt(range.to() != null, GymActivityDO::getStartsAt, range.to())
                        .orderByDesc(GymActivityDO::getId);
        if (req.getStoreId() != null)
            q.exists(
                    relation + " AND ast.store_id={1}",
                    TenantContextHolder.getRequiredTenantId(),
                    req.getStoreId());
        else if (allowed != null)
            q.exists(
                    relation
                            + " AND ast.store_id IN ("
                            + allowed.stream().map(String::valueOf).collect(Collectors.joining(","))
                            + ")",
                    TenantContextHolder.getRequiredTenantId());
        var page = gymActivityMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::activityResponse).toList(), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymActivityRespVO activity(Long id) {
        return activityResponse(requireActivity(id, false));
    }

    private void validateActivity(GymActivitySaveReqVO req) {
        validate(req);
        if (new HashSet<>(req.getStoreIds()).size() != req.getStoreIds().size())
            throw exception(ACTIVITY_INVALID);
        req.setRegistrationStartsAt(req.getRegistrationStartsAt().truncatedTo(ChronoUnit.MICROS));
        req.setRegistrationEndsAt(req.getRegistrationEndsAt().truncatedTo(ChronoUnit.MICROS));
        req.setStartsAt(req.getStartsAt().truncatedTo(ChronoUnit.MICROS));
        req.setEndsAt(req.getEndsAt().truncatedTo(ChronoUnit.MICROS));
        if (!req.getRegistrationEndsAt().isAfter(req.getRegistrationStartsAt())
                || req.getRegistrationEndsAt().isAfter(req.getStartsAt())
                || !req.getEndsAt().isAfter(req.getStartsAt())) throw exception(ACTIVITY_INVALID);
        req.getStoreIds().forEach(s -> stores.requireStore(s, false));
        if (req.getCoverFileId() != null) {
            var file = gymFileMapper.selectById(req.getCoverFileId());
            if (file == null
                    || !"image".equals(file.getCategory())
                    || file.getMimeType() == null
                    || !file.getMimeType().startsWith("image/")
                    || !req.getStoreIds().contains(file.getStoreId()))
                throw exception(ACTIVITY_COVER_INVALID);
        }
    }

    private void replaceActivityStores(Long activity, List<Long> ids) {
        gymActivityStoreMapper.deleteRelations(activity);
        for (Long id : new TreeSet<>(ids)) {
            var link = new GymActivityStoreDO();
            link.setActivityId(activity);
            link.setStoreId(id);
            gymActivityStoreMapper.insert(link);
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymActivityRespVO createActivity(GymActivitySaveReqVO req) {
        if (req.getStatus() != null && !"draft".equals(req.getStatus()))
            throw exception(ACTIVITY_STATE_INVALID);
        req.setStatus("draft");
        if (req.getDescription() == null) req.setDescription("");
        validateActivity(req);
        scope.checkAll(req.getStoreIds());
        var row = BeanUtils.toBean(req, GymActivityDO.class);
        gymActivityMapper.insert(row);
        replaceActivityStores(row.getId(), req.getStoreIds());
        return activityResponse(row);
    }

    private boolean content(GymActivitySaveReqVO req) {
        return req.getName() != null
                || req.getDescription() != null
                || req.getStoreIds() != null
                || req.getRegistrationStartsAt() != null
                || req.getRegistrationEndsAt() != null
                || req.getStartsAt() != null
                || req.getEndsAt() != null
                || req.isCapacityPresent()
                || req.isCoverFileIdPresent();
    }

    private long confirmed(Long id) {
        return gymActivityRegistrationMapper.selectCount(
                new LambdaQueryWrapper<GymActivityRegistrationDO>()
                        .eq(GymActivityRegistrationDO::getActivityId, id)
                        .eq(GymActivityRegistrationDO::getStatus, "confirmed"));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymActivityRespVO patchActivity(Long id, GymActivitySaveReqVO patch) {
        var row = requireActivity(id, true);
        var original = activityStores(id);
        scope.checkAll(original);
        if (Set.of("closed", "cancelled").contains(row.getStatus())) {
            if (content(patch)
                    || patch.getStatus() != null && !patch.getStatus().equals(row.getStatus()))
                throw exception(ACTIVITY_STATE_INVALID);
            return activityResponse(row);
        }
        var req = BeanUtils.toBean(row, GymActivitySaveReqVO.class);
        req.setStoreIds(original);
        GymPatchUtils.merge(patch, req);
        if (patch.isCapacityPresent()) req.setCapacity(patch.getCapacity());
        if (patch.isCoverFileIdPresent()) req.setCoverFileId(patch.getCoverFileId());
        validateActivity(req);
        scope.checkAll(req.getStoreIds());
        long count = confirmed(id);
        if (req.getCapacity() != null && req.getCapacity() < count)
            throw exception(ACTIVITY_CAPACITY_INVALID);
        long history =
                gymActivityRegistrationMapper.selectCount(
                        new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                .eq(GymActivityRegistrationDO::getActivityId, id));
        if (history > 0
                && (!new HashSet<>(original).equals(new HashSet<>(req.getStoreIds()))
                        || !row.getRegistrationStartsAt().equals(req.getRegistrationStartsAt())
                        || !row.getRegistrationEndsAt().equals(req.getRegistrationEndsAt())
                        || !row.getStartsAt().equals(req.getStartsAt())
                        || !row.getEndsAt().equals(req.getEndsAt())))
            throw exception(ACTIVITY_REGISTERED_IMMUTABLE);
        String before = row.getStatus(), after = req.getStatus();
        if (!before.equals(after)) {
            boolean transition =
                    "draft".equals(before) && Set.of("published", "cancelled").contains(after)
                            || "published".equals(before)
                                    && Set.of("closed", "cancelled").contains(after);
            if (!transition || "cancelled".equals(after) && !now().isBefore(row.getEndsAt()))
                throw exception(ACTIVITY_STATE_INVALID);
            if ("published".equals(after)) {
                if (!now().isBefore(req.getEndsAt())
                        || req.getStoreIds().stream()
                                .anyMatch(s -> !"active".equals(stores.get(s).getStatus())))
                    throw exception(ACTIVITY_STATE_INVALID);
            }
        }
        var updated = BeanUtils.toBean(req, GymActivityDO.class);
        updated.setId(id);
        gymActivityMapper.updateById(updated);
        if (!original.equals(req.getStoreIds())) replaceActivityStores(id, req.getStoreIds());
        if ("cancelled".equals(after) && !before.equals(after))
            for (var registration :
                    gymActivityRegistrationMapper.selectList(
                            new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                    .eq(GymActivityRegistrationDO::getActivityId, id)
                                    .eq(GymActivityRegistrationDO::getStatus, "confirmed"))) {
                registration.setStatus("cancelled");
                registration.setCancelledAt(now());
                gymActivityRegistrationMapper.updateById(registration);
            }
        return activityResponse(updated);
    }

    private GymActivityRespVO activityResponse(GymActivityDO row) {
        var result = BeanUtils.toBean(row, GymActivityRespVO.class);
        var ids = visibleStores(row.getId());
        result.setStoreIds(ids.stream().map(String::valueOf).toList());
        result.setStoreNames(
                ids.stream().map(s -> stores.get(s).getName()).collect(Collectors.joining("、")));
        result.setRegistrationCount(
                ids.isEmpty()
                        ? 0L
                        : gymActivityRegistrationMapper.selectCount(
                                new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                        .eq(GymActivityRegistrationDO::getActivityId, row.getId())
                                        .eq(GymActivityRegistrationDO::getStatus, "confirmed")
                                        .in(GymActivityRegistrationDO::getStoreId, ids)));
        if (row.getCoverFileId() != null) {
            var file = gymFileMapper.selectById(row.getCoverFileId());
            var allowed = scope.readableStoreIds();
            if (file == null || allowed != null && !allowed.contains(file.getStoreId()))
                result.setCoverFileId(null);
        }
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<GymActivityRegistrationRespVO> registrations(
            Long activity, GymBusinessPageReqVO req) {
        requireActivity(activity, false);
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return new PageResult<>(List.of(), 0L);
        var range = GymDateRange.of(req);
        var q =
                new LambdaQueryWrapper<GymActivityRegistrationDO>()
                        .eq(GymActivityRegistrationDO::getActivityId, activity)
                        .in(allowed != null, GymActivityRegistrationDO::getStoreId, allowed)
                        .eq(
                                req.getStoreId() != null,
                                GymActivityRegistrationDO::getStoreId,
                                req.getStoreId())
                        .eq(
                                req.getStatus() != null,
                                GymActivityRegistrationDO::getStatus,
                                req.getStatus())
                        .ge(
                                range.from() != null,
                                GymActivityRegistrationDO::getRegisteredAt,
                                range.from())
                        .lt(
                                range.to() != null,
                                GymActivityRegistrationDO::getRegisteredAt,
                                range.to())
                        .orderByDesc(GymActivityRegistrationDO::getId);
        var page = gymActivityRegistrationMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream()
                        .map(r -> BeanUtils.toBean(r, GymActivityRegistrationRespVO.class))
                        .toList(),
                page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymActivityRegistrationRespVO register(Long activity, GymActivityRegistrationReqVO req) {
        validate(req);
        var row = requireActivity(activity, true);
        scope.check(req.getStoreId());
        if (!activityStores(activity).contains(req.getStoreId()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        var member = members.requireMember(req.getMemberId(), true);
        members.checkMemberStore(req.getMemberId(), req.getStoreId());
        if (!"active".equals(member.getStatus())
                || !"active".equals(stores.get(req.getStoreId()).getStatus()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        var registration =
                gymActivityRegistrationMapper.selectOne(
                        new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                .eq(GymActivityRegistrationDO::getActivityId, activity)
                                .eq(GymActivityRegistrationDO::getMemberId, req.getMemberId()));
        if (registration != null && !req.getStoreId().equals(registration.getStoreId()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        if (registration != null && "confirmed".equals(registration.getStatus()))
            return BeanUtils.toBean(registration, GymActivityRegistrationRespVO.class);
        Instant now = now();
        if (!"published".equals(row.getStatus())
                || now.isBefore(row.getRegistrationStartsAt())
                || !now.isBefore(row.getRegistrationEndsAt()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        if (row.getCapacity() != null && confirmed(activity) >= row.getCapacity())
            throw exception(ACTIVITY_CAPACITY_INVALID);
        boolean create = registration == null;
        if (create) registration = new GymActivityRegistrationDO();
        registration.setActivityId(activity);
        registration.setMemberId(member.getId());
        registration.setMemberName(member.getName());
        registration.setStoreId(req.getStoreId());
        registration.setStoreName(stores.get(req.getStoreId()).getName());
        registration.setStatus("confirmed");
        registration.setRegisteredAt(now);
        registration.setCancelledAt(null);
        if (create) gymActivityRegistrationMapper.insert(registration);
        else gymActivityRegistrationMapper.updateById(registration);
        return BeanUtils.toBean(registration, GymActivityRegistrationRespVO.class);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymActivityRegistrationRespVO cancelRegistration(Long activity, Long id) {
        var row = requireActivity(activity, true);
        var registration = gymActivityRegistrationMapper.selectById(id);
        if (registration == null || !activity.equals(registration.getActivityId()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        scope.check(registration.getStoreId());
        if ("cancelled".equals(registration.getStatus()))
            return BeanUtils.toBean(registration, GymActivityRegistrationRespVO.class);
        if (!"published".equals(row.getStatus()) || !now().isBefore(row.getStartsAt()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        registration.setStatus("cancelled");
        registration.setCancelledAt(now());
        gymActivityRegistrationMapper.updateById(registration);
        return BeanUtils.toBean(registration, GymActivityRegistrationRespVO.class);
    }

    public PageResult<GymFranchiseRespVO> inquiries(GymBusinessPageReqVO req) {
        scope.requireHeadquarters();
        var range = GymDateRange.of(req);
        var q =
                new LambdaQueryWrapper<GymFranchiseInquiryDO>()
                        .eq(
                                req.getStatus() != null,
                                GymFranchiseInquiryDO::getStatus,
                                req.getStatus())
                        .ge(
                                range.from() != null,
                                GymFranchiseInquiryDO::getReceivedAt,
                                range.from())
                        .lt(range.to() != null, GymFranchiseInquiryDO::getReceivedAt, range.to())
                        .orderByDesc(GymFranchiseInquiryDO::getId);
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            q.and(
                    w ->
                            w.like(GymFranchiseInquiryDO::getContactName, req.getKeyword())
                                    .or()
                                    .like(GymFranchiseInquiryDO::getPhone, req.getKeyword())
                                    .or()
                                    .like(
                                            GymFranchiseInquiryDO::getPreferredCity,
                                            req.getKeyword()));
        var page = gymFranchiseInquiryMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream()
                        .map(r -> BeanUtils.toBean(r, GymFranchiseRespVO.class))
                        .toList(),
                page.getTotal());
    }

    private GymFranchiseInquiryDO requireInquiry(Long id, boolean lock) {
        scope.requireHeadquarters();
        var q =
                new LambdaQueryWrapper<GymFranchiseInquiryDO>()
                        .eq(GymFranchiseInquiryDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = gymFranchiseInquiryMapper.selectOne(q);
        if (row == null) throw exception(FRANCHISE_NOT_EXISTS);
        return row;
    }

    public GymFranchiseRespVO inquiry(Long id) {
        return BeanUtils.toBean(requireInquiry(id, false), GymFranchiseRespVO.class);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymFranchiseRespVO createInquiry(GymFranchiseSubmitReqVO req) {
        scope.requireHeadquarters();
        Long id = submitInquiry(req, "admin:" + UUID.randomUUID(), "admin");
        return inquiry(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long submitInquiry(GymFranchiseSubmitReqVO req, String key, String channel) {
        validate(req);
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}"))
            throw exception(FRANCHISE_REQUEST_INVALID);
        var content = new LinkedHashMap<String, Object>();
        content.put("contactName", req.getContactName().trim());
        content.put("phone", req.getPhone());
        content.put("preferredCity", req.getPreferredCity().trim());
        content.put("message", req.getMessage() == null ? "" : req.getMessage());
        String hash = SecureUtil.sha256(JsonUtils.toJsonString(content));
        var existing =
                gymFranchiseInquiryMapper.selectOne(
                        new LambdaQueryWrapper<GymFranchiseInquiryDO>()
                                .eq(GymFranchiseInquiryDO::getRequestKey, key));
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash()))
                throw exception(FRANCHISE_REQUEST_CONFLICT);
            return existing.getId();
        }
        var row = BeanUtils.toBean(req, GymFranchiseInquiryDO.class);
        row.setContactName(req.getContactName().trim());
        row.setPreferredCity(req.getPreferredCity().trim());
        row.setMessage(req.getMessage() == null ? "" : req.getMessage());
        row.setStatus("new");
        row.setReceivedAt(now());
        row.setRequestKey(key);
        row.setRequestHash(hash);
        row.setChannel(channel);
        if (SecurityFrameworkUtils.getLoginUserId() == null) {
            row.setCreator("");
            row.setUpdater("");
        }
        try {
            gymFranchiseInquiryMapper.insert(row);
        } catch (DuplicateKeyException e) {
            existing =
                    gymFranchiseInquiryMapper.selectOne(
                            new LambdaQueryWrapper<GymFranchiseInquiryDO>()
                                    .eq(GymFranchiseInquiryDO::getRequestKey, key));
            if (existing == null || !hash.equals(existing.getRequestHash()))
                throw exception(FRANCHISE_REQUEST_CONFLICT);
            return existing.getId();
        }
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymFranchiseRespVO patchInquiry(Long id, GymFranchisePatchReqVO req) {
        validate(req);
        var row = requireInquiry(id, true);
        if (row.getStatus().equals(req.getStatus()))
            return BeanUtils.toBean(row, GymFranchiseRespVO.class);
        if ("closed".equals(row.getStatus()) || "new".equals(req.getStatus()))
            throw exception(FRANCHISE_STATE_INVALID);
        row.setStatus(req.getStatus());
        row.setHandledBy(SecurityFrameworkUtils.getLoginUserId());
        if ("contacted".equals(req.getStatus())) row.setContactedAt(now());
        else row.setClosedAt(now());
        gymFranchiseInquiryMapper.updateById(row);
        return BeanUtils.toBean(row, GymFranchiseRespVO.class);
    }
}
