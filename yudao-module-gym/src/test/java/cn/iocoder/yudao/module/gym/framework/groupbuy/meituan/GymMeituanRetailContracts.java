package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.*;

import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** 独立的官方文档契约样例，测试不从生产枚举生成期望路径或请求。 */
public final class GymMeituanRetailContracts {
    private GymMeituanRetailContracts() {}

    public static Stream<JsonNode> cases() {
        try (var input =
                GymMeituanRetailContracts.class.getResourceAsStream(
                        "/meituan/retail-contracts.json")) {
            var root =
                    JsonUtils.getObjectMapper()
                            .readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return IntStream.range(0, root.size()).mapToObj(root::get);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Class<?> requestType(JsonNode c) throws Exception {
        return c.path("request").isNull()
                ? null
                : Class.forName(
                        "cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail."
                                + c.get("request").asString());
    }

    public static Class<?> responseType(JsonNode c) throws Exception {
        return Class.forName(
                "cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses$"
                        + c.get("response").asString());
    }

    public static Object request(JsonNode c) throws Exception {
        var type = requestType(c);
        if (type == null) return null;
        var request = JsonUtils.getObjectMapper().readValue(c.get("biz").toString(), type);
        // 等同服务层填充的可信员工身份；只为验证平台线上实际序列化内容。
        if (request instanceof MeituanAccountRequest r) {
            r.setAppShopAccount("7");
            r.setAppShopAccountName("测试店员");
        }
        if (request instanceof MeituanOperatorRequest r) r.setOperatorId("7");
        if (request instanceof MeituanRefundAuditRequest r) {
            r.setOperatorName("测试店员");
            r.setOperatorIp("127.0.0.1");
        }
        return request;
    }

    public static Method clientMethod(JsonNode c) throws Exception {
        var type = requestType(c);
        return type == null
                ? GymMeituanRetailClient.class.getMethod(
                        c.get("method").asString(), Long.class, Long.class)
                : GymMeituanRetailClient.class.getMethod(
                        c.get("method").asString(), Long.class, Long.class, type);
    }

    public static Object invoke(GymMeituanRetailClient client, JsonNode c) throws Exception {
        return requestType(c) == null
                ? clientMethod(c).invoke(client, 1L, 12L)
                : clientMethod(c).invoke(client, 1L, 12L, request(c));
    }
}
