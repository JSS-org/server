package cn.iocoder.yudao.module.gym.dal.mysql.course;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseQuoteDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymCourseQuoteMapper extends BaseMapperX<GymCourseQuoteDO> {
    @Select("SELECT * FROM gym_course_quote WHERE id=#{id} AND deleted=0 FOR UPDATE")
    GymCourseQuoteDO selectForUpdate(@Param("id") Long id);
}
