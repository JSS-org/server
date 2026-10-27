package cn.iocoder.yudao.module.trade.convert.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;

import org.junit.jupiter.api.Test;

import java.util.List;

class TradeOrderPickUpCodeConvertTest {

    @Test
    void preservesLeadingZerosInAdminListAndDetail() {
        var order = new TradeOrderDO().setId(1L).setPickUpVerifyCode("001234");
        assertEquals("001234", TradeOrderConvert.INSTANCE.convert(order, List.of()).getPickUpVerifyCode());
        assertEquals("001234", TradeOrderConvert.INSTANCE.convert2(order, List.of()).getPickUpVerifyCode());
    }

    @Test
    void preservesNonNumericAndLongCodesInAdminListAndDetail() {
        for (String code : List.of("A-001234", "123456789012345678901234567890")) {
            var order = new TradeOrderDO().setId(1L).setPickUpVerifyCode(code);
            assertEquals(code, TradeOrderConvert.INSTANCE.convert(order, List.of()).getPickUpVerifyCode());
            assertEquals(code, TradeOrderConvert.INSTANCE.convert2(order, List.of()).getPickUpVerifyCode());
        }
    }
}
