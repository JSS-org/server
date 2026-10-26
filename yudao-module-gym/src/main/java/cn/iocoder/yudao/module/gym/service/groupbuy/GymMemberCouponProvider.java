package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailClient;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.*;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.Prepare;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class GymMemberCouponProvider {
    @Resource private GymMeituanRetailClient meituan;

    private <T> T data(MeituanResponse<T> result) {
        if (result == null || !"OP_SUCCESS".equals(result.getCode()) || result.getData() == null)
            throw exception(COUPON_PROVIDER_INVALID);
        return result.getData();
    }

    public Prepare prepare(Long tenant, Long store, String code, String verifiedMobile) {
        var request = new MeituanReceiptCodeRequest();
        request.setReceiptCode(code);
        var p = data(meituan.prepare(tenant, store, request));
        if (!code.equals(p.getReceiptCode())
                || p.getDealId() == null
                || p.getDealId() <= 0
                || p.getDealGroupId() == null
                || p.getDealGroupId() <= 0) throw exception(COUPON_PROVIDER_INVALID);
        // Never authorize by masked-phone suffix or coupon possession alone.
        if (Objects.equals(verifiedMobile, p.getMobile())) return p;
        if (p.getMobile() != null && p.getMobile().matches("1[3-9][0-9]{9}"))
            throw exception(COUPON_OWNERSHIP_REQUIRED);
        if (!Integer.valueOf(1).equals(p.getPlatform())
                && !Integer.valueOf(2).equals(p.getPlatform()))
            throw exception(COUPON_OWNERSHIP_REQUIRED);
        var lookup = new MeituanMobileReceiptsRequest();
        lookup.setMobile(verifiedMobile);
        lookup.setDealId(p.getDealId());
        lookup.setDealGroupId(p.getDealGroupId());
        // Prepare: 1=Meituan, 2=Dianping; querybymobile: 1=Dianping, 2=Meituan.
        lookup.setPlatform(p.getPlatform() == 1 ? 2 : 1);
        lookup.setLimit(30);
        for (int offset = 0; offset < 300; offset += 30) {
            lookup.setOffset(offset);
            var rows = data(meituan.mobileReceipts(tenant, store, lookup)).getResult();
            if (rows == null
                    || rows.size() > 30
                    || rows.stream().anyMatch(r -> r == null || r.getSerialNumber() == null))
                throw exception(COUPON_PROVIDER_INVALID);
            if (rows.stream().anyMatch(r -> code.equals(r.getSerialNumber()))) return p;
            if (rows.size() < 30) break;
        }
        throw exception(COUPON_OWNERSHIP_REQUIRED);
    }
}
