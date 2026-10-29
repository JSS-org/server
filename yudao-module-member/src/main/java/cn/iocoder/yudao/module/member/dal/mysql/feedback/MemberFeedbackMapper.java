package cn.iocoder.yudao.module.member.dal.mysql.feedback;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.feedback.MemberFeedbackDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberFeedbackMapper extends BaseMapperX<MemberFeedbackDO> {
    default MemberFeedbackDO selectOwned(Long id, Long userId, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<MemberFeedbackDO>()
                .eq(MemberFeedbackDO::getId, id).eq(MemberFeedbackDO::getUserId, userId)
                .eq(MemberFeedbackDO::getTenantId, tenantId));
    }
}
