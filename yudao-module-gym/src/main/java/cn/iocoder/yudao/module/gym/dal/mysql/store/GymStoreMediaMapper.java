package cn.iocoder.yudao.module.gym.dal.mysql.store;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreMediaDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GymStoreMediaMapper extends BaseMapperX<GymStoreMediaDO> {
    // 仅整体替换关系；租户拦截器对 DELETE 同样生效。
    @Delete("DELETE FROM gym_store_media WHERE store_id = #{id}")
    int deleteRelations(@Param("id") Long id);
}
