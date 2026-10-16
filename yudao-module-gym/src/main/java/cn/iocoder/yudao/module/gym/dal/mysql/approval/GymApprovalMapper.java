package cn.iocoder.yudao.module.gym.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.GymApprovalDO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GymApprovalMapper extends BaseMapperX<GymApprovalDO> {
    default GymApprovalDO selectForUpdate(Long id) {
        return selectOne(
                new LambdaQueryWrapper<GymApprovalDO>()
                        .eq(GymApprovalDO::getId, id)
                        .last("FOR UPDATE"));
    }
}
