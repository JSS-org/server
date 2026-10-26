package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.TreeMap;

/** 美团公共请求信封；biz 保存已序列化的原文，不包含签名密钥，不生成凭据 toString。 */
@Getter
@Setter
public class MeituanCommonRequest {
    private String appAuthToken;
    private Integer businessId;
    private String charset = "utf-8";
    private Long developerId;
    private Long timestamp;
    private String version = "2";
    private String biz;
    private String realOpBizCode;
    private String sign;

    /** 仅在签名和表单传输边界转换；biz 不再序列化，空参数不发送。 */
    public Map<String, String> toParameters() {
        Map<String, String> parameters = new TreeMap<>();
        add(parameters, "appAuthToken", appAuthToken);
        add(parameters, "businessId", businessId);
        add(parameters, "charset", charset);
        add(parameters, "developerId", developerId);
        add(parameters, "timestamp", timestamp);
        add(parameters, "version", version);
        add(parameters, "biz", biz);
        add(parameters, "realOpBizCode", realOpBizCode);
        add(parameters, "sign", sign);
        return parameters;
    }

    private static void add(Map<String, String> parameters, String name, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            parameters.put(name, value.toString());
        }
    }
}
