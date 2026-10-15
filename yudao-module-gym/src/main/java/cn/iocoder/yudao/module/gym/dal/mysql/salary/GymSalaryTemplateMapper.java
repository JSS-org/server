package cn.iocoder.yudao.module.gym.dal.mysql.salary;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.salary.GymSalaryTemplateDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymSalaryTemplateMapper extends BaseMapperX<GymSalaryTemplateDO> {
    @Select("SELECT * FROM gym_salary_template WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymSalaryTemplateDO selectForUpdate(@Param("id") Long id);
}
