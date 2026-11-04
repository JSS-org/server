package cn.iocoder.yudao.module.mes.service.wm.productreceipt;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.mes.controller.admin.wm.productreceipt.vo.detail.MesWmProductReceiptDetailListReqVO;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

class MesWmProductReceiptDetailListValidationTest {
    @Test
    void requiresARealParentAndPreservesLargeIdentifiers() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var query = new MesWmProductReceiptDetailListReqVO();
            assertFalse(validator.validate(query).isEmpty());
            query.setReceiptId(9007199254740993L);
            assertTrue(validator.validate(query).isEmpty());
            query.setLineId(9007199254740995L);
            assertTrue(validator.validate(query).isEmpty());
            assertEquals(9007199254740993L, query.getReceiptId());
            query.setReceiptId(null);
            assertTrue(validator.validate(query).isEmpty());
            query.setLineId(0L);
            assertFalse(validator.validate(query).isEmpty());
        }
    }
}
