package cn.iocoder.yudao.module.gym.dal.mysql.member;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymTrainingPlanMapper extends BaseMapperX<GymTrainingPlanDO> {
    @Select("SELECT * FROM gym_training_plan WHERE id=#{id} AND deleted=0 FOR UPDATE")
    GymTrainingPlanDO selectForUpdate(@Param("id") Long id);
}
