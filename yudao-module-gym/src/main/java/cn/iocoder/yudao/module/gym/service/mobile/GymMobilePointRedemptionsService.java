package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointRedemptionVO.*;
import cn.iocoder.yudao.module.gym.service.points.*;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class GymMobilePointRedemptionsService {
    @Resource private GymPointRedemptionTransactions transactions;
    @Resource private GymPointRedemptionWorker worker;

    // Must not wrap preparation and failure compensation in the same transaction.
    @Transactional(propagation = Propagation.NEVER)
    public Result create(Create req, String key) {
        Long id = transactions.prepare(req, key);
        worker.process(id);
        return transactions.get(id);
    }

    public Result get(Long id) {
        return transactions.get(id);
    }
}
