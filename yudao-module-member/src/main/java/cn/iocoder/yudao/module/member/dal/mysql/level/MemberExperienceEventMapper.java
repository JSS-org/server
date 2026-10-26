package cn.iocoder.yudao.module.member.dal.mysql.level;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberExperienceEventDO;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberExperienceEventMapper extends BaseMapperX<MemberExperienceEventDO> {
    default MemberExperienceEventDO selectEventForUpdate(Long userId, Integer type, String bizId) {
        return selectOneForUpdate(
                MemberExperienceEventDO::getUserId,
                userId,
                MemberExperienceEventDO::getBizType,
                type,
                MemberExperienceEventDO::getBizId,
                bizId);
    }
}
