package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class AppTradeOrderSettlementReqVOTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private AppTradeOrderCreateReqVO request(AppTradeOrderSettlementReqVO.Item item) {
        var request = new AppTradeOrderCreateReqVO();
        request.setItems(Arrays.asList(item));
        request.setPointStatus(false);
        request.setDeliveryType(1);
        return request;
    }

    @Test
    void allowsDirectPurchaseAndCartOnlyPurchase() {
        assertTrue(
                validator
                        .validate(
                                request(
                                        new AppTradeOrderSettlementReqVO.Item()
                                                .setSkuId(10L)
                                                .setCount(1)))
                        .isEmpty());
        assertTrue(
                validator
                        .validate(request(new AppTradeOrderSettlementReqVO.Item().setCartId(20L)))
                        .isEmpty());
    }

    @Test
    void cascadesQuantityConstraintsIntoOrderItems() {
        for (int count : List.of(0, -1)) {
            var violations =
                    validator.validate(
                            request(
                                    new AppTradeOrderSettlementReqVO.Item()
                                            .setSkuId(10L)
                                            .setCount(count)));
            assertTrue(
                    violations.stream()
                            .anyMatch(
                                    v -> v.getPropertyPath().toString().equals("items[0].count")));
        }
    }

    @Test
    void rejectsMissingCountEvenWhenCartIdAlsoPresent() {
        assertFalse(
                validator
                        .validate(
                                request(
                                        new AppTradeOrderSettlementReqVO.Item()
                                                .setSkuId(10L)
                                                .setCartId(20L)))
                        .isEmpty());
        assertFalse(
                validator
                        .validate(request(new AppTradeOrderSettlementReqVO.Item().setCount(1)))
                        .isEmpty());
    }

    @Test
    void rejectsNullAndEmptyItems() {
        assertFalse(validator.validate(request(null)).isEmpty());
        var request = request(new AppTradeOrderSettlementReqVO.Item().setCartId(20L));
        request.setItems(List.of());
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void activityWithMissingItemsReturnsViolationsInsteadOfThrowing() {
        var request = request(new AppTradeOrderSettlementReqVO.Item().setCartId(20L));
        request.setSeckillActivityId(1L);
        request.setItems(null);
        assertFalse(validator.validate(request).isEmpty());
    }
}
