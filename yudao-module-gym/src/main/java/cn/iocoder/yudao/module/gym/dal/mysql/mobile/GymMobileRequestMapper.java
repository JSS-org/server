package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileRequestDO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GymMobileRequestMapper extends BaseMapperX<GymMobileRequestDO> {

    @Insert(
            "INSERT INTO gym_mobile_request (tenant_id,user_type,user_id,operation,request_key,request_hash) VALUES (#{tenant},#{type},#{user},#{operation},#{key},#{hash}) ON DUPLICATE KEY UPDATE id=id")
    int ensure(
            @Param("tenant") Long tenant,
            @Param("type") Integer type,
            @Param("user") Long user,
            @Param("operation") String operation,
            @Param("key") String key,
            @Param("hash") String hash);

    @Select(
            "SELECT * FROM gym_mobile_request WHERE tenant_id=#{tenant} AND user_type=#{type} AND user_id=#{user} AND operation=#{operation} AND request_key=#{key} AND deleted=0 FOR UPDATE")
    GymMobileRequestDO lock(
            @Param("tenant") Long tenant,
            @Param("type") Integer type,
            @Param("user") Long user,
            @Param("operation") String operation,
            @Param("key") String key);
}
