package cn.iocoder.yudao.module.gym.dal.mysql.member;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMeasurementDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymMeasurementMapper extends BaseMapperX<GymMeasurementDO> {
    @Select("SELECT * FROM gym_measurement WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymMeasurementDO selectForUpdate(@Param("id") Long id);
}
