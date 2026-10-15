package cn.iocoder.yudao.module.gym.dal.mysql.member;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberCoachDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface GymMemberCoachMapper extends BaseMapperX<GymMemberCoachDO> {
 @Select("""
  SELECT mc.* FROM gym_member_coach mc
  JOIN gym_employee e ON e.id=mc.employee_id AND e.tenant_id=mc.tenant_id AND e.deleted=0 AND e.status='active' AND e.hired_on <= #{onDate}
  JOIN gym_employee_store es ON es.employee_id=e.id AND es.store_id=mc.store_id AND es.tenant_id=mc.tenant_id AND es.deleted=0 AND es.joined_on <= #{onDate}
  JOIN gym_member_store ms ON ms.member_id=mc.member_id AND ms.store_id=mc.store_id AND ms.tenant_id=mc.tenant_id AND ms.deleted=0
  JOIN gym_store s ON s.id=mc.store_id AND s.tenant_id=mc.tenant_id AND s.deleted=0 AND s.status='active'
  WHERE mc.tenant_id=#{tenantId} AND mc.deleted=0 AND mc.active=1 AND e.user_id=#{userId}
  """)
 List<GymMemberCoachDO> currentResponsibilities(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("onDate") java.time.LocalDate onDate);
}
