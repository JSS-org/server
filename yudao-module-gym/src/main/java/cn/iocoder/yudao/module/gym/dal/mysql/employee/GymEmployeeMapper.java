package cn.iocoder.yudao.module.gym.dal.mysql.employee;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymEmployeeMapper extends BaseMapperX<GymEmployeeDO> {
    @Select("SELECT * FROM gym_employee WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymEmployeeDO selectForUpdate(@Param("id") Long id);
}
