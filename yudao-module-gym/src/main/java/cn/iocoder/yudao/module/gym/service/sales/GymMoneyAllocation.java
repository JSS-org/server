package cn.iocoder.yudao.module.gym.service.sales;

import java.math.*;
import java.util.*;

/** Exact cumulative cent allocation. Stable item order breaks ties; cumulative shares never decrease. */
public final class GymMoneyAllocation {
    private GymMoneyAllocation() {}

    public static List<BigDecimal> cumulative(BigDecimal amount, List<BigDecimal> weights) {
        if (amount == null
                || amount.signum() < 0
                || weights == null
                || weights.isEmpty()
                || weights.stream().anyMatch(w -> w == null || w.signum() < 0))
            throw new IllegalArgumentException("Invalid money allocation");
        var cents = amount.movePointRight(2).toBigIntegerExact();
        var values = weights.stream().map(w -> w.movePointRight(2).toBigIntegerExact()).toList();
        var total = values.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (total.signum() <= 0 || cents.compareTo(total) > 0)
            throw new IllegalArgumentException("Amount exceeds order value");
        var shares = new ArrayList<BigInteger>();
        for (var weight : values) shares.add(cents.multiply(weight).divide(total));
        int remainder =
                cents.subtract(shares.stream().reduce(BigInteger.ZERO, BigInteger::add))
                        .intValueExact();
        // Lower proportional quotas leave fewer than N cents; assign the remainder by exact
        // next-cent priority.
        for (int seat = 0; seat < remainder; seat++) {
            int best = -1;
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index).signum() == 0
                        || shares.get(index).compareTo(values.get(index)) >= 0) continue;
                if (best < 0
                        || values.get(index)
                                        .multiply(shares.get(best).add(BigInteger.ONE))
                                        .compareTo(
                                                values.get(best)
                                                        .multiply(
                                                                shares.get(index)
                                                                        .add(BigInteger.ONE)))
                                > 0) best = index;
            }
            if (best < 0) throw new IllegalStateException("No cent allocation candidate");
            shares.set(best, shares.get(best).add(BigInteger.ONE));
        }
        return shares.stream().map(v -> new BigDecimal(v, 2)).toList();
    }
}
