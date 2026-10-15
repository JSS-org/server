package cn.iocoder.yudao.module.gym.dal.mysql.salary;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.salary.GymSalaryAssignmentDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymSalaryAssignmentMapper extends BaseMapperX<GymSalaryAssignmentDO> {
    @Select("SELECT * FROM gym_salary_assignment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymSalaryAssignmentDO selectForUpdate(@Param("id") Long id);
}
