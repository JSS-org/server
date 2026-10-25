package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileSalesReportAggregate.*;

import org.apache.ibatis.annotations.*;

import java.util.List;

/** Financial events are already attributed shares. Never multiply them by order-item joins. */
@Mapper
public interface GymMobileSalesReportMapper {
    String SCOPE =
            "e.tenant_id=#{q.tenant} AND e.deleted=0 AND e.event_type IN ('payment','refund') AND e.product_type IN ('card','private') AND e.store_id IN <foreach collection='q.stores' item='s' open='(' separator=',' close=')'>#{s}</foreach> AND e.occurred_at &gt;= #{q.from} AND e.occurred_at &lt; #{q.to} <if test='q.product != null'>AND e.product_type=#{q.product}</if> <if test='q.employee != null'>AND e.employee_id=#{q.employee}</if>";
    String GROUPED =
            "SELECT MIN(e.id) id,e.business_order_id orderId,e.store_id storeId,e.employee_id employeeId,e.product_type productType,SUM(CASE WHEN e.event_type='payment' THEN e.amount ELSE 0 END) paidAmount,SUM(CASE WHEN e.event_type='refund' THEN e.amount ELSE 0 END) refundedAmount,MAX(e.occurred_at) latestAt FROM gym_financial_event e WHERE "
                    + SCOPE
                    + " GROUP BY e.business_order_id,e.store_id,e.employee_id,e.product_type";

    @Select("<script>SELECT COUNT(*) FROM (" + GROUPED + ") g</script>")
    long count(@Param("q") Criteria q);

    @Select(
            "<script>SELECT g.*,o.order_no orderNo,(SELECT MIN(p.occurred_at) FROM gym_financial_event p WHERE p.tenant_id=#{q.tenant} AND p.deleted=0 AND p.event_type='payment' AND p.business_order_id=g.orderId AND p.store_id=g.storeId AND p.product_type=g.productType AND (p.employee_id=g.employeeId OR (p.employee_id IS NULL AND g.employeeId IS NULL))) paidAt FROM ("
                    + GROUPED
                    + ") g LEFT JOIN gym_business_order o ON o.id=g.orderId AND o.store_id=g.storeId AND o.tenant_id=#{q.tenant} AND o.deleted=0 ORDER BY g.latestAt DESC,g.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Row> rows(@Param("q") Criteria q, @Param("size") int size, @Param("offset") long offset);

    @Select(
            "<script>SELECT COALESCE(SUM(CASE WHEN e.event_type='payment' THEN e.amount ELSE 0 END),0) paidAmount,COALESCE(SUM(CASE WHEN e.event_type='refund' THEN e.amount ELSE 0 END),0) refundedAmount,COUNT(DISTINCT CASE WHEN e.event_type='payment' THEN e.business_order_id END) paidOrderCount,COALESCE(SUM(CASE WHEN e.product_type='card' THEN CASE WHEN e.event_type='refund' THEN -e.amount ELSE e.amount END ELSE 0 END),0) cardAmount,COUNT(DISTINCT CASE WHEN e.event_type='payment' AND e.product_type='card' THEN e.business_order_id END) cardOrderCount,COALESCE(SUM(CASE WHEN e.product_type='private' THEN CASE WHEN e.event_type='refund' THEN -e.amount ELSE e.amount END ELSE 0 END),0) courseAmount,COUNT(DISTINCT CASE WHEN e.event_type='payment' AND e.product_type='private' THEN e.business_order_id END) courseOrderCount FROM gym_financial_event e WHERE "
                    + SCOPE
                    + "</script>")
    Totals totals(@Param("q") Criteria q);
}
