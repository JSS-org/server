package cn.iocoder.yudao.module.gym.service.common;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.mysql.common.GymBusinessMutexMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class GymBusinessMutex {
    @Resource private GymBusinessMutexMapper gymBusinessMutexMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockStoreEntitlements() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        gymBusinessMutexMapper.ensure(tenantId);
        gymBusinessMutexMapper.lock(tenantId);
    }
}
