package cn.iocoder.yudao.module.gym.dal.mysql.contract;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface GymContractMapper extends BaseMapperX<GymContractDO> {
 default GymContractDO selectForUpdate(Long id){return selectOne(new LambdaQueryWrapper<GymContractDO>().eq(GymContractDO::getId,id).last("FOR UPDATE"));}
}
