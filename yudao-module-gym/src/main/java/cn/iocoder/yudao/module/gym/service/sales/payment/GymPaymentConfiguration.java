package cn.iocoder.yudao.module.gym.service.sales.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GymPaymentConfiguration {
    @Bean
    @ConditionalOnMissingBean(GymPaymentReceiptVerifier.class)
    public GymPaymentReceiptVerifier gymPaymentReceiptVerifier() {
        return new UnconfiguredGymPaymentReceiptVerifier();
    }
}
