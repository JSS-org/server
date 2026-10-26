package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.*;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class GymMobileRecordPageReqVO extends PageParam {
    @Positive private Long storeId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Size(max = 24)
    private String status;

    public void validateFilters(Set<String> statuses) {
        if ((startDate == null) != (endDate == null)
                || startDate != null && !endDate.isAfter(startDate))
            throw exception(BUSINESS_DATE_INVALID);
        if (status != null && !statuses.contains(status)) throw exception(MOBILE_REQUEST_INVALID);
    }

    public Instant from() {
        return startDate == null ? null : startDate.atStartOfDay(GymDateRange.ZONE).toInstant();
    }

    public Instant to() {
        return endDate == null ? null : endDate.atStartOfDay(GymDateRange.ZONE).toInstant();
    }
}
