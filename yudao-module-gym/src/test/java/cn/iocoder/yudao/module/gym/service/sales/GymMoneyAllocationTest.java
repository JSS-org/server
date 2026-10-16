package cn.iocoder.yudao.module.gym.service.sales;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.*;
import java.util.*;

class GymMoneyAllocationTest {
    @Test
    void cumulativeSharesMatchIndependentSequentialCentAllocationAndNeverDecrease() {
        var random = new Random(20261015);
        for (int sample = 0; sample < 100; sample++) {
            int size = 1 + random.nextInt(7);
            var weights = new ArrayList<BigDecimal>();
            var units = new ArrayList<Integer>();
            int total = 0;
            for (int item = 0; item < size; item++) {
                int weight = 1 + random.nextInt(30);
                total += weight;
                units.add(weight);
                weights.add(BigDecimal.valueOf(weight, 2));
            }
            var expected = new int[size];
            for (int amount = 0; amount <= total; amount++) {
                var actual = GymMoneyAllocation.cumulative(BigDecimal.valueOf(amount, 2), weights);
                for (int item = 0; item < size; item++)
                    assertEquals(BigDecimal.valueOf(expected[item], 2), actual.get(item));
                assertEquals(
                        BigDecimal.valueOf(amount, 2),
                        actual.stream().reduce(new BigDecimal("0.00"), BigDecimal::add));
                if (amount < total) {
                    int best = 0;
                    for (int item = 1; item < size; item++)
                        if ((long) units.get(item) * (expected[best] + 1)
                                > (long) units.get(best) * (expected[item] + 1)) best = item;
                    expected[best]++;
                }
            }
        }
    }

    @Test
    void splitRefundDeltasExactlyReversePaymentAndZeroWeightReceivesNothing() {
        var weights =
                List.of(
                        new BigDecimal("0.01"),
                        new BigDecimal("0.01"),
                        new BigDecimal("0.01"),
                        new BigDecimal("0.00"));
        var payment = GymMoneyAllocation.cumulative(new BigDecimal("0.03"), weights);
        var refunded =
                new BigDecimal[] {
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                };
        var previous = GymMoneyAllocation.cumulative(BigDecimal.ZERO, weights);
        for (int cents = 1; cents <= 3; cents++) {
            var next = GymMoneyAllocation.cumulative(BigDecimal.valueOf(cents, 2), weights);
            for (int item = 0; item < weights.size(); item++) {
                var delta = next.get(item).subtract(previous.get(item));
                assertTrue(delta.signum() >= 0);
                refunded[item] = refunded[item].add(delta);
            }
            previous = next;
        }
        for (int item = 0; item < weights.size(); item++)
            assertEquals(0, payment.get(item).compareTo(refunded[item]));
        assertEquals(new BigDecimal("0.00"), payment.getLast());
    }

    @Test
    void largeCentValuesUseExactIntegerArithmetic() {
        var weights = List.of(new BigDecimal("99999999999999.99"), new BigDecimal("0.01"));
        assertEquals(
                weights,
                GymMoneyAllocation.cumulative(new BigDecimal("100000000000000.00"), weights));
        var result = GymMoneyAllocation.cumulative(new BigDecimal("50000000000000.00"), weights);
        assertEquals(
                new BigDecimal("50000000000000.00"),
                result.stream().reduce(new BigDecimal("0.00"), BigDecimal::add));
    }

    @Test
    void invalidWeightsAndOverAllocationAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GymMoneyAllocation.cumulative(BigDecimal.ONE, List.of(BigDecimal.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GymMoneyAllocation.cumulative(
                                new BigDecimal("1.01"), List.of(BigDecimal.ONE)));
        assertThrows(
                ArithmeticException.class,
                () ->
                        GymMoneyAllocation.cumulative(
                                new BigDecimal("0.001"), List.of(BigDecimal.ONE)));
    }
}
