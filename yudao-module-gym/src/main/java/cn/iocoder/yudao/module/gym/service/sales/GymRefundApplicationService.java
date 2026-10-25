package cn.iocoder.yudao.module.gym.service.sales;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRefundVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.mobile.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Refund requests authorize review, never fabricate provider success or revoke rights on submission. */
@Service
public class GymRefundApplicationService {
    @Resource private GymRefundApplicationMapper applications;
    @Resource private GymBusinessOrderMapper orders;
    @Resource private GymOrderItemMapper items;
    @Resource private GymOrderPaymentMapper payments;
    @Resource private GymOrderRefundMapper refunds;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileWorkAccess work;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock clock;
    @Resource private GymFulfillmentQueueService queue;
    @Resource private Validator validator;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private record Request(Long orderId, Long memberId, String reason) {}

    private record ReviewRequest(Long id, int version, String decision, String note) {}

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private GymBusinessOrderDO order(Long id, boolean lock) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var q = new LambdaQueryWrapper<GymBusinessOrderDO>().eq(GymBusinessOrderDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var order = orders.selectOne(q);
        if (order == null
                || !Set.of("card_sale", "course_sale").contains(order.getBusinessType())
                || !Set.of("miniapp", "app").contains(order.getChannel()))
            throw exception(SALES_ORDER_NOT_EXISTS);
        var lines =
                items.selectList(
                        new LambdaQueryWrapper<GymOrderItemDO>()
                                .eq(GymOrderItemDO::getOrderId, id));
        if (lines.size() != 1
                || !(lines.getFirst().getProductType() + "_sale").equals(order.getBusinessType()))
            throw exception(SALES_ORDER_NOT_EXISTS);
        return order;
    }

    private GymBusinessOrderDO ownOrder(Long id, Long memberId, Long userId, boolean lock) {
        var order = order(id, lock);
        if (!memberId.equals(order.getMemberId())
                || order.getBuyerUserId() != null && !userId.equals(order.getBuyerUserId()))
            throw exception(SALES_ORDER_NOT_EXISTS);
        return order;
    }

