package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO.*;
import cn.iocoder.yudao.module.gym.service.groupbuy.*;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class GymMobileCouponPreviewsService {
    @Resource private GymCouponPreviewTransactions transactions;
    @Resource private GymMemberCouponProvider provider;

    @Transactional(propagation = Propagation.NEVER)
    public Preview preview(Request req) {
        var context = transactions.begin(req);
        var prepared =
                provider.prepare(
                        context.tenant(), req.getStoreId(), req.getCouponCode(), context.mobile());
        return transactions.save(req, context, prepared);
    }
}
