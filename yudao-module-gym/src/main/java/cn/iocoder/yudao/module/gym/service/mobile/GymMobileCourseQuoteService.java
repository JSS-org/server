package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCourseQuoteVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.GymApprovalDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseQuoteDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.approval.GymApprovalMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileQuoteProperties;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.course.GymCourseService;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymMobileCourseQuoteService {
    @Resource private GymMobileWorkAccess work;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private GymCourseService courses;
    @Resource private GymMemberRecordsService records;
    @Resource private GymCourseQuoteMapper quotes;
    @Resource private GymCourseProductMapper products;
    @Resource private GymApprovalMapper approvals;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymStoreMapper stores;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymMobileQuoteProperties properties;
    @Resource private Validator validator;

    private record Payload(Long storeId, Long productId, Long memberId, Long approvalId) {}

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Quote create(Create req, String key) {
        Long actor = work.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        courses.checkQuoteApplicationAccess(
                req.getMemberId(), req.getProductId(), req.getStoreId());
        var storeIds = scope.readableStoreIds();
        boolean personal = storeIds != null && !storeIds.contains(req.getStoreId());
        if (personal && req.getApprovalId() == null) throw exception(STORE_NO_PERMISSION);
        // Cache replay never bypasses the current staff scope or approval ownership.
        if (req.getApprovalId() != null) approved(req, actor, personal, false);
        return idempotency.execute(
                "course-quote-create",
                key,
                new Payload(
                        req.getStoreId(),
                        req.getProductId(),
                        req.getMemberId(),
                        req.getApprovalId()),
                Quote.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    Map<String, Object> snapshot;
                    BigDecimal amount;
                    if (req.getApprovalId() != null) {
                        snapshot = approved(req, actor, personal, true);
                        var existing =
                                quotes.selectOne(
                                        GymCourseQuoteDO::getApprovalId, req.getApprovalId());
                        if (existing != null) return response(existing, clock.now());
                        courses.validateQuoteApproval(snapshot, req.getStoreId());
                        amount = new BigDecimal(snapshot.get("requestedPrice").toString());
                    } else {
                        snapshot =
                                courses.quoteApprovalSnapshot(
                                        req.getMemberId(), req.getProductId(), req.getStoreId());
                        amount = new BigDecimal(snapshot.get("originalPrice").toString());
                    }
                    var now = clock.now().truncatedTo(ChronoUnit.MICROS);
                    var row = new GymCourseQuoteDO();
                    row.setStoreId(req.getStoreId());
                    row.setProductId(req.getProductId());
                    row.setMemberId(req.getMemberId());
                    row.setApprovalId(req.getApprovalId());
                    row.setIssuerUserId(actor);
                    row.setAmount(amount);
                    row.setProductVersion(
                            Integer.valueOf(snapshot.get("productVersion").toString()));
                    row.setTermsSnapshot(JsonUtils.toJsonString(snapshot));
                    row.setIssuedAt(now);
                    row.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
                    quotes.insert(row);
                    return response(row, now);
                });
    }

    private Map<String, Object> approved(Create req, Long actor, boolean personal, boolean lock) {
        GymApprovalDO row =
                lock
                        ? approvals.selectForUpdate(req.getApprovalId())
                        : approvals.selectById(req.getApprovalId());
        if (row == null
                || !"approved".equals(row.getStatus())
                || !"price_change".equals(row.getBusinessType())
                || !Objects.equals(row.getStoreId(), req.getStoreId())
                || personal && !Objects.equals(row.getApplicantUserId(), actor))
            throw exception(COURSE_QUOTE_APPROVAL_INVALID);
        var snapshot = JsonUtils.parseObject(row.getRequestSnapshot(), Map.class);
        if (snapshot == null
                || !"course-quote-products".equals(snapshot.get("sourceType"))
                || !"quote".equals(snapshot.get("priceScope"))
                || !Objects.equals(
                        Objects.toString(snapshot.get("productId"), null),
                        req.getProductId().toString())
                || !Objects.equals(
                        Objects.toString(snapshot.get("memberId"), null),
                        Objects.toString(req.getMemberId(), null)))
            throw exception(COURSE_QUOTE_APPROVAL_INVALID);
        return snapshot;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Quote get(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        Long memberId = identities.requireMemberId();
        var row = quotes.selectById(id);
        if (row == null
                || row.getMemberId() != null && !memberId.equals(row.getMemberId())
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, memberId)
                                        .eq(GymMemberStoreDO::getStoreId, row.getStoreId()))
                        == 0) throw exception(COURSE_QUOTE_NOT_EXISTS);
        records.checkPurchase(memberId);
        return response(row, clock.now());
    }

    private Quote response(GymCourseQuoteDO row, Instant now) {
        String status =
                row.getUsedOrderId() != null
                        ? "used"
                        : !now.isBefore(row.getExpiresAt()) || !currentProduct(row, now)
                                ? "expired"
                                : "active";
        return new Quote(
                row.getId().toString(),
                row.getStoreId().toString(),
                row.getProductId().toString(),
                row.getMemberId() == null ? null : row.getMemberId().toString(),
                row.getAmount().setScale(2).toPlainString(),
                row.getExpiresAt().atZone(GymDateRange.ZONE).toOffsetDateTime().toString(),
                status,
                properties.getSharePage() + "?quoteId=" + row.getId());
    }

    private boolean currentProduct(GymCourseQuoteDO row, Instant now) {
        var product = products.selectById(row.getProductId());
        var store = stores.selectById(row.getStoreId());
        if (product == null
                || store == null
                || !"active".equals(store.getStatus())
                || !"active".equals(product.getStatus())
                || !"private".equals(product.getKind())
                || !Objects.equals(row.getStoreId(), product.getStoreId())
                || !Objects.equals(row.getProductVersion(), product.getVersion())) return false;
        var coach = employees.selectById(product.getCoachId());
        var today = now.atZone(GymDateRange.ZONE).toLocalDate();
        if (coach == null
                || !"active".equals(coach.getStatus())
                || coach.getHiredOn() == null
                || coach.getHiredOn().isAfter(today)) return false;
        return employments.selectCount(
                        new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                .eq(GymEmployeeStoreDO::getEmployeeId, coach.getId())
                                .eq(GymEmployeeStoreDO::getStoreId, row.getStoreId())
                                .le(GymEmployeeStoreDO::getJoinedOn, today))
                > 0;
    }
}
