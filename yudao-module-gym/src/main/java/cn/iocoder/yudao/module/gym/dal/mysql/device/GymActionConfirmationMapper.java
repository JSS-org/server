package cn.iocoder.yudao.module.gym.dal.mysql.device;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.GymActionConfirmationDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymActionConfirmationMapper extends BaseMapperX<GymActionConfirmationDO> {
    // Accepted counters remain spent after expiration, consumption and logical deletion.
    @Select(
            "SELECT MAX(verification_counter) FROM gym_action_confirmation WHERE tenant_id=#{tenant} AND user_id=#{user} AND credential_fingerprint=#{fingerprint}")
    Long lastCounter(
            @Param("tenant") Long tenant,
            @Param("user") Long user,
            @Param("fingerprint") String fingerprint);
}
