package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 固定接口白名单；不接受调用方传入 URL、业务编号或授权令牌。 */
@Getter
public enum MeituanApi {
    CATERING_HISTORY("/tuangou/coupon/queryListByDate", 1, false),
    PREPARE("/ddzh/tuangou/receipt/prepare"),
    CONSUME("/ddzh/tuangou/receipt/consume", true),
    REVERSE_CONSUME("/ddzh/tuangou/receipt/reverseconsume", true),
    GET_CONSUMED("/ddzh/tuangou/receipt/getconsumed"),
    RECEIPT_HISTORY("/ddzh/tuangou/receipt/querylistbydate"),
    SHOP_DEALS("/ddzh/tuangou/deal/queryshopdeal"),
    PRODUCTS_BY_TYPE("/ddzh/tuangou/product/queryproductbytype"),
    PRODUCTS("/ddzh/tuangou/product/queryproduct"),
    MOBILE_RECEIPTS("/ddzh/tuangou/receipt/querybymobile"),
    BATCH_CONSUME("/ddzh/tuangou/receipt/batchconsume", true),
    STANDARD_PRODUCTS("/ddzh/tuangou/receipt/queryproduct"),
    REFUND_PRECHECK("/ddzh/tuangou/pre/refund/valid"),
    REFUND_APPLY("/ddzh/tuangou/apply/refund", true),
    REFUND_QUERY("/ddzh/tuangou/query/refund/info"),
    REFUND_AUDIT("/ddzh/tuangou/refund/audit", true),
    BATCH_REVERSE_CONSUME("/ddzh/tuangou/receipt/batch/reverseconsume", true);

    private final String path;
    private final int businessId;
    private final boolean mutation;

    MeituanApi(String path) {
        this(path, false);
    }

    MeituanApi(String path, boolean mutation) {
        this(path, 58, mutation);
    }

    MeituanApi(String path, int businessId, boolean mutation) {
        this.path = path;
        this.businessId = businessId;
        this.mutation = mutation;
    }

    public boolean acceptsData(JsonNode data) {
        if (data == null || !data.isObject()) return false;
        return switch (this) {
            case PREPARE, GET_CONSUMED ->
                    data.path("receiptCode").isString()
                            && !data.path("receiptCode").asString().isBlank();
            case CATERING_HISTORY ->
                    data.path("total").isIntegralNumber()
                            && data.path("total").asLong() >= 0
                            && data.path("couponCodes").isArray();
            case RECEIPT_HISTORY ->
                    data.path("totalCount").isIntegralNumber()
                            && data.path("totalCount").asLong() >= 0
                            && data.path("records").isArray();
            case REFUND_PRECHECK, REFUND_APPLY, REFUND_QUERY, REFUND_AUDIT ->
                    data.path("result").isObject();
            default -> data.path("result").isArray();
        };
    }
}
