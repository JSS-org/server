package cn.iocoder.yudao.module.gym.service.contract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

class GymContractReminderTimerTest {
    @Test
    void privateTimerFiresWithoutReplacingTheApplicationScheduler() {
        var handler = mock(GymContractReminderScheduler.class);
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
                .withBean(GymContractReminderScheduler.class, () -> handler)
                .withBean(TaskScheduler.class, () -> global)
                .withUserConfiguration(GymContractReminderTimer.class)
                .withPropertyValues(
                        "gym.contract.reminders.interval-ms=1000",
                        "gym.contract.reminders.initial-delay-ms=0")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertTrue(fired.await(2, TimeUnit.SECONDS));
                            assertTrue(threadName.get().startsWith("gym-contract-reminder-"));
                            assertEquals(1, context.getBeansOfType(TaskScheduler.class).size());
                            assertSame(global, context.getBean(TaskScheduler.class));
                            verifyNoInteractions(global);
                        });
    }

    @Test
    void explicitlyDisabledTimerDoesNotStartBackgroundWork() {
        var handler = mock(GymContractReminderScheduler.class);
        new ApplicationContextRunner()
                .withBean(GymContractReminderScheduler.class, () -> handler)
                .withUserConfiguration(GymContractReminderTimer.class)
                .withPropertyValues("gym.contract.reminders.enabled=false")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertTrue(
                                    context.getBeansOfType(GymContractReminderTimer.class)
                                            .isEmpty());
                            verifyNoInteractions(handler);
                        });
    }
}
