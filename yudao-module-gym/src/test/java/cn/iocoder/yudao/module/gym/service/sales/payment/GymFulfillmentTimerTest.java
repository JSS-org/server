package cn.iocoder.yudao.module.gym.service.sales.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentScheduler;
import cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentTimer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

class GymFulfillmentTimerTest {
    @Test
    void privateTimerFiresWithoutReplacingTheApplicationScheduler() {
        var handler = mock(GymFulfillmentScheduler.class);
        var global = mock(TaskScheduler.class);
        var fired = new CountDownLatch(1);
        var threadName = new AtomicReference<String>();
        doAnswer(
                        i -> {
                            threadName.set(Thread.currentThread().getName());
                            fired.countDown();
                            return null;
                        })
                .when(handler)
                .tick();
        new ApplicationContextRunner()
                .withBean(GymFulfillmentScheduler.class, () -> handler)
                .withBean(TaskScheduler.class, () -> global)
                .withUserConfiguration(GymFulfillmentTimer.class)
                .withPropertyValues(
                        "gym.commerce.fulfillment.interval-ms=1000",
                        "gym.commerce.fulfillment.initial-delay-ms=0")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertTrue(fired.await(2, TimeUnit.SECONDS));
                            assertTrue(threadName.get().startsWith("gym-fulfillment-"));
                            assertEquals(1, context.getBeansOfType(TaskScheduler.class).size());
                            assertSame(global, context.getBean(TaskScheduler.class));
                            verifyNoInteractions(global);
                        });
    }

    @Test
    void explicitlyDisabledTimerDoesNotStartBackgroundWork() {
        var handler = mock(GymFulfillmentScheduler.class);
        new ApplicationContextRunner()
                .withBean(GymFulfillmentScheduler.class, () -> handler)
                .withUserConfiguration(GymFulfillmentTimer.class)
                .withPropertyValues("gym.commerce.fulfillment.enabled=false")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertTrue(context.getBeansOfType(GymFulfillmentTimer.class).isEmpty());
                            verifyNoInteractions(handler);
                        });
    }
}
