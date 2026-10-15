package cn.iocoder.yudao.module.gym.dal.mysql.member;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymMemberMapper extends BaseMapperX<GymMemberDO> {
    @Select("SELECT * FROM gym_member WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GymMemberDO selectForUpdate(@Param("id") Long id);
}
