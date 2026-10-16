package cn.iocoder.yudao.module.gym.dal.mysql.store;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymStoreMapper extends BaseMapperX<GymStoreDO> {
    @Select("SELECT * FROM gym_store WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymStoreDO selectForUpdate(@Param("id") Long id);
}
