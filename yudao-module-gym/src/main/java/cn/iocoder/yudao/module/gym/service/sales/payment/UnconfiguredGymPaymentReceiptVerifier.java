package cn.iocoder.yudao.module.gym.service.sales.payment;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.COMMERCE_PROVIDER_UNCONFIGURED;

/** TODO-INTEGRATION-PAYMENT: replace with a provider adapter after merchant credentials and receipt APIs are supplied. */
public class UnconfiguredGymPaymentReceiptVerifier implements GymPaymentReceiptVerifier {
    public Receipt payment(String reference) {
        throw exception(COMMERCE_PROVIDER_UNCONFIGURED);
    }

    public Receipt refund(String reference) {
        throw exception(COMMERCE_PROVIDER_UNCONFIGURED);
    }
}
