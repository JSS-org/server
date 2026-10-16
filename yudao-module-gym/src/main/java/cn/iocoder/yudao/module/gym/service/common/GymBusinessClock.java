package cn.iocoder.yudao.module.gym.service.common;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GymBusinessClock {
    public Instant now() {
        return Instant.now();
    }
}
