package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileSubscriptionsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;

@Service
public class GymMobileSubscriptionsService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymSubscriptionMapper subscriptions;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Subscription> page(Query req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        Long member = identities.requireMemberId();
        var page =
                subscriptions.selectPage(
                        req,
                        new LambdaQueryWrapper<GymSubscriptionDO>()
                                .eq(GymSubscriptionDO::getMemberId, member)
                                .eq(
                                        req.getStoreId() != null,
                                        GymSubscriptionDO::getStoreId,
                                        req.getStoreId())
                                .orderByDesc(GymSubscriptionDO::getId));
        return new PageResult<>(
                page.getList().stream().map(this::response).toList(), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Subscription get(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        Long member = identities.requireMemberId();
        var row =
                subscriptions.selectOne(
                        new LambdaQueryWrapper<GymSubscriptionDO>()
                                .eq(GymSubscriptionDO::getId, id)
                                .eq(GymSubscriptionDO::getMemberId, member));
        if (row == null) throw exception(SUBSCRIPTION_NOT_EXISTS);
        return response(row);
    }

    private String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private String status(String value) {
        if (value == null) return "unknown";
        return switch (value) {
            case "pending", "pending_authorization", "pending_sign" -> "pending_sign";
            case "cancelled", "terminated" -> "terminated";
            case "active", "past_due", "terminating", "unconfigured", "failed" -> value;
            default -> "unknown";
        };
    }

    private Subscription response(GymSubscriptionDO row) {
        return new Subscription(
                row.getId().toString(),
                row.getStoreId().toString(),
                row.getCardTemplateId().toString(),
                status(row.getStatus()),
                row.getAmount().setScale(2).toPlainString(),
                time(row.getNextChargeAt()),
                row.getAgreementVersion(),
                time(row.getCancelledAt()));
    }
}
