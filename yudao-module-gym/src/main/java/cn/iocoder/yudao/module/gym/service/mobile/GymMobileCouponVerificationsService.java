package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponVerificationVO.*;
import cn.iocoder.yudao.module.gym.service.groupbuy.*;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class GymMobileCouponVerificationsService {
    @Resource private GymCouponVerificationTransactions transactions;
    @Resource private GymCouponVerificationWorker worker;

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
