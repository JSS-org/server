package cn.iocoder.yudao.module.gym.dal.mysql.contract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractTemplateDO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GymContractTemplateMapper extends BaseMapperX<GymContractTemplateDO> {
    default GymContractTemplateDO selectForUpdate(Long id) {
        return selectOne(
                new LambdaQueryWrapper<GymContractTemplateDO>()
                        .eq(GymContractTemplateDO::getId, id)
                        .last("FOR UPDATE"));
    }

    default boolean isPublishedFile(Long id) {
        return selectCount(
                        new LambdaQueryWrapper<GymContractTemplateDO>()
                                .eq(GymContractTemplateDO::getFileId, id)
                                .eq(GymContractTemplateDO::getStatus, "published"))
                > 0;
    }
}
