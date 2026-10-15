package cn.iocoder.yudao.module.gym.dal.mysql.course;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseSessionDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymCourseSessionMapper extends BaseMapperX<GymCourseSessionDO> {
    @Select("SELECT * FROM gym_course_session WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymCourseSessionDO selectForUpdate(@Param("id") Long id);
}
