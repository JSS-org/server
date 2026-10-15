package cn.iocoder.yudao.module.gym.dal.mysql.marketing;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.marketing.GymActivityStoreDO;
import org.apache.ibatis.annotations.*;
@Mapper public interface GymActivityStoreMapper extends BaseMapperX<GymActivityStoreDO> {
 @Delete("DELETE FROM gym_activity_store WHERE activity_id=#{id}") int deleteRelations(@Param("id") Long id);
}
