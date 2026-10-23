package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanCommonRequest;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;

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

/** 共享签名与表单 POST 传输；无自动重试，不将未知核销/退款结果冒充失败或成功。 */
@Component
public class GymMeituanTransport {
    static final String BASE_URL = "https://api-open-cater.meituan.com";
    private final GymMeituanProperties properties;
    private final RestClient restClient;

    @Autowired
    public GymMeituanTransport(GymMeituanProperties properties) {
        this(properties, createRestClient(properties));
    }

    GymMeituanTransport(GymMeituanProperties properties, RestClient restClient) {
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

    public <T> MeituanResponse<T> post(
            Long tenantId, Long storeId, MeituanApi api, Object biz, Class<T> responseType) {
        var tenant = properties.getTenants().get(tenantId);
        var auth = tenant == null ? null : tenant.getStores().get(storeId);
        if (!properties.isEnabled()
                || properties.getDeveloperId() == null
                || properties.getDeveloperId() <= 0
                || !StringUtils.hasText(properties.getSignKey())
                || auth == null
                || !StringUtils.hasText(auth.getAppAuthToken())
                || auth.getBusinessId() == null) {
            throw exception(GROUP_BUY_PROVIDER_UNCONFIGURED);
        }
        if (auth.getBusinessId() != api.getBusinessId()) {
            throw exception(GROUP_BUY_PROVIDER_SCENE_MISMATCH);
        }
        if (api.isMutation() && !properties.isMutationsEnabled()) {
            throw exception(GROUP_BUY_PROVIDER_MUTATIONS_DISABLED);
        }
        var request = new MeituanCommonRequest();
        request.setAppAuthToken(auth.getAppAuthToken());
        request.setBusinessId(api.getBusinessId());
        request.setDeveloperId(properties.getDeveloperId());
        request.setTimestamp(Instant.now().getEpochSecond());
        // 无业务参数的标品查询不发送 biz；其余请求只序列化一次。
        if (biz != null) request.setBiz(JsonUtils.toJsonString(biz));
        if (StringUtils.hasText(auth.getRealOpBizCode()))
            request.setRealOpBizCode(auth.getRealOpBizCode());
        request.setSign(sign(properties.getSignKey(), request.toParameters()));
        var form = new LinkedMultiValueMap<String, String>();
        form.setAll(request.toParameters());
        String body;
        try {
            body =
                    restClient
                            .post()
                            .uri(BASE_URL + api.getPath())
                            .contentType(
                                    MediaType.parseMediaType(
                                            "application/x-www-form-urlencoded;charset=utf-8"))
                            .body(form)
                            .retrieve()
                            .body(String.class);
        } catch (RestClientException e) {
            // 不输出含凭据/券码/手机号的异常原文。变更类请求需补查状态后再决定下一步。
            throw exception(
                    api.isMutation()
                            ? GROUP_BUY_PROVIDER_RESULT_UNKNOWN
                            : GROUP_BUY_PROVIDER_REQUEST_FAILED);
        }
        var mapper = JsonUtils.getObjectMapper();
        tools.jackson.databind.JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (RuntimeException e) {
            throw invalidResponse(api);
        }
        if (root == null || !root.isObject() || !root.path("code").isString())
            throw invalidResponse(api);
        String code = root.path("code").asString();
        if (!"OP_SUCCESS".equals(code)) {
            // 业务错误可能包含“部分验证成功”。仅明确的公共参数拒绝可判定未执行；
            // 其余变更类错误保守返回待确认，不丢失部分执行的风险语义。
            if (api.isMutation() && !"OP_SYSTEM_PARAM_ERROR".equals(code))
                throw exception(GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
            throw exception(GROUP_BUY_PROVIDER_REJECTED);
        }
        if (!api.acceptsData(root.get("data"))) throw invalidResponse(api);
        try {
            var type =
                    mapper.getTypeFactory()
                            .constructParametricType(MeituanResponse.class, responseType);
            return mapper.readValue(body, type);
        } catch (RuntimeException e) {
            throw invalidResponse(api);
        }
    }

    private RuntimeException invalidResponse(MeituanApi api) {
        return exception(
                api.isMutation()
                        ? GROUP_BUY_PROVIDER_RESULT_UNKNOWN
                        : GROUP_BUY_PROVIDER_RESPONSE_INVALID);
    }

    static String sign(String signKey, Map<String, String> params) {
        var source = new StringBuilder(signKey);
        new TreeMap<>(params)
                .forEach(
                        (key, value) -> {
                            if (!"sign".equalsIgnoreCase(key) && value != null && !value.isEmpty())
                                source.append(key).append(value);
                        });
        return DigestUtil.sha1Hex(source.toString());
    }
}
