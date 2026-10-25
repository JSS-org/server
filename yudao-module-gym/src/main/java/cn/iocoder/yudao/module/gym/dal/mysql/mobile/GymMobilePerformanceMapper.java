package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobilePerformanceAggregate.*;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GymMobilePerformanceMapper {
    String STORES =
            "<foreach collection='q.stores' item='s' open='(' separator=',' close=')'>#{s}</foreach>";
    String FINANCIAL_ID =
            "<choose><when test='q.byEmployee'>e.employee_id</when><otherwise>e.store_id</otherwise></choose>";
    String LESSON_ID =
            "<choose><when test='q.byEmployee'>l.coach_id</when><otherwise>l.store_id</otherwise></choose>";

    @Select(
            "<script>SELECT "
                    + FINANCIAL_ID
                    + " id,SUM(CASE WHEN e.event_type='refund' THEN -e.amount ELSE e.amount END) netAmount,COUNT(DISTINCT CASE WHEN e.event_type='payment' THEN e.business_order_id END) paidOrderCount FROM gym_financial_event e WHERE e.tenant_id=#{q.tenant} AND e.deleted=0 AND e.event_type IN ('payment','refund') AND e.product_type IN ('card','private') AND e.store_id IN "
                    + STORES
                    + " AND e.occurred_at &gt;= #{q.from} AND e.occurred_at &lt; #{q.to} <if test='q.employee != null'>AND e.employee_id=#{q.employee}</if> GROUP BY "
                    + FINANCIAL_ID
                    + "</script>")
    List<Amount> amounts(@Param("q") Criteria q);

    @Select(
            "<script>SELECT "
                    + LESSON_ID
                    + " id,COUNT(*) lessonCount FROM gym_lesson_record l WHERE l.tenant_id=#{q.tenant} AND l.deleted=0 AND l.status='confirmed' AND l.store_id IN "
                    + STORES
                    + " AND l.confirmed_at &gt;= #{q.from} AND l.confirmed_at &lt; #{q.to} <if test='q.employee != null'>AND l.coach_id=#{q.employee}</if> GROUP BY "
                    + LESSON_ID
                    + "</script>")
    List<Lessons> lessons(@Param("q") Criteria q);
}
