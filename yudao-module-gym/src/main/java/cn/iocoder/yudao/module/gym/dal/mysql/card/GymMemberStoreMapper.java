package cn.iocoder.yudao.module.gym.dal.mysql.card;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymMemberStoreMapper extends BaseMapperX<GymMemberStoreDO> {
    @Select("SELECT * FROM gym_member_store WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymMemberStoreDO selectForUpdate(@Param("id") Long id);
}
