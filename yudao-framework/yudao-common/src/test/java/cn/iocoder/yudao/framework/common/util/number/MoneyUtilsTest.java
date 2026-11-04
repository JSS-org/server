package cn.iocoder.yudao.framework.common.util.number;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyUtilsTest {

    @ParameterizedTest
    @CsvSource({
        "1000, 2, 6020, 1204",
        "10000, 1, 1, 1",
        "199, 3, 3333, 198",
        "1000, 2, 6000, 1200",
        "1000, 2, 10000, 2000",
        "1000, 2, 0, 0",
        "1000, 2, , 2000"
    })
    void calculatorPreservesFractionalPercentAndFloorsCents(
            Integer price, Integer count, Integer percent, Integer expected) {
        assertEquals(expected, MoneyUtils.calculator(price, count, percent));
    }
}
