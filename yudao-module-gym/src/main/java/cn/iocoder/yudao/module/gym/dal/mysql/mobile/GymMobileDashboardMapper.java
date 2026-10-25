package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileDashboardAggregate.*;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymMobileDashboardMapper {
    String STORES =
            "<foreach collection='q.stores' item='s' open='(' separator=',' close=')'>#{s}</foreach>";
    String NET = "CASE WHEN f.event_type='refund' THEN -f.amount ELSE f.amount END";

    @Select(
            "<script>SELECT COALESCE(SUM(CASE WHEN f.occurred_at &gt;= #{q.day} THEN "
                    + NET
                    + " ELSE 0 END),0) todayAmount,COALESCE(SUM("
                    + NET
                    + "),0) monthAmount FROM gym_financial_event f WHERE f.tenant_id=#{q.tenant} AND f.deleted=0 AND f.event_type IN ('payment','refund') AND f.product_type IN ('card','private') AND f.store_id IN "
                    + STORES
                    + " AND f.occurred_at &gt;= #{q.month} AND f.occurred_at &lt; #{q.end} AND f.occurred_at &lt;= #{q.now} <if test='q.employee != null'>AND f.employee_id=#{q.employee}</if></script>")
    Amount amounts(@Param("q") Criteria q);

    // Scope employee and store eligibility have already been resolved from the current account.
    // Both event queries correlate responsibility to this event's member AND store.
    String RESPONSIBILITY =
            "EXISTS(SELECT 1 FROM gym_member_coach mc JOIN gym_member_store ms ON ms.member_id=mc.member_id AND ms.store_id=mc.store_id AND ms.tenant_id=#{q.tenant} AND ms.deleted=0 AND ms.joined_at &lt;= #{q.now} WHERE mc.tenant_id=#{q.tenant} AND mc.deleted=0 AND mc.active=1 AND mc.employee_id=#{q.employee} AND mc.member_id=e.member_id AND mc.store_id=e.store_id)";
    String COUNT =
            "COALESCE(SUM(CASE WHEN e.event_time &gt;= #{q.day} THEN 1 ELSE 0 END),0) todayCount,COALESCE(SUM(CASE WHEN e.event_time &gt;= #{q.yesterday} AND e.event_time &lt; #{q.day} THEN 1 ELSE 0 END),0) yesterdayCount,COALESCE(SUM(CASE WHEN e.event_time &gt;= #{q.month} THEN 1 ELSE 0 END),0) monthCount";
    String EVENT_FILTER =
            " WHERE e.tenant_id=#{q.tenant} AND e.deleted=0 AND e.store_id IN "
                    + STORES
                    + " AND e.event_time &gt;= #{q.scanFrom} AND e.event_time &lt; #{q.end} AND e.event_time &lt;= #{q.now} <if test='q.employee != null'>AND "
                    + RESPONSIBILITY
                    + "</if>";

    @Select(
            "<script>SELECT "
                    + COUNT
                    + " FROM (SELECT tenant_id,deleted,store_id,member_id,status,entered_at event_time FROM gym_checkin) e "
                    + EVENT_FILTER
                    + " AND e.status='succeeded'</script>")
    Counts checkins(@Param("q") Criteria q);

    @Select(
            "<script>SELECT "
                    + COUNT
                    + " FROM (SELECT tenant_id,deleted,store_id,member_id,coach_id,status,confirmed_at event_time FROM gym_lesson_record) e "
                    + EVENT_FILTER
                    + " AND e.status='confirmed' <if test='q.employee != null'>AND e.coach_id=#{q.employee}</if></script>")
    Counts lessons(@Param("q") Criteria q);

    @Select(
            "<script>SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN m.registered_at &gt;= #{q.day} AND m.registered_at &lt; #{q.end} THEN 1 ELSE 0 END),0) newTodayCount FROM gym_member m WHERE m.tenant_id=#{q.tenant} AND m.deleted=0 AND m.status='active' AND m.registered_at &lt;= #{q.now} AND EXISTS(SELECT 1 FROM gym_member_store ms WHERE ms.tenant_id=#{q.tenant} AND ms.deleted=0 AND ms.member_id=m.id AND ms.joined_at &lt;= #{q.now} AND ms.store_id IN "
                    + STORES
                    + " <if test='q.employee != null'>AND EXISTS(SELECT 1 FROM gym_member_coach mc WHERE mc.tenant_id=#{q.tenant} AND mc.deleted=0 AND mc.active=1 AND mc.employee_id=#{q.employee} AND mc.member_id=m.id AND mc.store_id=ms.store_id)</if>)</script>")
    Population members(@Param("q") Criteria q);

    @Select(
            "<script>SELECT COUNT(*) FROM gym_approval a WHERE a.tenant_id=#{q.tenant} AND a.deleted=0 AND a.status='pending' AND a.store_id IN "
                    + STORES
                    + " AND EXISTS(SELECT 1 FROM gym_approval_task t JOIN gym_employee e ON e.id=t.approver_employee_id AND e.tenant_id=#{q.tenant} AND e.deleted=0 AND e.status='active' AND e.user_id=t.approver_user_id WHERE t.tenant_id=#{q.tenant} AND t.deleted=0 AND t.approval_id=a.id AND t.status='pending' AND t.step_no=a.current_step AND t.approver_user_id=#{q.user})</script>")
    long pending(@Param("q") Criteria q);
}
