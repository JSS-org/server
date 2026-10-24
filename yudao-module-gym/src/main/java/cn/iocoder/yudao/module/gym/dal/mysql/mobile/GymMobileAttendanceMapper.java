package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/** SQL aggregates never return the identities of ranking participants. All day bounds are server-generated. */
@Mapper
public interface GymMobileAttendanceMapper {
    record Day(Instant from, Instant to, int value) {}

    String DAYS =
            "COUNT(DISTINCT CASE <foreach collection='days' item='day'>WHEN c.entered_at &gt;= #{day.from} AND c.entered_at &lt; #{day.to} THEN #{day.value} </foreach>END)";
    String SCOPE =
            " FROM gym_checkin c JOIN gym_member m ON m.id=c.member_id AND m.tenant_id=#{tenant} AND m.deleted=0 WHERE c.tenant_id=#{tenant} AND c.deleted=0 AND c.status='succeeded' AND c.store_id=#{store} AND c.entered_at &gt;= #{from} AND c.entered_at &lt; #{to} AND c.entered_at &lt;= #{now} ";

    @Select(
            "<script>SELECT COUNT(*) checkinCount,"
                    + DAYS
                    + " activeDays"
                    + SCOPE
                    + " AND c.member_id=#{member}</script>")
    GymMobileAttendanceAggregate own(
            @Param("tenant") Long tenant,
            @Param("store") Long store,
            @Param("member") Long member,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("now") Instant now,
            @Param("days") List<Day> days);

    @Select(
            "<script>SELECT COUNT(*) population,COALESCE(SUM(CASE WHEN a.activeDays &gt; #{ownDays} THEN 1 ELSE 0 END),0) ahead FROM (SELECT c.member_id,"
                    + DAYS
                    + " activeDays"
                    + SCOPE
                    + " GROUP BY c.member_id) a</script>")
    GymMobileAttendanceAggregate ranking(
            @Param("tenant") Long tenant,
            @Param("store") Long store,
            @Param("ownDays") int ownDays,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("now") Instant now,
            @Param("days") List<Day> days);
}
