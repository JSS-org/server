package cn.iocoder.yudao.module.member.service.level;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.EXPERIENCE_BIZ_CONFLICT;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.level.vo.experience.MemberExperienceRecordPageReqVO;
import cn.iocoder.yudao.module.member.convert.level.MemberExperienceRecordConvert;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberExperienceEventDO;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberExperienceRecordDO;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberExperienceEventMapper;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberExperienceRecordMapper;
import cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum;

import jakarta.annotation.Resource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

/**
 * 会员经验记录 Service 实现类
 *
 * @author owen
 */
@Service
@Validated
public class MemberExperienceRecordServiceImpl implements MemberExperienceRecordService {

    @Resource private MemberExperienceRecordMapper experienceLogMapper;
    @Resource private MemberExperienceEventMapper experienceEventMapper;

    @Override
    public boolean claimExperienceEvent(
            Long userId,
            MemberExperienceBizTypeEnum bizType,
            String bizId,
            int requestedExperience) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "Experience event claims require the member transaction");
        try {
            // Insert first: a missing-row FOR UPDATE range lookup can gap-lock unrelated users.
            experienceEventMapper.insert(
                    new MemberExperienceEventDO()
                            .setUserId(userId)
                            .setBizType(bizType.getType())
                            .setBizId(bizId)
                            .setRequestedExperience(requestedExperience));
            return true;
        } catch (DuplicateKeyException duplicate) {
            var event =
                    experienceEventMapper.selectEventForUpdate(userId, bizType.getType(), bizId);
            if (event == null) throw duplicate;
            // Legacy requests were not stored; their existing event is sealed without guessing an
            // amount.
            if (event.getRequestedExperience() != null
                    && event.getRequestedExperience() != requestedExperience)
                throw exception(EXPERIENCE_BIZ_CONFLICT);
            return false;
        }
    }

    @Override
    public MemberExperienceRecordDO getExperienceRecord(Long id) {
        return experienceLogMapper.selectById(id);
    }

    @Override
    public PageResult<MemberExperienceRecordDO> getExperienceRecordPage(
            MemberExperienceRecordPageReqVO pageReqVO) {
        return experienceLogMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<MemberExperienceRecordDO> getExperienceRecordPage(
            Long userId, PageParam pageParam) {
        return experienceLogMapper.selectPage(userId, pageParam);
    }

    @Override
    public void createExperienceRecord(
            Long userId,
            Integer experience,
            Integer totalExperience,
            MemberExperienceBizTypeEnum bizType,
            String bizId) {
        String description = StrUtil.format(bizType.getDescription(), experience);
        MemberExperienceRecordDO record =
                MemberExperienceRecordConvert.INSTANCE.convert(
                        userId,
                        experience,
                        totalExperience,
                        bizId,
                        bizType.getType(),
                        bizType.getTitle(),
                        description);
        experienceLogMapper.insert(record);
    }
}
