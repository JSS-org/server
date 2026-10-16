package cn.iocoder.yudao.module.gym.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymEmployeeStoreMapper extends BaseMapperX<GymEmployeeStoreDO> {
    // 仅整体替换关系；租户拦截器对 DELETE 同样生效。
    @Delete("DELETE FROM gym_employee_store WHERE employee_id = #{id}")
    int deleteRelations(@Param("id") Long id);
}
