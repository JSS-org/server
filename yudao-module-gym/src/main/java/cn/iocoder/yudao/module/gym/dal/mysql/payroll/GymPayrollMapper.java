package cn.iocoder.yudao.module.gym.dal.mysql.payroll;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.payroll.GymPayrollDO;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GymPayrollMapper extends BaseMapperX<GymPayrollDO> {}
