package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import lombok.Data;

@Data
public class GymMobileAttendanceAggregate {
    private Long checkinCount;
    private Integer activeDays;
    private Long population;
    private Long ahead;
}
