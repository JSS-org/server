package cn.iocoder.yudao.module.gym.service.contract;

import jakarta.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Set;

@Component
@ConditionalOnProperty(
        name = "gym.contract.reminders.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GymContractReminderTimer {
    @Resource private GymContractReminderScheduler handler;

    @Value("${gym.contract.reminders.interval-ms:300000}")
    private long interval;

    @Value("${gym.contract.reminders.initial-delay-ms:300000}")
    private long initialDelay;

    @Value("${gym.contract.reminders.channel:app}")
    private String channel;

    private ThreadPoolTaskScheduler timer;

    @PostConstruct
    public void start() {
        if (interval < 1000
                || initialDelay < 0
                || !Set.of("app", "miniapp", "sms").contains(channel))
            throw new IllegalArgumentException("Invalid gym contract reminder timer configuration");
        timer = new ThreadPoolTaskScheduler();
        timer.setPoolSize(1);
        timer.setThreadNamePrefix("gym-contract-reminder-");
        timer.setDaemon(true);
        timer.setRemoveOnCancelPolicy(true);
        timer.setWaitForTasksToCompleteOnShutdown(true);
        timer.setAwaitTerminationSeconds(10);
        timer.initialize();
        timer.scheduleWithFixedDelay(
                handler::tick, Instant.now().plusMillis(initialDelay), Duration.ofMillis(interval));
    }

    @PreDestroy
    public void stop() {
        if (timer != null) timer.shutdown();
    }
}
