package cn.iocoder.yudao.module.gym.dal.mysql.course;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseProductDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymCourseProductMapper extends BaseMapperX<GymCourseProductDO> {
    @Select("SELECT * FROM gym_course_product WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymCourseProductDO selectForUpdate(@Param("id") Long id);
}
