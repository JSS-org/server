package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileMemberSummaryAggregate.Criteria;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymMobileMemberSummaryMapper {
    String STORES =
            "<foreach collection='q.stores' item='s' open='(' separator=',' close=')'>#{s}</foreach>";
    String MEMBER_STORE =
            "ms.tenant_id=#{q.tenant} AND ms.deleted=0 AND ms.member_id=m.id AND ms.joined_at &lt;= #{q.now} AND ms.store_id IN "
                    + STORES;
    String PACK =
            "SELECT 1 FROM gym_course_pack p JOIN gym_member_store ms ON ms.store_id=p.store_id AND ms.member_id=p.member_id WHERE p.tenant_id=#{q.tenant} AND p.deleted=0 AND p.member_id=m.id AND p.status='active' AND p.remaining_sessions&gt;0 AND (p.starts_at IS NULL OR p.starts_at &lt;= #{q.now}) AND (p.expires_at IS NULL OR p.expires_at &gt; #{q.now}) AND "
                    + MEMBER_STORE;
    String CARD =
            "SELECT 1 FROM gym_member_card c JOIN gym_member_card_store cs ON cs.card_id=c.id AND cs.tenant_id=#{q.tenant} AND cs.deleted=0 JOIN gym_member_store ms ON ms.store_id=cs.store_id AND ms.member_id=c.member_id WHERE c.tenant_id=#{q.tenant} AND c.deleted=0 AND c.member_id=m.id AND c.status='active' AND (c.kind='period' OR (c.kind='count' AND c.remaining_uses&gt;0)) AND (c.starts_at IS NULL OR c.starts_at &lt;= #{q.now}) AND c.expires_at &gt; #{q.now} AND c.expires_at &lt;= #{q.limit} AND "
                    + MEMBER_STORE;

    @Select(
            "<script>SELECT COUNT(*) memberCount,COALESCE(SUM(CASE WHEN EXISTS("
                    + PACK
                    + ") THEN 1 ELSE 0 END),0) privateCourseMemberCount,COALESCE(SUM(CASE WHEN m.registered_at &gt;= #{q.dayStart} AND m.registered_at &lt; #{q.dayEnd} THEN 1 ELSE 0 END),0) newMemberCount,COALESCE(SUM(CASE WHEN EXISTS("
                    + CARD
                    + ") THEN 1 ELSE 0 END),0) expiringCardCount,COALESCE(SUM(CASE WHEN EXISTS("
                    + PACK
                    + " AND p.expires_at &lt;= #{q.limit}) THEN 1 ELSE 0 END),0) expiringCourseCount FROM gym_member m WHERE m.tenant_id=#{q.tenant} AND m.deleted=0 AND m.status='active' AND m.registered_at &lt;= #{q.now} AND EXISTS(SELECT 1 FROM gym_member_store ms WHERE "
                    + MEMBER_STORE
                    + ")</script>")
    GymMobileMemberSummaryAggregate summary(@Param("q") Criteria q);
}