    private GymRefundApplicationDO application(Long id, boolean lock) {
        var q =
                new LambdaQueryWrapper<GymRefundApplicationDO>()
                        .eq(GymRefundApplicationDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = applications.selectOne(q);
        if (row == null) throw exception(REFUND_APPLICATION_NOT_EXISTS);
        return row;
    }

    private GymRefundApplicationDO admin(Long id) {
        work.requireAdmin();
        var row = application(id, false);
        scope.check(row.getStoreId());
        return row;
    }

    private BigDecimal remaining(GymBusinessOrderDO order) {
        if (!Set.of(
                        "partially_paid",
                        "paid",
                        "pending_fulfillment",
                        "fulfilled",
                        "partially_refunded")
                .contains(order.getStatus())) throw exception(REFUND_APPLICATION_INVALID);
        BigDecimal paid =
                payments
                        .selectList(
                                new LambdaQueryWrapper<GymOrderPaymentDO>()
                                        .eq(GymOrderPaymentDO::getOrderId, order.getId())
                                        .eq(GymOrderPaymentDO::getStatus, "paid"))
                        .stream()
                        .map(GymOrderPaymentDO::getAmount)
                        .reduce(ZERO, BigDecimal::add);
        BigDecimal refunded =
                refunds
                        .selectList(
                                new LambdaQueryWrapper<GymOrderRefundDO>()
                                        .eq(GymOrderRefundDO::getOrderId, order.getId())
                                        .eq(GymOrderRefundDO::getStatus, "succeeded"))
                        .stream()
                        .map(GymOrderRefundDO::getAmount)
                        .reduce(ZERO, BigDecimal::add);
        if (paid.compareTo(order.getPaidAmount()) != 0
                || refunded.compareTo(order.getRefundedAmount()) != 0
                || paid.compareTo(order.getPayableAmount()) > 0
                || refunded.signum() < 0
                || paid.subtract(refunded).signum() <= 0)
            throw exception(REFUND_APPLICATION_INVALID);
        return paid.subtract(refunded);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Submitted create(Long orderId, Create req, String key) {
        validate(req);
        var identity = identities.requireIdentity();
        ownOrder(orderId, identity.getMemberId(), identity.getUserId(), false);
        String reason = req.getReason().trim();
        if (reason.isBlank()) throw exception(MOBILE_REQUEST_INVALID);
        return idempotency.execute(
                "refund-application-create",
                key,
                new Request(orderId, identity.getMemberId(), reason),
                Submitted.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var order =
                            ownOrder(
                                    orderId,
                                    identities.requireMemberId(),
                                    identities.requireUser().getId(),
                                    true);
                    var old =
                            applications.selectOne(
                                    new LambdaQueryWrapper<GymRefundApplicationDO>()
                                            .eq(GymRefundApplicationDO::getActiveOrderId, orderId)
                                            .last("FOR UPDATE"));
                    if (old != null) {
                        if (!identity.getUserId().equals(old.getRequesterUserId())
                                || !reason.equals(old.getReason()))
                            throw exception(REFUND_APPLICATION_OPEN);
                        return submitted(old);
                    }
                    var amount = remaining(order);
                    var row = new GymRefundApplicationDO();
                    row.setOrderId(orderId);
                    row.setActiveOrderId(orderId);
                    row.setStoreId(order.getStoreId());
                    row.setMemberId(identity.getMemberId());
                    row.setRequesterUserId(identity.getUserId());
                    row.setRequestedAmount(amount);
                    row.setRefundedAmount(ZERO);
                    row.setReason(reason);
                    row.setStatus("pending_review");
                    row.setVersion(1);
                    row.setSubmittedAt(now());
                    applications.insert(row);
                    queue.holdForRefund(orderId);
                    return submitted(row);
                });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Application get(Long id) {
        var identity = identities.requireIdentity();
        var row = application(id, false);
        if (!identity.getMemberId().equals(row.getMemberId())
                || !identity.getUserId().equals(row.getRequesterUserId()))
            throw exception(REFUND_APPLICATION_NOT_EXISTS);
        return new Application(
                row.getId().toString(),
                row.getOrderId().toString(),
                row.getStatus(),
                money(row.getRequestedAmount()),
                money(row.getRefundedAmount()),
                row.getReason(),
                row.getFailureReason());
    }

    public Review review(Long id) {
        return reviewResponse(admin(id));
    }

    public PageResult<Review> page(Query req) {
        validate(req);
        work.requireAdmin();
        var ids = scope.readableStoreIds();
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var q = new LambdaQueryWrapper<GymRefundApplicationDO>();
        if (ids != null) {
            if (ids.isEmpty()) q.apply("1=0");
            else q.in(GymRefundApplicationDO::getStoreId, ids);
        }
        q.eq(req.getStoreId() != null, GymRefundApplicationDO::getStoreId, req.getStoreId())
                .eq(req.getOrderId() != null, GymRefundApplicationDO::getOrderId, req.getOrderId())
                .eq(req.getStatus() != null, GymRefundApplicationDO::getStatus, req.getStatus())
                .orderByDesc(GymRefundApplicationDO::getId);
        var result = applications.selectPage(req, q);
        return new PageResult<>(
                result.getList().stream().map(this::reviewResponse).toList(), result.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Review decide(Long id, Decision req, String key) {
        validate(req);
        Long actor = work.requireAdmin();
        var visible = admin(id);
        String note = req.getNote().trim();
        if (note.isBlank()) throw exception(MOBILE_REQUEST_INVALID);
        return idempotency.execute(
                "refund-application-decide",
                key,
                new ReviewRequest(id, req.getExpectedVersion(), req.getDecision(), note),
                Review.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var order = order(visible.getOrderId(), true);
                    scope.check(order.getStoreId());
                    var row = application(id, true);
                    if (!row.getVersion().equals(req.getExpectedVersion()))
                        throw exception(MOBILE_VERSION_CONFLICT);
                    if (!"pending_review".equals(row.getStatus()))
                        throw exception(REFUND_APPLICATION_INVALID);
                    if ("approve".equals(req.getDecision())) {
                        if (remaining(order).compareTo(row.getRequestedAmount()) < 0)
                            throw exception(REFUND_APPLICATION_INVALID);
                        row.setStatus("approved");
                    } else {
                        row.setStatus("rejected");
                        row.setFailureReason(note);
                        row.setActiveOrderId(null);
                    }
                    row.setReviewerUserId(actor);
                    row.setReviewedAt(now());
                    row.setReviewNote(note);
                    row.setVersion(row.getVersion() + 1);
                    applications.updateById(row);
                    if ("rejected".equals(row.getStatus())) queue.resumeAfterRefundRejection(order);
                    return reviewResponse(row);
                });
    }

    /** Internal authenticated adapter precheck; no public endpoint can assert a refund succeeded. */
    public void checkForVerification(Long id, Long orderId) {
        var row = admin(id);
        if (!orderId.equals(row.getOrderId())
                || !Set.of("approved", "partially_refunded", "refunded", "closed")
                        .contains(row.getStatus())) throw exception(REFUND_APPLICATION_INVALID);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateReceipt(
            Long id, GymBusinessOrderDO order, BigDecimal amount, Instant occurredAt) {
        work.requireAdmin();
        scope.check(order.getStoreId());
        var row = application(id, true);
        if (!order.getId().equals(row.getOrderId())
                || !Set.of("approved", "partially_refunded").contains(row.getStatus())
                || occurredAt.isBefore(row.getReviewedAt())
                || amount.signum() <= 0
                || amount.compareTo(row.getRequestedAmount().subtract(row.getRefundedAmount())) > 0)
            throw exception(REFUND_APPLICATION_INVALID);
    }

    /** Called only for a new, authenticated receipt, in the same order/refund/rights transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRefund(GymBusinessOrderDO order, GymOrderRefundDO refund) {
        if (refund.getRefundApplicationId() != null) {
            var row = application(refund.getRefundApplicationId(), true);
            row.setRefundedAmount(row.getRefundedAmount().add(refund.getAmount()));
            row.setStatus(
                    row.getRefundedAmount().compareTo(row.getRequestedAmount()) == 0
                            ? "refunded"
                            : "partially_refunded");
            if ("refunded".equals(row.getStatus())) row.setActiveOrderId(null);
            row.setVersion(row.getVersion() + 1);
            applications.updateById(row);
        }
        var active =
                applications.selectOne(
                        new LambdaQueryWrapper<GymRefundApplicationDO>()
                                .eq(GymRefundApplicationDO::getActiveOrderId, order.getId())
                                .last("FOR UPDATE"));
        if (active != null
                && order.getPaidAmount()
                                .subtract(order.getRefundedAmount())
                                .compareTo(
                                        active.getRequestedAmount()
                                                .subtract(active.getRefundedAmount()))
                        < 0) {
            active.setStatus("closed");
            active.setActiveOrderId(null);
            active.setFailureReason("订单可退余额已变化，请核实后重新申请");
            active.setVersion(active.getVersion() + 1);
            applications.updateById(active);
        }
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    private String time(Instant at) {
        return at == null ? null : at.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private Submitted submitted(GymRefundApplicationDO row) {
        return new Submitted(
                row.getId().toString(), row.getStatus(), money(row.getRequestedAmount()));
    }

    private Review reviewResponse(GymRefundApplicationDO row) {
        return new Review(
                row.getId().toString(),
                row.getOrderId().toString(),
                row.getStoreId().toString(),
                row.getMemberId().toString(),
                row.getStatus(),
                money(row.getRequestedAmount()),
                money(row.getRefundedAmount()),
                row.getReason(),
                row.getFailureReason(),
                row.getVersion(),
                time(row.getSubmittedAt()),
                time(row.getReviewedAt()),
                row.getReviewNote());
    }
}
