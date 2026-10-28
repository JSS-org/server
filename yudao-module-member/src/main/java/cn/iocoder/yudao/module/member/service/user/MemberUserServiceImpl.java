package cn.iocoder.yudao.module.member.service.user;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.*;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserUpdateReqVO;
import cn.iocoder.yudao.module.member.controller.app.user.vo.*;
import cn.iocoder.yudao.module.member.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.member.convert.user.MemberUserConvert;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.group.MemberGroupMapper;
import cn.iocoder.yudao.module.member.dal.mysql.tag.MemberTagMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.mq.producer.user.MemberUserProducer;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.annotations.VisibleForTesting;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员 User Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Valid
@Slf4j
public class MemberUserServiceImpl implements MemberUserService {

    @Resource private MemberUserMapper memberUserMapper;
    @Resource private MemberTagMapper memberTagMapper;
    @Resource private MemberGroupMapper memberGroupMapper;

    @Resource private SmsCodeApi smsCodeApi;
    @Resource private SocialClientApi socialClientApi;
    @Resource private OAuth2TokenCommonApi oauth2TokenApi;

    @Resource private PasswordEncoder passwordEncoder;

    @Resource private MemberUserProducer memberUserProducer;

    @Override
    public MemberUserDO getUserByMobile(String mobile) {
        return memberUserMapper.selectByMobile(mobile);
    }

    @Override
    public List<MemberUserDO> getUserListByNickname(String nickname) {
        return memberUserMapper.selectListByNicknameLike(nickname);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO createUserIfAbsent(String mobile, String registerIp, Integer terminal) {
        // 用户已经存在
        MemberUserDO user = memberUserMapper.selectByMobile(mobile);
        if (user != null) {
            return user;
        }
        // 用户不存在，则进行创建
        return createUser(mobile, null, null, registerIp, terminal, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO createUser(
            String nickname, String avtar, String registerIp, Integer terminal) {
        return createUser(null, nickname, avtar, registerIp, terminal, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO registerUser(
            String mobile, String password, String nickname, String registerIp, Integer terminal) {
        validateMobileUnique(null, mobile);
        try {
            return createUser(mobile, nickname, null, registerIp, terminal, password);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // The existing unique active-mobile key resolves concurrent registration attempts.
            throw exception(USER_MOBILE_USED, mobile);
        }
    }

    private MemberUserDO createUser(
            String mobile, String nickname, String avtar, String registerIp, Integer terminal,
            String suppliedPassword) {
        String password = suppliedPassword == null ? IdUtil.fastSimpleUUID() : suppliedPassword;
        // 插入用户
        MemberUserDO user = new MemberUserDO();
        user.setMobile(mobile);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus()); // 默认开启
        user.setPassword(encodePassword(password)); // 加密密码
        user.setRegisterIp(registerIp).setRegisterTerminal(terminal);
        user.setNickname(nickname).setAvatar(avtar); // 基础信息
        if (StrUtil.isEmpty(nickname)) {
            // 昵称为空时，随机一个名字，避免一些依赖 nickname 的逻辑报错，或者有点丑。例如说，短信发送有昵称时~
            user.setNickname("用户" + RandomUtil.randomNumbers(6));
        }
        memberUserMapper.insert(user);

        // 发送 MQ 消息：用户创建
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        memberUserProducer.sendUserCreateMessage(user.getId());
                    }
                });
        return user;
    }

    @Override
    public void updateUserLogin(Long id, String loginIp) {
        memberUserMapper.updateById(
                new MemberUserDO().setId(id).setLoginIp(loginIp).setLoginDate(LocalDateTime.now()));
    }

    @Override
    public MemberUserDO getUser(Long id) {
        return memberUserMapper.selectById(id);
    }

    @Override
    public MemberUserDO getUserForUpdate(Long id) {
        return memberUserMapper.selectOneForUpdate(MemberUserDO::getId, id);
    }

    @Override
    public List<MemberUserDO> getUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return memberUserMapper.selectByIds(ids);
    }

