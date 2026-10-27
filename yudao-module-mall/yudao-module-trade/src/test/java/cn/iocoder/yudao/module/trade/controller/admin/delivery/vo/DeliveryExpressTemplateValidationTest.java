package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.expresstemplate.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

class DeliveryExpressTemplateValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void start() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    private DeliveryExpressTemplateChargeBaseVO charge() {
        return new DeliveryExpressTemplateChargeBaseVO()
                .setAreaIds(List.of(110101))
                .setStartCount(0D)
                .setStartPrice(0)
                .setExtraCount(0D)
                .setExtraPrice(0);
    }

    private DeliveryExpressTemplateCreateReqVO request(DeliveryExpressTemplateChargeBaseVO charge) {
        var request = new DeliveryExpressTemplateCreateReqVO();
        request.setName("Freight");
        request.setChargeMode(1);
        request.setSort(0);
        request.setCharges(List.of(charge));
        return request;
    }

    @Test
    void acceptsZeroPriceFlatRulesAndIntegerFreeThresholds() {
        var request = request(charge());
        request.setFrees(
                List.of(
                        new DeliveryExpressTemplateFreeBaseVO()
                                .setAreaIds(List.of(110101))
                                .setFreeCount(0)
                                .setFreePrice(0)));
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsInvalidTemplateFieldsEmptyChargesAndNegativeFreeThresholds() {
        var request = request(charge());
        request.setName("");
        request.setSort(-1);
        request.setChargeMode(99);
        request.setCharges(List.of());
        request.setFrees(
                List.of(
                        new DeliveryExpressTemplateFreeBaseVO()
                                .setAreaIds(List.of(110101))
                                .setFreeCount(-1)
                                .setFreePrice(-1)));
        var paths =
                validator.validate(request).stream()
                        .map(value -> value.getPropertyPath().toString())
                        .collect(java.util.stream.Collectors.toSet());
        assertTrue(
                paths.containsAll(
                        List.of(
                                "name",
                                "sort",
                                "chargeMode",
                                "charges",
                                "frees[0].freeCount",
                                "frees[0].freePrice")),
                paths.toString());
    }

    @Test
    void rejectsUnsafeChargeInputsBeforeTheyCanBePersisted() {
        for (var value :
                List.of(
                        charge().setExtraPrice(29),
                        charge().setStartCount(-1D),
                        charge().setExtraCount(Double.NaN),
                        charge().setStartPrice(-1))) {
            assertFalse(validator.validate(request(value)).isEmpty());
        }
    }
}
