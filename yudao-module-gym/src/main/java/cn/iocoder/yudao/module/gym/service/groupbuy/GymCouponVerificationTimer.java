package cn.iocoder.yudao.module.gym.service.groupbuy;

import jakarta.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.*;

/** Privately owned scheduler: does not replace or occupy the application's default scheduling executor. */
@Component
@ConditionalOnProperty(
        name = "gym.coupons.verification.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GymCouponVerificationTimer {
    @Resource private GymCouponVerificationScheduler handler;

    @Value("${gym.coupons.verification.interval-ms:30000}")
    private long interval;

    @Value("${gym.coupons.verification.initial-delay-ms:30000}")
    private long initialDelay;

    private ThreadPoolTaskScheduler timer;

    @PostConstruct
    public void start() {
        if (interval < 1000 || initialDelay < 0)
            throw new IllegalArgumentException(
                    "gym coupon verification interval must be >= 1000 ms and initial delay nonnegative");
        timer = new ThreadPoolTaskScheduler();
        timer.setPoolSize(1);
        timer.setThreadNamePrefix("gym-coupon-verification-");
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
