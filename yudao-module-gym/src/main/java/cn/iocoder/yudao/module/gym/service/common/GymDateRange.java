package cn.iocoder.yudao.module.gym.service.common;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.BUSINESS_DATE_INVALID;

import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;

import java.time.*;

/** ISO instants and Asia/Shanghai business dates; ranges have an exclusive upper bound. */
public record GymDateRange(Instant from, Instant to) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static GymDateRange of(GymBusinessPageReqVO req) {
        int modes =
                (req.getDate() != null ? 1 : 0)
                        + (req.getMonth() != null ? 1 : 0)
                        + (req.getFrom() != null || req.getTo() != null ? 1 : 0);
        if (modes > 1) throw exception(BUSINESS_DATE_INVALID);
        try {
            Instant from = parse(req.getFrom()), to = parse(req.getTo());
            if (req.getDate() != null) {
                from = req.getDate().atStartOfDay(ZONE).toInstant();
                to = req.getDate().plusDays(1).atStartOfDay(ZONE).toInstant();
            }
            if (req.getMonth() != null) {
                LocalDate day = YearMonth.parse(req.getMonth()).atDay(1);
                from = day.atStartOfDay(ZONE).toInstant();
                to = day.plusMonths(1).atStartOfDay(ZONE).toInstant();
            }
            if (from != null && to != null && !to.isAfter(from))
                throw exception(BUSINESS_DATE_INVALID);
            return new GymDateRange(from, to);
        } catch (DateTimeException e) {
            throw exception(BUSINESS_DATE_INVALID);
        }
    }

    private static Instant parse(String value) {
        if (value == null) return null;
        return value.length() == 10
                ? LocalDate.parse(value).atStartOfDay(ZONE).toInstant()
                : Instant.parse(value);
    }
}
