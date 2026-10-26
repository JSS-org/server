package cn.iocoder.yudao.module.gym.service.sales;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;
import cn.iocoder.yudao.module.gym.service.course.GymCourseService;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GymMemberOrderFulfillmentService {
    @Resource private GymOrderEntitlementAccess access;
    @Resource private GymCardService cards;
    @Resource private GymCourseService courses;
    @Resource private GymBusinessClock clock;
    @Resource private GymOrderItemMapper items;
    @Resource private GymOrderPolicyMapper policies;
    @Resource private GymBusinessOrderMapper orders;
    @Resource private GymFulfillmentQueueService queue;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void fulfill(Long orderId) {
        var purchase = access.requireOrder(orderId);
        if (purchase.policy().getFulfilledAt() != null) return;
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        var item = purchase.item();
        Long rightId =
                "card".equals(item.getProductType())
                        ? cards.issuePurchasedOrderItem(item.getId(), now).getId()
                        : courses.issuePurchasedOrderItem(item.getId(), now).getId();
        item.setEntitlementIds(JsonUtils.toJsonString(List.of(rightId.toString())));
        items.updateById(item);
        purchase.policy().setFulfilledAt(now);
        policies.updateById(purchase.policy());
        purchase.order().setStatus("fulfilled");
        orders.updateById(purchase.order());
        queue.complete(orderId);
    }
}
