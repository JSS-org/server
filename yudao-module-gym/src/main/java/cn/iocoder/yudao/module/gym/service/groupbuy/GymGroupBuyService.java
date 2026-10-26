package cn.iocoder.yudao.module.gym.service.groupbuy;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryRespVO;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanCouponClient;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanCouponHistoryRequest;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class GymGroupBuyService {
    @Resource private GymStoreService stores;
    @Resource private GymMeituanCouponClient meituan;

    public GymMeituanCouponHistoryRespVO queryMeituanHistory(
            @Valid GymMeituanCouponHistoryReqVO req) {
        // 先确认当前租户真实门店及员工门店范围，再读取该租户/门店的平台凭据。
        stores.requireStore(req.getStoreId(), false);
        var request = new MeituanCouponHistoryRequest();
        request.setDate(req.getDate());
        request.setOffset(req.getOffset());
        request.setLimit(req.getLimit());
        return meituan.queryHistory(
                TenantContextHolder.getRequiredTenantId(), req.getStoreId(), request);
    }
}
