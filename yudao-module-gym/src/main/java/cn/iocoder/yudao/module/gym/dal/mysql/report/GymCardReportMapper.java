package cn.iocoder.yudao.module.gym.dal.mysql.report;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/** Every join is tenant-qualified; store sets and date bounds are supplied only by the server scope. */
@Mapper
public interface GymCardReportMapper {
    String STORES =
            "<foreach collection='stores' item='store' open='(' separator=',' close=')'>#{store}</foreach>";
    String EVENT_SCOPE =
            "e.tenant_id=#{tenant} AND e.deleted=0 AND e.product_type='card' AND e.store_id IN "
                    + STORES
                    + " <if test='from != null'>AND e.occurred_at &gt;= #{from}</if> <if test='to != null'>AND e.occurred_at &lt; #{to}</if>";

    @Select(
            "<script>SELECT i.product_id templateId, MAX(i.product_name_snapshot) name, MAX(i.product_version) version, SUM(CASE WHEN e.event_type='refund' THEN -a.amount ELSE a.amount END) amount FROM gym_order_item_financial_event a JOIN gym_financial_event e ON e.id=a.financial_event_id AND e.tenant_id=#{tenant} JOIN gym_order_item i ON i.id=a.order_item_id AND i.order_id=e.business_order_id AND i.tenant_id=#{tenant} AND i.deleted=0 AND i.product_type='card' JOIN gym_business_order o ON o.id=i.order_id AND o.store_id=e.store_id AND o.tenant_id=#{tenant} AND o.deleted=0 AND o.business_type='card_sale' WHERE a.tenant_id=#{tenant} AND a.deleted=0 AND "
                    + EVENT_SCOPE
                    + " GROUP BY i.product_id</script>")
    List<GymCardReportAggregate> net(
            @Param("tenant") Long tenant,
            @Param("stores") List<Long> stores,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Select(
            "<script>SELECT COALESCE(SUM(CASE WHEN e.event_type='refund' THEN -e.amount ELSE e.amount END),0) amount, COUNT(DISTINCT CASE WHEN e.event_type='payment' THEN e.business_order_id ELSE NULL END) count FROM gym_financial_event e WHERE "
                    + EVENT_SCOPE
                    + "</script>")
    GymCardReportAggregate totals(
            @Param("tenant") Long tenant,
            @Param("stores") List<Long> stores,
            @Param("from") Instant from,
            @Param("to") Instant to);

    String SOLD_AT = "CASE WHEN o.payable_amount=0 THEN pol.fulfilled_at ELSE p.completed_at END";

    @Select(
            "<script>SELECT i.product_id templateId,MAX(i.product_name_snapshot) name,MAX(i.product_version) version,SUM(i.quantity) count FROM gym_business_order o JOIN gym_order_item i ON i.order_id=o.id AND i.tenant_id=#{tenant} AND i.deleted=0 AND i.product_type='card' LEFT JOIN (SELECT order_id,SUM(amount) paid_amount,MAX(paid_at) completed_at FROM gym_order_payment WHERE tenant_id=#{tenant} AND deleted=0 AND status='paid' GROUP BY order_id) p ON p.order_id=o.id LEFT JOIN gym_order_policy pol ON pol.order_id=o.id AND pol.tenant_id=#{tenant} AND pol.deleted=0 WHERE o.tenant_id=#{tenant} AND o.deleted=0 AND o.business_type='card_sale' AND o.store_id IN "
                    + STORES
                    + " AND ((o.payable_amount&gt;0 AND p.paid_amount=o.payable_amount AND o.paid_amount=p.paid_amount) OR (o.payable_amount=0 AND o.status='fulfilled' AND pol.fulfilled_at IS NOT NULL)) <if test='from != null'>AND "
                    + SOLD_AT
                    + " &gt;= #{from}</if> <if test='to != null'>AND "
                    + SOLD_AT
                    + " &lt; #{to}</if> GROUP BY i.product_id</script>")
    List<GymCardReportAggregate> sold(
            @Param("tenant") Long tenant,
            @Param("stores") List<Long> stores,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Select(
            "<script>SELECT c.template_id templateId,MAX(c.name) name,MAX(c.template_snapshot) templateSnapshot,COUNT(DISTINCT c.member_id) count FROM gym_member_card c JOIN gym_member m ON m.id=c.member_id AND m.tenant_id=#{tenant} AND m.deleted=0 AND m.status='active' WHERE c.tenant_id=#{tenant} AND c.deleted=0 AND c.status='active' AND c.starts_at &lt;= #{now} AND (c.expires_at IS NULL OR c.expires_at &gt; #{now}) AND (c.kind='period' OR (c.kind='count' AND c.remaining_uses&gt;0)) AND EXISTS(SELECT 1 FROM gym_member_card_store cs WHERE cs.card_id=c.id AND cs.tenant_id=#{tenant} AND cs.deleted=0 AND cs.store_id IN "
                    + STORES
                    + ") GROUP BY c.template_id</script>")
    List<GymCardReportAggregate> holders(
            @Param("tenant") Long tenant,
            @Param("stores") List<Long> stores,
            @Param("now") Instant now);

    @Select(
            "<script>SELECT c.template_id templateId,MAX(c.name) name,MAX(c.template_snapshot) templateSnapshot,SUM(l.session_units) count FROM gym_lesson_record l JOIN gym_member_card c ON c.id=l.member_card_id AND c.tenant_id=#{tenant} WHERE l.tenant_id=#{tenant} AND l.deleted=0 AND l.status='confirmed' AND l.store_id IN "
                    + STORES
                    + " <if test='from != null'>AND l.confirmed_at &gt;= #{from}</if> <if test='to != null'>AND l.confirmed_at &lt; #{to}</if> GROUP BY c.template_id</script>")
    List<GymCardReportAggregate> usages(
            @Param("tenant") Long tenant,
            @Param("stores") List<Long> stores,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