    @Override
    public void updateUser(Long userId, AppMemberUserUpdateReqVO reqVO) {
        // 1.1 检测用户是否存在
        validateUserExists(userId);
        // 1.2 校验手机是否已经被绑定
        validateEmailUnique(userId, reqVO.getEmail());

        // 2. 更新用户
        MemberUserDO updateObj = BeanUtils.toBean(reqVO, MemberUserDO.class).setId(userId);
        memberUserMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserMobile(Long userId, AppMemberUserUpdateMobileReqVO reqVO) {
        // 1.1 检测用户是否存在
        MemberUserDO user = validateUserExists(userId);
        // 1.2 校验新手机是否已经被绑定
        validateMobileUnique(null, reqVO.getMobile());

        // 2.1 校验旧手机和旧验证码
        // 补充说明：从安全性来说，老手机也校验 oldCode 验证码会更安全。但是由于 uni-app 商城界面暂时没做，所以这里不强制校验
        if (StrUtil.isNotEmpty(reqVO.getOldCode())) {
            smsCodeApi.useSmsCode(
                    new SmsCodeUseReqDTO()
                            .setMobile(user.getMobile())
                            .setCode(reqVO.getOldCode())
                            .setScene(SmsSceneEnum.MEMBER_UPDATE_MOBILE.getScene())
                            .setUsedIp(getClientIP()));
        }
        // 2.2 使用新验证码
        smsCodeApi.useSmsCode(
                new SmsCodeUseReqDTO()
                        .setMobile(reqVO.getMobile())
                        .setCode(reqVO.getCode())
                        .setScene(SmsSceneEnum.MEMBER_UPDATE_MOBILE.getScene())
                        .setUsedIp(getClientIP()));

        // 3. 更新用户手机
        memberUserMapper.updateById(
                MemberUserDO.builder().id(userId).mobile(reqVO.getMobile()).build());
    }

    @Override
    public void updateUserMobileByWeixin(
            Long userId, AppMemberUserUpdateMobileByWeixinReqVO reqVO) {
        // 1.1 获得对应的手机号信息
        SocialWxPhoneNumberInfoRespDTO phoneNumberInfo =
                socialClientApi.getWxMaPhoneNumberInfo(
                        UserTypeEnum.MEMBER.getValue(), reqVO.getCode());
        Assert.notNull(phoneNumberInfo, "获得手机信息失败，结果为空");
        // 1.2 校验新手机是否已经被绑定
        validateMobileUnique(userId, phoneNumberInfo.getPhoneNumber());

        // 2. 更新用户手机
        memberUserMapper.updateById(
                MemberUserDO.builder().id(userId).mobile(phoneNumberInfo.getPhoneNumber()).build());
    }

    @Override
    public void updateUserPassword(Long userId, AppMemberUserUpdatePasswordReqVO reqVO) {
        // 检测用户是否存在
        MemberUserDO user = validateUserExists(userId);
        // 校验验证码
        smsCodeApi.useSmsCode(
                new SmsCodeUseReqDTO()
                        .setMobile(user.getMobile())
                        .setCode(reqVO.getCode())
                        .setScene(SmsSceneEnum.MEMBER_UPDATE_PASSWORD.getScene())
                        .setUsedIp(getClientIP()));

        // 更新用户密码
        memberUserMapper.updateById(
                MemberUserDO.builder()
                        .id(userId)
                        .password(passwordEncoder.encode(reqVO.getPassword()))
                        .build());
    }

    @Override
    public void resetUserPassword(AppMemberUserResetPasswordReqVO reqVO) {
        // 检验用户是否存在
        MemberUserDO user = validateUserExists(reqVO.getMobile());

        // 使用验证码
        smsCodeApi.useSmsCode(
                AuthConvert.INSTANCE.convert(
                        reqVO, SmsSceneEnum.MEMBER_RESET_PASSWORD, getClientIP()));

        // 更新密码
        memberUserMapper.updateById(
                MemberUserDO.builder()
                        .id(user.getId())
                        .password(passwordEncoder.encode(reqVO.getPassword()))
                        .build());
    }

    private MemberUserDO validateUserExists(String mobile) {
        MemberUserDO user = memberUserMapper.selectByMobile(mobile);
        if (user == null) {
            throw exception(USER_MOBILE_NOT_EXISTS);
        }
        return user;
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 对密码进行加密
     *
     * @param password 密码
     * @return 加密后的密码
     */
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(MemberUserUpdateReqVO updateReqVO) {
        // 校验存在
        MemberUserDO user =
                updateReqVO.getId() == null ? null : getUserForUpdate(updateReqVO.getId());
        if (user == null) throw exception(USER_NOT_EXISTS);
        // 校验手机唯一
        validateMobileUnique(updateReqVO.getId(), updateReqVO.getMobile());
        // 校验邮箱唯一
        validateEmailUnique(updateReqVO.getId(), updateReqVO.getEmail());

        // 更新
        MemberUserDO updateObj = MemberUserConvert.INSTANCE.convert(updateReqVO);
        validateProfileAssociations(user, updateReqVO, updateObj);
        if (updateReqVO.isBirthdayPresent()
                || updateReqVO.isAreaIdPresent()
                || updateReqVO.isGroupIdPresent()) {
            var update =
                    new LambdaUpdateWrapper<MemberUserDO>().eq(MemberUserDO::getId, user.getId());
            if (updateReqVO.isBirthdayPresent()) {
                update.set(MemberUserDO::getBirthday, updateReqVO.getBirthday());
                updateObj.setBirthday(null);
            }
            if (updateReqVO.isAreaIdPresent()) {
                update.set(MemberUserDO::getAreaId, updateObj.getAreaId());
                updateObj.setAreaId(null);
            }
            if (updateReqVO.isGroupIdPresent()) {
                update.set(MemberUserDO::getGroupId, updateObj.getGroupId());
                updateObj.setGroupId(null);
            }
            memberUserMapper.update(updateObj, update);
        } else memberUserMapper.updateById(updateObj);

        // 如果是禁用用户，则删除其 Token 信息
        if (CommonStatusEnum.isDisable(updateObj.getStatus())) {
            oauth2TokenApi.removeAccessToken(updateObj.getId(), UserTypeEnum.MEMBER.getValue());
        }
    }

    private void validateProfileAssociations(
            MemberUserDO user, MemberUserUpdateReqVO request, MemberUserDO update) {
        if (request.isAreaIdPresent()) {
            Long area = request.getAreaId();
            if (area != null && (area < 0 || area > Integer.MAX_VALUE))
                throw exception(USER_AREA_INVALID);
            update.setAreaId(area == null || area == 0 ? null : area.intValue());
        }
        if (request.isGroupIdPresent()) {
            Long id = request.getGroupId();
            if (id == null || id == 0L) update.setGroupId(null);
            else if (!id.equals(user.getGroupId())) {
                var group = memberGroupMapper.selectById(id);
                if (group == null || !CommonStatusEnum.isEnable(group.getStatus()))
                    throw exception(USER_GROUP_INVALID);
            }
        }
        if (request.isTagIdsPresent()) {
            Set<Long> ids =
                    request.getTagIds() == null
                            ? new LinkedHashSet<>()
                            : new LinkedHashSet<>(request.getTagIds());
            if (ids.stream().anyMatch(id -> id == null || id <= 0)
                    || ids.stream().map(String::valueOf).collect(Collectors.joining(",")).length()
                            > 255) throw exception(USER_TAG_INVALID);
            Set<Long> original =
                    new LinkedHashSet<>(user.getTagIds() == null ? List.of() : user.getTagIds());
            List<Long> added = ids.stream().filter(id -> !original.contains(id)).toList();
            if (!added.isEmpty() && memberTagMapper.selectByIds(added).size() != added.size())
                throw exception(USER_TAG_INVALID);
            update.setTagIds(new ArrayList<>(ids));
        }
    }

    @VisibleForTesting
    MemberUserDO validateUserExists(Long id) {
        if (id == null) {
            return null;
        }
        MemberUserDO user = memberUserMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @VisibleForTesting
    void validateMobileUnique(Long id, String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return;
        }
        MemberUserDO user = memberUserMapper.selectByMobile(mobile);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_MOBILE_USED, mobile);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_MOBILE_USED, mobile);
        }
    }

    @VisibleForTesting
    void validateEmailUnique(Long id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        MemberUserDO user = memberUserMapper.selectByEmail(email);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_EMAIL_USED, email);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_EMAIL_USED, email);
        }
    }

    @Override
    public PageResult<MemberUserDO> getUserPage(MemberUserPageReqVO pageReqVO) {
        return memberUserMapper.selectPage(pageReqVO);
    }

    @Override
    public void updateUserLevel(Long id, Long levelId, Integer experience) {
        // 0 代表无等级：防止UpdateById时，会被过滤掉的问题
        levelId = ObjectUtil.defaultIfNull(levelId, 0L);
        memberUserMapper.updateById(
                new MemberUserDO().setId(id).setLevelId(levelId).setExperience(experience));
    }

    @Override
    public Long getUserCountByGroupId(Long groupId) {
        return memberUserMapper.selectCountByGroupId(groupId);
    }

    @Override
    public Long getUserCountByLevelId(Long levelId) {
        return memberUserMapper.selectCountByLevelId(levelId);
    }

    @Override
    public Long getUserCountByTagId(Long tagId) {
        return memberUserMapper.selectCountByTagId(tagId);
    }

    @Override
    public boolean updateUserPoint(Long id, Integer point) {
        if (point > 0) {
            memberUserMapper.updatePointIncr(id, point);
        } else if (point < 0) {
            return memberUserMapper.updatePointDecr(id, point) > 0;
        }
        return true;
    }
}
