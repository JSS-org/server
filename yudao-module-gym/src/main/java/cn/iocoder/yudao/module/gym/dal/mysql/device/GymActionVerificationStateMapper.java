package cn.iocoder.yudao.module.gym.dal.mysql.device;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.GymActionVerificationStateDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymActionVerificationStateMapper
        extends BaseMapperX<GymActionVerificationStateDO> {
    @Insert(
            "INSERT INTO gym_action_verification_state(tenant_id,user_id) VALUES(#{tenant},#{user}) ON DUPLICATE KEY UPDATE id=id")
    int ensure(@Param("tenant") Long tenant, @Param("user") Long user);

    // Include tombstones: logical deletion must not reset an account's lockout.
    @Select(
            "SELECT * FROM gym_action_verification_state WHERE tenant_id=#{tenant} AND user_id=#{user} FOR UPDATE")
    GymActionVerificationStateDO lock(@Param("tenant") Long tenant, @Param("user") Long user);

    @Update(
            "UPDATE gym_action_verification_state SET failure_count=#{failures},locked_until=#{until},last_attempt_at=#{now} WHERE tenant_id=#{tenant} AND user_id=#{user}")
    int saveAttempt(
            @Param("tenant") Long tenant,
            @Param("user") Long user,
            @Param("failures") int failures,
            @Param("until") java.time.Instant until,
            @Param("now") java.time.Instant now);
}
