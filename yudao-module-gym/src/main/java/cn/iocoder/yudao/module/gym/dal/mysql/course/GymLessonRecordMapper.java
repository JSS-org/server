package cn.iocoder.yudao.module.gym.dal.mysql.course;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymLessonRecordDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymLessonRecordMapper extends BaseMapperX<GymLessonRecordDO> {
    @Select("SELECT * FROM gym_lesson_record WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymLessonRecordDO selectForUpdate(@Param("id") Long id);
}
