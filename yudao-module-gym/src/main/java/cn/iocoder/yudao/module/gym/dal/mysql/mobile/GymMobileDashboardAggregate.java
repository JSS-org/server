package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GymMobileDashboardAggregate {
    private GymMobileDashboardAggregate() {}

    public record Criteria(
            Long tenant,
            List<Long> stores,
            Long user,
            Long employee,
            Instant now,
            Instant scanFrom,
            Instant yesterday,
            Instant day,
            Instant end,
            Instant month) {}

    @Data
    public static class Amount {
        private BigDecimal todayAmount;
        private BigDecimal monthAmount;
    }

    @Data
    public static class Counts {
        private long todayCount;
        private long yesterdayCount;
        private long monthCount;
    }

    @Data
    public static class Population {
        private long total;
        private long newTodayCount;
    }
}
