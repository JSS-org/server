package cn.iocoder.yudao.module.jss.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssUserIdentityDO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JssUserIdentityMapper extends BaseMapperX<JssUserIdentityDO> {
    default JssUserIdentityDO findByMember(Long memberId) {
        return selectOne(JssUserIdentityDO::getMemberUserId, memberId);
    }

    @Select("SELECT * FROM jss_user_identity WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    JssUserIdentityDO selectForUpdate(@Param("id") Long id);

    @Update(
            "UPDATE jss_user_identity SET status = 1, binding_version = binding_version + 1, "
                    + "updater = #{operator}, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int revoke(@Param("id") Long id, @Param("operator") String operator);
}
