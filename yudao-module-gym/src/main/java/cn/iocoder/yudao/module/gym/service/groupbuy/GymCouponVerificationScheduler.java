package cn.iocoder.yudao.module.gym.service.groupbuy;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GymCouponVerificationScheduler {
    private final TenantFrameworkService tenants;
    private final GymCouponVerificationTransactions queue;
    private final GymCouponVerificationWorker worker;

    public void tick() {
        var tenantIds = tenants.getTenantIds();
        if (tenantIds == null) return;
        int candidates = 0;
        for (Long tenant : tenantIds)
            try {
                candidates +=
                        TenantUtils.execute(
                                tenant,
                                () -> {
                                    try {
                                        tenants.validTenant(tenant);
                                    } catch (
                                            cn.iocoder.yudao.framework.common.exception
                                                            .ServiceException
                                                    e) {
                                        if (java.util.Set.of(
                                                        cn.iocoder.yudao.module.system.enums
                                                                .ErrorCodeConstants
                                                                .TENANT_NOT_EXISTS
                                                                .getCode(),
                                                        cn.iocoder.yudao.module.system.enums
                                                                .ErrorCodeConstants.TENANT_DISABLE
                                                                .getCode(),
                                                        cn.iocoder.yudao.module.system.enums
                                                                .ErrorCodeConstants.TENANT_EXPIRE
                                                                .getCode())
                                                .contains(e.getCode())) {
                                            log.debug(
                                                    "团购核销恢复跳过不可用租户，tenantId={}，错误码={}",
                                                    tenant,
                                                    e.getCode());
                                            return 0;
                                        }
                                        throw e;
                                    }
                                    var ids = queue.due();
                                    for (Long id : ids) {
                                        try {
                                            worker.process(id);
                                        } catch (Exception e) {
                                            log.warn(
                                                    "团购核销恢复失败，保留处理中待后续扫描，tenantId={}, verificationId={}",
                                                    tenant,
                                                    id);
                                        }
                                    }
                                    return ids.size();
                                });
            } catch (Exception e) {
                log.warn("团购核销恢复租户批次执行失败，tenantId={}", tenant);
            }
        log.debug("团购核销恢复扫描完成，租户数={}，候选任务数={}", tenantIds.size(), candidates);
    }
}
