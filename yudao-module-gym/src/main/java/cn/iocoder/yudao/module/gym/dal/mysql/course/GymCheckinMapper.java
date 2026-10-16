package cn.iocoder.yudao.module.gym.dal.mysql.course;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCheckinDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymCheckinMapper extends BaseMapperX<GymCheckinDO> {
    @Select("SELECT * FROM gym_checkin WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymCheckinDO selectForUpdate(@Param("id") Long id);
}
