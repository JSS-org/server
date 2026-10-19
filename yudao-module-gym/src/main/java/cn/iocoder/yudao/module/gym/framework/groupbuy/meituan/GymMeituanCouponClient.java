package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryRespVO;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanCommonRequest;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanCouponHistoryRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/** 美团只读历史查询适配，不执行核销，不写本地核销结果。 */
@Component
public class GymMeituanCouponClient {
    static final String HISTORY_URL =
            "https://api-open-cater.meituan.com/tuangou/coupon/queryListByDate";
    private final GymMeituanProperties properties;
    private final RestClient restClient;

    @Autowired
    public GymMeituanCouponClient(GymMeituanProperties properties) {
        this(properties, createRestClient(properties));
    }

    GymMeituanCouponClient(GymMeituanProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient createRestClient(GymMeituanProperties properties) {
        var httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return RestClient.builder().requestFactory(factory).build();
    }

    public GymMeituanCouponHistoryRespVO queryHistory(
            Long tenantId, Long storeId, MeituanCouponHistoryRequest req) {
        var tenant = properties.getTenants().get(tenantId);
        var auth = tenant == null ? null : tenant.getStores().get(storeId);
        if (!properties.isEnabled()
                || properties.getDeveloperId() == null
                || properties.getDeveloperId() <= 0
                || !StringUtils.hasText(properties.getSignKey())
                || auth == null
                || !StringUtils.hasText(auth.getAppAuthToken())
                || auth.getBusinessId() == null
                || auth.getBusinessId() <= 0) {
            throw exception(GROUP_BUY_PROVIDER_UNCONFIGURED);
        }
        // 同一份 biz 原文先参与签名、再由表单转换器编码，不能签名后重新序列化。
        var request = new MeituanCommonRequest();
        request.setAppAuthToken(auth.getAppAuthToken());
        request.setBusinessId(auth.getBusinessId());
        request.setDeveloperId(properties.getDeveloperId());
        request.setTimestamp(Instant.now().getEpochSecond());
        request.setBiz(JsonUtils.toJsonString(req));
        if (StringUtils.hasText(auth.getRealOpBizCode())) {
            request.setRealOpBizCode(auth.getRealOpBizCode());
        }
        request.setSign(sign(properties.getSignKey(), request.toParameters()));
        var form = new LinkedMultiValueMap<String, String>();
        form.setAll(request.toParameters());
        String body;
        try {
            body =
                    restClient
                            .post()
                            .uri(HISTORY_URL)
                            .contentType(
                                    MediaType.parseMediaType(
                                            "application/x-www-form-urlencoded;charset=utf-8"))
                            .body(form)
                            .retrieve()
                            .body(String.class);
        } catch (RestClientException e) {
            // 不把包含凭据或券码的上游原文/异常带入公共错误响应与日志。
            throw exception(GROUP_BUY_PROVIDER_REQUEST_FAILED);
        }
        GymMeituanCouponHistoryRespVO result;
        try {
            result =
                    JsonUtils.getObjectMapper()
                            .readValue(body, GymMeituanCouponHistoryRespVO.class);
        } catch (RuntimeException e) {
            throw exception(GROUP_BUY_PROVIDER_RESPONSE_INVALID);
        }
        if (result == null || !StringUtils.hasText(result.getCode())) {
            throw exception(GROUP_BUY_PROVIDER_RESPONSE_INVALID);
        }
        if (!"OP_SUCCESS".equals(result.getCode())) {
            throw exception(GROUP_BUY_PROVIDER_REJECTED);
        }
        if (result.getData() == null
                || result.getData().getTotal() == null
                || result.getData().getTotal() < 0
                || result.getData().getCouponCodes() == null) {
            throw exception(GROUP_BUY_PROVIDER_RESPONSE_INVALID);
        }
        return result;
    }

    static String sign(String signKey, Map<String, String> params) {
        var source = new StringBuilder(signKey);
        new TreeMap<>(params)
                .forEach(
                        (key, value) -> {
                            if (!"sign".equalsIgnoreCase(key)
                                    && value != null
                                    && !value.isEmpty()) {
                                source.append(key).append(value);
                            }
                        });
        return DigestUtil.sha1Hex(source.toString());
    }
}
