package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryRespVO;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanCouponHistoryRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 旧餐饮历史查询适配，仅接受 businessId=1；健身使用 GymMeituanRetailClient。 */
@Component
public class GymMeituanCouponClient {
    static final String HISTORY_URL =
            GymMeituanTransport.BASE_URL + MeituanApi.CATERING_HISTORY.getPath();
    private final GymMeituanTransport transport;

    @Autowired
    public GymMeituanCouponClient(GymMeituanTransport transport) {
        this.transport = transport;
    }

    GymMeituanCouponClient(GymMeituanProperties properties) {
        this(new GymMeituanTransport(properties));
    }

    GymMeituanCouponClient(GymMeituanProperties properties, RestClient restClient) {
        this(new GymMeituanTransport(properties, restClient));
    }

    public GymMeituanCouponHistoryRespVO queryHistory(
            Long tenantId, Long storeId, MeituanCouponHistoryRequest req) {
        var response =
                transport.post(
                        tenantId,
                        storeId,
                        MeituanApi.CATERING_HISTORY,
                        req,
                        GymMeituanCouponHistoryRespVO.History.class);
        var result = new GymMeituanCouponHistoryRespVO();
        result.setCode(response.getCode());
        result.setMsg(response.getMsg());
        result.setTraceId(response.getTraceId());
        result.setData(response.getData());
        return result;
    }

    static String sign(String signKey, Map<String, String> params) {
        return GymMeituanTransport.sign(signKey, params);
    }
}
