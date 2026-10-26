package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GymMobilePerformanceAggregate {
    private GymMobilePerformanceAggregate() {}

    public record Criteria(
            Long tenant,
            List<Long> stores,
            Instant from,
            Instant to,
            Long employee,
            boolean byEmployee) {}

    @Data
    public static class Amount {
        private Long id;
        private BigDecimal netAmount;
        private Long paidOrderCount;
    }

    @Data
    public static class Lessons {
        private Long id;
        private Long lessonCount;
    }
}
