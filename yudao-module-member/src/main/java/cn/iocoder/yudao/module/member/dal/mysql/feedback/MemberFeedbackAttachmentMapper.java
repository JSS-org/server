package cn.iocoder.yudao.module.member.dal.mysql.feedback;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.feedback.MemberFeedbackAttachmentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberFeedbackAttachmentMapper extends BaseMapperX<MemberFeedbackAttachmentDO> {
    // Read IDs only; never load private blobs into a cleanup worker.
    @Select("SELECT id FROM member_feedback_attachment WHERE tenant_id = #{tenantId} "
            + "AND feedback_id IS NULL AND create_time < #{cutoff} ORDER BY id LIMIT #{limit}")
    List<Long> selectExpiredUnclaimedIds(@Param("tenantId") Long tenantId,
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    // Physical deletion releases the blob. Recheck every predicate after candidate selection,
    // so a feedback submission that claimed the image meanwhile always keeps its attachment.
    @Delete("<script>DELETE FROM member_feedback_attachment WHERE tenant_id = #{tenantId} "
            + "AND feedback_id IS NULL AND create_time &lt; #{cutoff} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteExpiredUnclaimedIds(@Param("ids") Collection<Long> ids, @Param("tenantId") Long tenantId,
            @Param("cutoff") LocalDateTime cutoff);

    default MemberFeedbackAttachmentDO selectOwned(Long id, Long userId, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<MemberFeedbackAttachmentDO>()
                .eq(MemberFeedbackAttachmentDO::getId, id)
                .eq(MemberFeedbackAttachmentDO::getUserId, userId)
                .eq(MemberFeedbackAttachmentDO::getTenantId, tenantId));
    }

    default List<MemberFeedbackAttachmentDO> lockOwned(Collection<Long> ids, Long userId, Long tenantId) {
        return selectList(new LambdaQueryWrapperX<MemberFeedbackAttachmentDO>()
                .select(MemberFeedbackAttachmentDO::getId, MemberFeedbackAttachmentDO::getFeedbackId)
                .in(MemberFeedbackAttachmentDO::getId, ids)
                .eq(MemberFeedbackAttachmentDO::getUserId, userId)
                .eq(MemberFeedbackAttachmentDO::getTenantId, tenantId)
                .orderByAsc(MemberFeedbackAttachmentDO::getId).last("FOR UPDATE"));
    }

    default int attach(Long id, Long userId, Long tenantId, Long feedbackId, int sort) {
        return update(null, new LambdaUpdateWrapper<MemberFeedbackAttachmentDO>()
                .eq(MemberFeedbackAttachmentDO::getId, id)
                .eq(MemberFeedbackAttachmentDO::getUserId, userId)
                .eq(MemberFeedbackAttachmentDO::getTenantId, tenantId)
                .isNull(MemberFeedbackAttachmentDO::getFeedbackId)
                .set(MemberFeedbackAttachmentDO::getFeedbackId, feedbackId)
                .set(MemberFeedbackAttachmentDO::getSort, sort));
    }

    default List<MemberFeedbackAttachmentDO> selectByFeedback(Long feedbackId, Long userId, Long tenantId) {
        return selectList(new LambdaQueryWrapperX<MemberFeedbackAttachmentDO>()
                .select(MemberFeedbackAttachmentDO::getId)
                .eq(MemberFeedbackAttachmentDO::getFeedbackId, feedbackId)
                .eq(MemberFeedbackAttachmentDO::getUserId, userId)
                .eq(MemberFeedbackAttachmentDO::getTenantId, tenantId)
                .orderByAsc(MemberFeedbackAttachmentDO::getSort, MemberFeedbackAttachmentDO::getId));
    }
}
