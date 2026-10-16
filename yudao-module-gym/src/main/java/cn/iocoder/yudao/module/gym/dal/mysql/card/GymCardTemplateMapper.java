package cn.iocoder.yudao.module.gym.dal.mysql.card;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymCardTemplateDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymCardTemplateMapper extends BaseMapperX<GymCardTemplateDO> {
    @Select("SELECT * FROM gym_card_template WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymCardTemplateDO selectForUpdate(@Param("id") Long id);
}
