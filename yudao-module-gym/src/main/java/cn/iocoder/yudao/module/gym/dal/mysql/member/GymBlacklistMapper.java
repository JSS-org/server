package cn.iocoder.yudao.module.gym.dal.mysql.member;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymBlacklistDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymBlacklistMapper extends BaseMapperX<GymBlacklistDO> {
    @Select("SELECT * FROM gym_blacklist WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymBlacklistDO selectForUpdate(@Param("id") Long id);
}
