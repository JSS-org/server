package cn.iocoder.yudao.module.trade.util.delivery;

/** Shared validation for incoming charge rules and historical rules used by pricing. */
public final class DeliveryExpressRuleUtils {
    private DeliveryExpressRuleUtils() {}

    public static boolean validCharge(
            Double startCount, Integer startPrice, Double extraCount, Integer extraPrice) {
        return startCount != null
                && Double.isFinite(startCount)
                && startCount >= 0
                && startPrice != null
                && startPrice >= 0
                && extraCount != null
                && Double.isFinite(extraCount)
                && extraCount >= 0
                && extraPrice != null
                && extraPrice >= 0
                && (extraCount > 0 || extraPrice == 0);
    }
}
