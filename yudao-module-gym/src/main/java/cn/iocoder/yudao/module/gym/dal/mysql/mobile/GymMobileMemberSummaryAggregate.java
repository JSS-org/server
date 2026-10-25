package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class GymMobileMemberSummaryAggregate {
    private long memberCount;
    private long privateCourseMemberCount;
    private long newMemberCount;
    private long expiringCardCount;
    private long expiringCourseCount;

    public record Criteria(
            Long tenant,
            List<Long> stores,
            Instant now,
            Instant limit,
            Instant dayStart,
            Instant dayEnd) {}
}
