package cn.iocoder.yudao.module.gym.service.contract;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Slf4j @Component @RequiredArgsConstructor
public class GymContractReminderScheduler {
    private final TenantFrameworkService tenants;
    private final GymContractMapper contracts;
    private final GymContractReminderWorker worker;
    private final GymBusinessClock clock;
    @Value("${gym.contract.reminders.channel:app}") private String channel = "app";

    public void tick() {
        var tenantIds = tenants.getTenantIds();
        if (tenantIds == null) return;
        int attempted = 0;
        for (Long tenant : tenantIds) try {
            attempted += TenantUtils.execute(tenant, () -> {
                try { tenants.validTenant(tenant); }
                catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
                    if (Set.of(cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_NOT_EXISTS.getCode(),
                            cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_DISABLE.getCode(),
                            cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_EXPIRE.getCode()).contains(e.getCode())) return 0;
                    throw e;
                }
                int count = 0; long cursor = 0;
                var cutoff = clock.now().plus(366, ChronoUnit.DAYS);
                // Keyset scanning visits all windows, even if earlier rows are not yet due.
                while (true) {
                    var page = contracts.selectList(new LambdaQueryWrapper<GymContractDO>()
                            .gt(GymContractDO::getId, cursor).in(GymContractDO::getStatus, Set.of("unsigned", "sent", "viewed"))
                            .le(GymContractDO::getDueAt, cutoff).orderByAsc(GymContractDO::getId).last("LIMIT 100"));
                    if (page.isEmpty()) break;
                    for (var row : page) {
                        try { if (worker.process(row.getId(), channel)) count++; }
                        catch (Exception e) { log.warn("自动合同提醒执行失败，tenantId={}, contractId={}", tenant, row.getId(), e); }
                    }
                    cursor = page.getLast().getId();
                }
                return count;
            });
        } catch (Exception e) { log.warn("自动合同提醒租户扫描失败，tenantId={}", tenant, e); }
        log.debug("自动合同提醒扫描完成，租户数={}，本次提醒尝试数={}", tenantIds.size(), attempted);
    }
}
