package cn.iocoder.yudao.module.gym.service.sales.payment;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
class GymPaymentConfigurationTest {
 @Test void missingProviderFailsClosed(){
  new ApplicationContextRunner().withUserConfiguration(GymPaymentConfiguration.class).run(context->{
   var verifier=context.getBean(GymPaymentReceiptVerifier.class);assertInstanceOf(UnconfiguredGymPaymentReceiptVerifier.class,verifier);
   assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->verifier.payment("reference"));
  });
 }
 @Test void configuredAdapterReplacesOnlyTheUnconfiguredFallback(){
  var provider=mock(GymPaymentReceiptVerifier.class);
  new ApplicationContextRunner().withBean(GymPaymentReceiptVerifier.class,()->provider).withUserConfiguration(GymPaymentConfiguration.class).run(context->{assertSame(provider,context.getBean(GymPaymentReceiptVerifier.class));assertEquals(1,context.getBeansOfType(GymPaymentReceiptVerifier.class).size());});
 }
}
