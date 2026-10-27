package cn.iocoder.yudao.module.trade.convert.delivery;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateChargeDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;

import org.junit.jupiter.api.Test;

import java.util.List;

class DeliveryExpressTemplateConvertTest {
    @Test
    void missingTemplateRemainsNullForTheDetailController() {
        assertNull(DeliveryExpressTemplateConvert.INSTANCE.convert(null, List.of(), List.of()));
    }

    @Test
    void preservesTemplateAndChargeIdsInRealMapStructConversion() {
        long id = 9007199254740993L;
        var result =
                DeliveryExpressTemplateConvert.INSTANCE.convert(
                        new DeliveryExpressTemplateDO().setId(id).setName("Freight"),
                        List.of(
                                new DeliveryExpressTemplateChargeDO()
                                        .setId(id + 2)
                                        .setAreaIds(List.of(110101))),
                        List.of());
        assertEquals(id, result.getId());
        assertEquals(id + 2, result.getCharges().get(0).getId());
        assertTrue(result.getFrees().isEmpty());
    }
}
