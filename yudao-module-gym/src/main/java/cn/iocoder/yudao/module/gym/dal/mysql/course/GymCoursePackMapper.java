package cn.iocoder.yudao.module.gym.dal.mysql.course;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCoursePackDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymCoursePackMapper extends BaseMapperX<GymCoursePackDO> {
    @Select("SELECT * FROM gym_course_pack WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymCoursePackDO selectForUpdate(@Param("id") Long id);
}
