package cn.iocoder.yudao.module.gym.dal.mysql.course;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymBookingDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymBookingMapper extends BaseMapperX<GymBookingDO> {
    @Select("SELECT * FROM gym_booking WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymBookingDO selectForUpdate(@Param("id") Long id);
}
