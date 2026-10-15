package cn.iocoder.yudao.module.gym.dal.mysql.common;
import org.apache.ibatis.annotations.*;
/** Tenant-scoped transaction mutex for all-store entitlement issuance and store activation. */
@Mapper
public interface GymBusinessMutexMapper {
    @Insert("INSERT INTO gym_business_mutex (tenant_id,id) VALUES (#{tenantId},1) ON DUPLICATE KEY UPDATE id=id")
    int ensure(@Param("tenantId") Long tenantId);
    @Select("SELECT id FROM gym_business_mutex WHERE tenant_id=#{tenantId} AND id=1 FOR UPDATE")
    Long lock(@Param("tenantId") Long tenantId);
}
