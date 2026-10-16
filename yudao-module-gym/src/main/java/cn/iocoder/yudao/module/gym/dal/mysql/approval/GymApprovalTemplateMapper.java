package cn.iocoder.yudao.module.gym.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.GymApprovalTemplateDO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GymApprovalTemplateMapper extends BaseMapperX<GymApprovalTemplateDO> {
    default GymApprovalTemplateDO selectForUpdate(Long id) {
        return selectOne(
                new LambdaQueryWrapper<GymApprovalTemplateDO>()
                        .eq(GymApprovalTemplateDO::getId, id)
                        .last("FOR UPDATE"));
    }
}
