package cn.iocoder.yudao.module.gym.service.sales;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymFulfillmentTaskRespVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Durable queue: database leases protect workers; the business operation remains idempotent under the order lock. */
@Service
public class GymFulfillmentQueueService {
    @Resource private GymFulfillmentTaskMapper gymFulfillmentTaskMapper;
    @Resource private GymBusinessOrderMapper gymBusinessOrderMapper;
    @Resource private GymOrderPolicyMapper gymOrderPolicyMapper;
    @Resource private GymRefundApplicationMapper refundApplications;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock clock;

    @Resource
    private cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService identities;

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Long principal() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null
                || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || user.getId() == null) throw exception(COMMERCE_REQUEST_INVALID);
        return user.getId();
    }

    private GymFulfillmentTaskDO task(Long orderId, boolean lock) {
        var q =
                new LambdaQueryWrapper<GymFulfillmentTaskDO>()
                        .eq(GymFulfillmentTaskDO::getOrderId, orderId);
        if (lock) q.last("FOR UPDATE");
        return gymFulfillmentTaskMapper.selectOne(q);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(GymBusinessOrderDO order, GymOrderPolicyDO policy) {
        if (Boolean.TRUE.equals(policy.getApprovalRequired())) return;
        var row = new GymFulfillmentTaskDO();
        row.setOrderId(order.getId());
        row.setPrincipalUserId(principal());
        row.setPrincipalUserType(UserTypeEnum.ADMIN.getValue());
        row.setAttemptCount(0);
        row.setAttemptLimit(8);
        row.setStatus(order.getPayableAmount().signum() == 0 ? "pending" : "waiting_payment");
        row.setNextAttemptAt(order.getPayableAmount().signum() == 0 ? now() : null);
        gymFulfillmentTaskMapper.insert(row);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueMember(Long orderId) {
        var identity = identities.requireIdentity();
        var order = gymBusinessOrderMapper.selectById(orderId);
        var policy = gymOrderPolicyMapper.selectOne(GymOrderPolicyDO::getOrderId, orderId);
        if (order == null
                || policy == null
                || !identity.getUserId().equals(order.getBuyerUserId())
                || !identity.getMemberId().equals(order.getMemberId())
                || !"miniapp".equals(order.getChannel())
                || !Set.of("card_sale", "course_sale").contains(order.getBusinessType()))
            throw exception(COMMERCE_REQUEST_INVALID);
        if (Boolean.TRUE.equals(policy.getApprovalRequired())) return;
        var row = new GymFulfillmentTaskDO();
        row.setOrderId(orderId);
        row.setPrincipalUserId(identity.getUserId());
        row.setPrincipalUserType(UserTypeEnum.MEMBER.getValue());
        row.setAttemptCount(0);
        row.setAttemptLimit(8);
        row.setStatus(order.getPayableAmount().signum() == 0 ? "pending" : "waiting_payment");
        row.setNextAttemptAt(order.getPayableAmount().signum() == 0 ? now() : null);
        gymFulfillmentTaskMapper.insert(row);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ready(Long orderId) {
        var row = task(orderId, true);
        if (row != null && "waiting_payment".equals(row.getStatus())) {
            row.setStatus("pending");
            row.setNextAttemptAt(now());
            gymFulfillmentTaskMapper.updateById(row);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void holdForRefund(Long orderId) {
        var row = task(orderId, true);
        if (row != null
                && Set.of("pending", "failed", "processing", "waiting_payment")
                        .contains(row.getStatus())) {
            row.setStatus("waiting_refund");
            row.setNextAttemptAt(null);
            row.setClaimToken(null);
            row.setLeaseUntil(null);
            row.setFailureReason("订单存在处理中退款申请，尚未发放的权益暂停履约");
            gymFulfillmentTaskMapper.updateById(row);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resumeAfterRefundRejection(GymBusinessOrderDO order) {
        var row = task(order.getId(), true);
        if (row == null || !"waiting_refund".equals(row.getStatus())) return;
        boolean paid = order.getPaidAmount().compareTo(order.getPayableAmount()) == 0;
        if (order.getRefundedAmount().signum() != 0
                || Set.of("cancelled", "refunded", "fulfilled").contains(order.getStatus())) return;
        row.setStatus(
                paid
                        ? row.getAttemptCount() < row.getAttemptLimit() ? "pending" : "failed"
                        : "waiting_payment");
        row.setNextAttemptAt(paid && row.getAttemptCount() < row.getAttemptLimit() ? now() : null);
        row.setFailureReason(
                paid && row.getAttemptCount() >= row.getAttemptLimit()
                        ? "自动履约重试次数已用尽，请后台核查后重试"
                        : null);
        gymFulfillmentTaskMapper.updateById(row);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void holdForApproval(Long orderId) {
        var row = task(orderId, true);
        if (row != null && !Set.of("succeeded", "cancelled").contains(row.getStatus())) {
            row.setStatus("waiting_approval");
            row.setNextAttemptAt(null);
            row.setLeaseUntil(null);
            row.setClaimToken(null);
            row.setFailureReason("已发起人工开卡审批，自动履约暂停");
            gymFulfillmentTaskMapper.updateById(row);
        }
    }

    public boolean heldForApproval(Long orderId) {
        var row = task(orderId, false);
        return row != null && "waiting_approval".equals(row.getStatus());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long orderId) {
        var row = task(orderId, true);
        if (row != null) {
            row.setStatus("succeeded");
            row.setNextAttemptAt(null);
            row.setLeaseUntil(null);
            row.setClaimToken(null);
            row.setFailureReason(null);
            gymFulfillmentTaskMapper.updateById(row);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Long orderId) {
        var row = task(orderId, true);
        if (row != null && !"succeeded".equals(row.getStatus())) {
            row.setStatus("cancelled");
            row.setNextAttemptAt(null);
            row.setLeaseUntil(null);
            row.setClaimToken(null);
            row.setFailureReason("订单已退款，未完成的自动履约已取消");
            gymFulfillmentTaskMapper.updateById(row);
        }
    }

    public List<Long> due() {
        return gymFulfillmentTaskMapper
                .selectList(
                        new LambdaQueryWrapper<GymFulfillmentTaskDO>()
                                .and(
                                        q ->
                                                q.nested(
                                                                w ->
                                                                        w.in(
                                                                                        GymFulfillmentTaskDO
                                                                                                ::getStatus,
                                                                                        List.of(
                                                                                                "pending",
                                                                                                "failed"))
                                                                                .le(
                                                                                        GymFulfillmentTaskDO
                                                                                                ::getNextAttemptAt,
                                                                                        now())
                                                                                .apply(
                                                                                        "attempt_count < attempt_limit"))
                                                        .or()
                                                        .nested(
                                                                w ->
                                                                        w.eq(
                                                                                        GymFulfillmentTaskDO
                                                                                                ::getStatus,
                                                                                        "processing")
                                                                                .le(
                                                                                        GymFulfillmentTaskDO
                                                                                                ::getLeaseUntil,
                                                                                        now())))
                                .orderByAsc(GymFulfillmentTaskDO::getId)
                                .last("LIMIT 50"))
                .stream()
                .map(GymFulfillmentTaskDO::getId)
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public GymFulfillmentTaskDO claim(Long id) {
        var row =
                gymFulfillmentTaskMapper.selectOne(
                        new LambdaQueryWrapper<GymFulfillmentTaskDO>()
                                .eq(GymFulfillmentTaskDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null) return null;
        Instant now = now();
        boolean expired =
                "processing".equals(row.getStatus())
                        && row.getLeaseUntil() != null
                        && !row.getLeaseUntil().isAfter(now);
        boolean ready =
                Set.of("pending", "failed").contains(row.getStatus())
                        && row.getNextAttemptAt() != null
                        && !row.getNextAttemptAt().isAfter(now);
        if (!expired && !ready) return null;
        if (row.getAttemptCount() >= row.getAttemptLimit()) {
            if (expired) {
                row.setStatus("failed");
                row.setFailureReason("执行租约超时且重试次数已用尽");
                row.setNextAttemptAt(null);
                row.setClaimToken(null);
                row.setLeaseUntil(null);
                gymFulfillmentTaskMapper.updateById(row);
            }
            return null;
        }
        row.setStatus("processing");
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(now);
        row.setClaimToken(UUID.randomUUID().toString());
        row.setLeaseUntil(now.plusSeconds(120));
        row.setNextAttemptAt(null);
        gymFulfillmentTaskMapper.updateById(row);
        return row;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public void finish(Long id, String token, String failure) {
        var row =
                gymFulfillmentTaskMapper.selectOne(
                        new LambdaQueryWrapper<GymFulfillmentTaskDO>()
                                .eq(GymFulfillmentTaskDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null
                || !"processing".equals(row.getStatus())
                || !Objects.equals(token, row.getClaimToken())) return;
        row.setStatus(failure == null ? "succeeded" : "failed");
        row.setFailureReason(failure);
        row.setClaimToken(null);
        row.setLeaseUntil(null);
        row.setNextAttemptAt(
                failure != null && row.getAttemptCount() < row.getAttemptLimit()
                        ? now().plusSeconds(
                                        Math.min(
                                                3600L,
                                                60L
                                                        * (1L
                                                                << Math.min(
                                                                        6,
                                                                        row.getAttemptCount()
                                                                                - 1))))
                        : null);
        gymFulfillmentTaskMapper.updateById(row);
    }

    private GymBusinessOrderDO order(Long id, boolean lock) {
        var q = new LambdaQueryWrapper<GymBusinessOrderDO>().eq(GymBusinessOrderDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = gymBusinessOrderMapper.selectOne(q);
        if (row == null
                || !Set.of("card_sale", "course_sale").contains(row.getBusinessType())
                || !Set.of("miniapp", "app").contains(row.getChannel()))
            throw exception(SALES_ORDER_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }

    public GymFulfillmentTaskRespVO get(Long orderId) {
        order(orderId, false);
        var row = task(orderId, false);
        return row == null ? null : BeanUtils.toBean(row, GymFulfillmentTaskRespVO.class);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymFulfillmentTaskRespVO retry(Long orderId) {
        mutex.lockStoreEntitlements();
        var order = order(orderId, true);
        var policy =
                gymOrderPolicyMapper.selectOne(
                        new LambdaQueryWrapper<GymOrderPolicyDO>()
                                .eq(GymOrderPolicyDO::getOrderId, orderId));
        if (!Set.of("paid", "pending_fulfillment").contains(order.getStatus())
                || policy == null
                || Boolean.TRUE.equals(policy.getApprovalRequired())
                || policy.getFulfilledAt() != null
                || order.getRefundedAmount().signum() != 0
                || order.getPaidAmount().compareTo(order.getPayableAmount()) != 0)
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        if (refundApplications.selectCount(GymRefundApplicationDO::getActiveOrderId, orderId) != 0)
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        var row = task(orderId, true);
        if (row == null || !"failed".equals(row.getStatus()))
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        Long operator = principal();
        if (UserTypeEnum.ADMIN.getValue().equals(row.getPrincipalUserType()))
            row.setPrincipalUserId(operator);
        else if (!UserTypeEnum.MEMBER.getValue().equals(row.getPrincipalUserType())
                || !Objects.equals(row.getPrincipalUserId(), order.getBuyerUserId()))
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        // Member tasks always retain the actual buyer, even when an administrator requests retry.
        row.setAttemptLimit(Math.addExact(row.getAttemptCount(), 8));
        row.setStatus("pending");
        row.setNextAttemptAt(now());
        row.setLeaseUntil(null);
        row.setClaimToken(null);
        gymFulfillmentTaskMapper.updateById(row);
        return BeanUtils.toBean(row, GymFulfillmentTaskRespVO.class);
    }
}
