package cn.iocoder.yudao.module.gym.service.payroll;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.dal.dataobject.payroll.GymPayrollDO;
import cn.iocoder.yudao.module.gym.dal.mysql.payroll.GymPayrollMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkAccess;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class GymPayrollPublicationService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymPayrollMapper payrolls;
    @Resource private GymStoreMapper stores;
    @Resource private GymBusinessClock clock;

    public record Publication(String id, String publishedAt) {}

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Publication publish(Long id) {
        Long actor = access.requireAdmin();
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var initial = payrolls.selectById(id);
        if (initial == null) throw exception(PAYROLL_NOT_EXISTS);
        scope.check(initial.getStoreId());
        // Same lock order as calculate/confirm; publication never changes the frozen payroll state.
        if (stores.selectForUpdate(initial.getStoreId()) == null) throw exception(STORE_NOT_EXISTS);
        var row =
                payrolls.selectOne(
                        new LambdaQueryWrapper<GymPayrollDO>()
                                .eq(GymPayrollDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null) throw exception(PAYROLL_NOT_EXISTS);
        scope.check(row.getStoreId());
        if (!"confirmed".equals(row.getStatus()) || row.getConfirmedAt() == null)
            throw exception(PAYROLL_PUBLICATION_INVALID);
        if (row.getPublishedAt() == null) {
            row.setPublishedAt(clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            row.setPublishedBy(actor);
            payrolls.updateById(row);
        }
        return new Publication(
                row.getId().toString(),
                row.getPublishedAt().atZone(GymDateRange.ZONE).toOffsetDateTime().toString());
    }
}
