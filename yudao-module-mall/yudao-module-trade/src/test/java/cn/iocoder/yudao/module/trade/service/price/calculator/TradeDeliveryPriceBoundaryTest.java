package cn.iocoder.yudao.module.trade.service.price.calculator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.iocoder.yudao.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import cn.iocoder.yudao.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO.Charge;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO.OrderItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

class TradeDeliveryPriceBoundaryTest extends BaseMockitoUnitTest {
    @InjectMocks private TradeDeliveryPriceCalculator calculator;
    @Mock private MemberAddressApi addressApi;
    @Mock private DeliveryExpressTemplateService templates;
    @Mock private TradeConfigService config;

    @BeforeEach
    void address() {
        when(addressApi.getAddress(11L, 7L))
                .thenReturn(new MemberAddressRespDTO().setAreaId(110101));
    }

    private Charge charge(double start, int price, double extra, int extraPrice) {
        return new Charge()
                .setStartCount(start)
                .setStartPrice(price)
                .setExtraCount(extra)
                .setExtraPrice(extraPrice);
    }

    private OrderItem item(int count, long template) {
        return new OrderItem()
                .setSkuId(template)
                .setCount(count)
                .setPrice(100)
                .setSelected(true)
                .setDeliveryTypes(List.of(1))
                .setDeliveryTemplateId(template)
                .setWeight(1D)
                .setVolume(1D);
    }

    private TradePriceCalculateRespBO run(
            List<OrderItem> items, Map<Long, DeliveryExpressTemplateRespBO> rules) {
        TradePriceCalculatorHelper.recountPayPrice(items);
        var result =
                new TradePriceCalculateRespBO()
                        .setItems(items)
                        .setPrice(new TradePriceCalculateRespBO.Price());
        TradePriceCalculatorHelper.recountAllPrice(result);
        var ids =
                items.stream()
                        .map(OrderItem::getDeliveryTemplateId)
                        .collect(java.util.stream.Collectors.toSet());
        when(templates.getExpressTemplateMapByIdsAndArea(ids, 110101)).thenReturn(rules);
        calculator.calculate(
                new TradePriceCalculateReqBO().setUserId(7L).setAddressId(11L).setDeliveryType(1),
                result);
        return result;
    }

    private TradePriceCalculateRespBO run(int mode, Charge rule) {
        return run(
                List.of(item(3, 1L)),
                Map.of(
                        1L,
                        new DeliveryExpressTemplateRespBO().setChargeMode(mode).setCharge(rule)));
    }

    private void error(int code, Runnable action) {
        assertEquals(code, assertThrows(ServiceException.class, action::run).getCode());
    }

    @Test
    void calculatesCountWeightAndVolumeUsingCeilingSteps() {
        for (int mode : new int[] {1, 2, 3}) {
            var result = run(mode, charge(1, 100, 1, 29));
            assertEquals(158, result.getPrice().getDeliveryPrice());
            assertEquals(458, result.getPrice().getPayPrice());
        }
    }

    @Test
    void preservesFlatRatesWithZeroExtraCountAndZeroExtraPrice() {
        assertEquals(500, run(1, charge(0, 500, 0, 0)).getPrice().getDeliveryPrice());
    }

    @Test
    void rejectsPositiveExtraPriceWithZeroStepAsBusinessError() {
        error(1_011_005_002, () -> run(1, charge(0, 0, 0, 2)));
    }

    @Test
    void rejectsFreightMultiplicationOverflowInsteadOfReturningAWrappedCheaperPrice() {
        error(1_011_003_007, () -> run(1, charge(0, 0, 1, 1_500_000_000)));
    }

    @Test
    void rejectsNegativeAndNonfiniteRulesAsBusinessErrors() {
        for (var rule :
                List.of(
                        charge(-1, 100, 1, 29),
                        charge(Double.NaN, 100, 1, 29),
                        charge(0, -1, 1, 0),
                        charge(0, 1, Double.POSITIVE_INFINITY, 29))) {
            error(1_011_005_002, () -> run(1, rule));
        }
    }

    @Test
    void rejectsMissingRuleAmountsAsBusinessErrors() {
        error(1_011_005_002, () -> run(1, charge(0, 0, 1, 29).setStartPrice(null)));
        error(1_011_005_002, () -> run(1, charge(0, 0, 1, 29).setExtraCount(null)));
    }

    @Test
    void rejectsUnknownChargeModesAsBusinessErrors() {
        error(1_011_005_002, () -> run(99, charge(0, 100, 1, 29)));
    }

    @Test
    void rejectsFreightThatWouldOverflowTheItemPayableAmount() {
        error(1_011_003_007, () -> run(1, charge(3, Integer.MAX_VALUE, 1, 0)));
    }

    @Test
    void rejectsCombinedTemplatesWhoseOrderTotalWouldOverflow() {
        var rule =
                new DeliveryExpressTemplateRespBO()
                        .setChargeMode(1)
                        .setCharge(charge(1, 1_500_000_000, 1, 0));
        error(
                1_011_003_007,
                () -> run(List.of(item(1, 1L), item(1, 2L)), Map.of(1L, rule, 2L, rule)));
    }

    @Test
    void preservesFreeThresholdsAndConservesFlatFeesWhenWeightsAreZero() {
        var rule =
                new DeliveryExpressTemplateRespBO()
                        .setChargeMode(1)
                        .setCharge(charge(1, 100, 1, 29))
                        .setFree(
                                new DeliveryExpressTemplateRespBO.Free()
                                        .setFreeCount(2)
                                        .setFreePrice(200));
        assertEquals(0, run(List.of(item(3, 1L)), Map.of(1L, rule)).getPrice().getDeliveryPrice());
        var weighted =
                new DeliveryExpressTemplateRespBO()
                        .setChargeMode(2)
                        .setCharge(charge(1, 100, 1, 29));
        var result =
                run(
                        List.of(item(1, 1L).setWeight(0D), item(1, 1L).setWeight(0D)),
                        Map.of(1L, weighted));
        assertEquals(100, result.getPrice().getDeliveryPrice());
        assertEquals(100, result.getItems().stream().mapToInt(OrderItem::getDeliveryPrice).sum());
    }

    @Test
    void rejectsAMissingTemplateEvenWhenAnotherTemplateMatchesTheAddress() {
        var matching =
                new DeliveryExpressTemplateRespBO().setChargeMode(1).setCharge(charge(1, 29, 1, 0));
        error(1_011_003_001, () -> run(List.of(item(1, 1L), item(1, 2L)), Map.of(1L, matching)));
    }

    @Test
    void rejectsAFreeOnlyRegionWhenItsThresholdIsNotMet() {
        var freeOnly =
                new DeliveryExpressTemplateRespBO()
                        .setChargeMode(1)
                        .setFree(
                                new DeliveryExpressTemplateRespBO.Free()
                                        .setFreeCount(5)
                                        .setFreePrice(500));
        error(1_011_003_001, () -> run(List.of(item(3, 1L)), Map.of(1L, freeOnly)));
    }

    @Test
    void stillAllowsAFreeOnlyRegionWhenBothThresholdsAreMet() {
        var freeOnly =
                new DeliveryExpressTemplateRespBO()
                        .setChargeMode(1)
                        .setFree(
                                new DeliveryExpressTemplateRespBO.Free()
                                        .setFreeCount(3)
                                        .setFreePrice(300));
        var result = run(List.of(item(3, 1L)), Map.of(1L, freeOnly));
        assertEquals(0, result.getPrice().getDeliveryPrice());
        assertEquals(300, result.getPrice().getPayPrice());
    }
}
