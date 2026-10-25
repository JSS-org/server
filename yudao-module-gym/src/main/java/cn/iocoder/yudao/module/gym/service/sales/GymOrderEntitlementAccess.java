package cn.iocoder.yudao.module.gym.service.sales;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseProductDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCourseProductMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessMutex;
import cn.iocoder.yudao.module.gym.service.course.GymBookingRules;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

/** Authorizes persisted purchases under the real member identity, never an employee impersonation. */
@Service
public class GymOrderEntitlementAccess {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessOrderMapper orders;
    @Resource private GymOrderItemMapper items;
    @Resource private GymOrderPolicyMapper policies;
    @Resource private GymFulfillmentTaskMapper tasks;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymCourseProductMapper products;
    @Resource private GymBookingRules rules;
    @Resource private GymMemberRecordsService records;

    public record Purchase(
            GymBusinessOrderDO order,
            GymOrderItemDO item,
            GymOrderPolicyDO policy,
            GymMemberDO member,
            GymStoreDO store,
            GymCardTemplateDO card,
            GymCourseProductDO course) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Purchase requireItem(Long itemId) {
        mutex.lockStoreEntitlements();
        var item = items.selectById(itemId);
        if (item == null) throw exception(COMMERCE_FULFILLMENT_INVALID);
        var purchase = requireOrder(item.getOrderId());
        if (!itemId.equals(purchase.item().getId()) || purchase.policy().getFulfilledAt() != null)
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        return purchase;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Purchase requireOrder(Long orderId) {
        mutex.lockStoreEntitlements();
        var identity = identities.requireIdentity();
        var order =
                orders.selectOne(
                        new LambdaQueryWrapper<GymBusinessOrderDO>()
                                .eq(GymBusinessOrderDO::getId, orderId)
                                .last("FOR UPDATE"));
        if (order == null
                || !identity.getUserId().equals(order.getBuyerUserId())
                || !identity.getMemberId().equals(order.getMemberId())
                || !"miniapp".equals(order.getChannel())
                || !Set.of("card_sale", "course_sale").contains(order.getBusinessType()))
            throw exception(SALES_ORDER_NOT_EXISTS);
        var policy =
                policies.selectOne(
                        new LambdaQueryWrapper<GymOrderPolicyDO>()
                                .eq(GymOrderPolicyDO::getOrderId, orderId)
                                .last("FOR UPDATE"));
        var task = tasks.selectOne(GymFulfillmentTaskDO::getOrderId, orderId);
        if (policy == null
                || Boolean.TRUE.equals(policy.getApprovalRequired())
                || policy.getApprovedApprovalId() != null
                || order.getPayableAmount().signum() < 0
                || order.getPaidAmount().compareTo(order.getPayableAmount()) != 0
                || order.getRefundedAmount().signum() != 0
                || !Set.of("paid", "pending_fulfillment", "fulfilled").contains(order.getStatus())
                || (policy.getFulfilledAt() != null) != "fulfilled".equals(order.getStatus())
                || task != null
                        && Set.of("waiting_approval", "cancelled").contains(task.getStatus()))
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        var lines =
                items.selectList(
                        new LambdaQueryWrapper<GymOrderItemDO>()
                                .eq(GymOrderItemDO::getOrderId, orderId)
                                .last("FOR UPDATE"));
        if (lines.size() != 1) throw exception(COMMERCE_FULFILLMENT_INVALID);
        var item = lines.getFirst();
        if (!Integer.valueOf(1).equals(item.getQuantity())
                || !(item.getProductType() + "_sale").equals(order.getBusinessType())
                || item.getPayableAmount().compareTo(order.getPayableAmount()) != 0
                || item.getUnitPrice().compareTo(order.getPayableAmount()) != 0)
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        if (policy.getFulfilledAt() != null)
            return new Purchase(order, item, policy, null, null, null, null);
        GymCardTemplateDO card = null;
        GymCourseProductDO course = null;
        try {
            var terms = JsonUtils.parseObject(policy.getTermsSnapshot(), Map.class);
            if ("card".equals(item.getProductType())) {
                card =
                        JsonUtils.parseObject(
                                JsonUtils.toJsonString(terms.get("template")),
                                GymCardTemplateDO.class);
                if (card == null
                        || !Objects.equals(card.getId(), item.getProductId())
                        || !Objects.equals(card.getVersion(), item.getProductVersion())
                        || !TenantContextHolder.getRequiredTenantId().equals(card.getTenantId())
                        || !"published".equals(card.getStatus())
                        || !"all".equals(card.getAccessScope())
                                && !JsonUtils.parseArray(card.getStoreIds(), Long.class)
                                        .contains(order.getStoreId()))
                    throw exception(COMMERCE_FULFILLMENT_INVALID);
            } else {
                course =
                        JsonUtils.parseObject(
                                JsonUtils.toJsonString(terms.get("product")),
                                GymCourseProductDO.class);
                if (course == null
                        || !Objects.equals(course.getId(), item.getProductId())
                        || !Objects.equals(course.getVersion(), item.getProductVersion())
                        || !TenantContextHolder.getRequiredTenantId().equals(course.getTenantId())
                        || !order.getStoreId().equals(course.getStoreId())
                        || !"active".equals(course.getStatus())
                        || !"private".equals(course.getKind())
                        || course.getSessionCount() == null
                        || course.getSessionCount() < 1
                        || course.getDurationMinutes() == null
                        || course.getDurationMinutes() < 1)
                    throw exception(COMMERCE_FULFILLMENT_INVALID);
            }
        } catch (RuntimeException e) {
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        }
        var store = stores.selectForUpdate(order.getStoreId());
        if (store == null || !"active".equals(store.getStatus()))
            throw exception(COMMERCE_FULFILLMENT_INVALID);
        // Match course edits/bookings: coach before member and product.
        if (course != null) rules.requireCoach(course.getCoachId(), order.getStoreId());
        var member = members.selectForUpdate(order.getMemberId());
        if (member == null
                || !"active".equals(member.getStatus())
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member.getId())
                                        .eq(GymMemberStoreDO::getStoreId, order.getStoreId()))
                        == 0) throw exception(COMMERCE_FULFILLMENT_INVALID);
        records.checkPurchase(member.getId());
        if (course != null) {
            var current = products.selectForUpdate(course.getId());
            if (current == null
                    || !"active".equals(current.getStatus())
                    || !course.getCoachId().equals(current.getCoachId())
                    || !order.getStoreId().equals(current.getStoreId()))
                throw exception(COMMERCE_FULFILLMENT_INVALID);
        }
        return new Purchase(order, item, policy, member, store, card, course);
    }
}
