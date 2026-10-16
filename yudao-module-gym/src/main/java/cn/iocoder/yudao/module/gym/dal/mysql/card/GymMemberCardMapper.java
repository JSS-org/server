package cn.iocoder.yudao.module.gym.dal.mysql.card;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberCardDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymMemberCardMapper extends BaseMapperX<GymMemberCardDO> {
    @Select("SELECT * FROM gym_member_card WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymMemberCardDO selectForUpdate(@Param("id") Long id);
}
