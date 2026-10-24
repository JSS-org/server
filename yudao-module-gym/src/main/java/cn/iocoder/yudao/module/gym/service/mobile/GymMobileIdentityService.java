package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileIdentityVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Objects;
import java.util.UUID;

@Service
public class GymMobileIdentityService {
    @Resource private MemberUserApi users;
    @Resource private GymMobileIdentityMapper identities;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymStoreMapper stores;
    @Resource private GymMobileFileOwnerMapper fileOwners;
    @Resource private GymFileMapper files;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private Validator validator;

    public MemberUserRespDTO requireUser() {
        var login = SecurityFrameworkUtils.getLoginUser();
        if (login == null
                || !UserTypeEnum.MEMBER.getValue().equals(login.getUserType())
                || !Objects.equals(
                        login.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(MOBILE_IDENTITY_INVALID);
        }
        var user = users.getUser(login.getId());
        if (user == null
                || !Integer.valueOf(0).equals(user.getStatus())
                || user.getMobile() == null
                || !user.getMobile().matches("1[3-9][0-9]{9}")) {
            throw exception(MOBILE_IDENTITY_INVALID);
        }
        return user;
    }

    public GymMobileIdentityDO requireIdentity() {
        var user = requireUser();
        var identity = identities.selectOne(GymMobileIdentityDO::getUserId, user.getId());
        if (identity == null) throw exception(MOBILE_IDENTITY_REQUIRED);
        var member = members.selectById(identity.getMemberId());
        if (member == null || !"active".equals(member.getStatus()))
            throw exception(MOBILE_IDENTITY_INVALID);
        return identity;
    }

    public Long requireMemberId() {
        return requireIdentity().getMemberId();
    }

    public Context context() {
        var user = requireUser();
        var identity = identities.selectOne(GymMobileIdentityDO::getUserId, user.getId());
        if (identity == null) return new Context(null, null, false);
        requireIdentity();
        return new Context(
                identity.getMemberId().toString(), identity.getDefaultStoreId().toString(), true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Initialization initialize(Initialize req, String key) {
        validate(req);
        var user = requireUser();
        return idempotency.execute(
                "profile-initialize",
                key,
                req,
                Initialization.class,
                () -> {
                    // Serializes initial claims across different idempotency keys and different
                    // accounts.
                    // Reuses the tenant mutex also held when extending all-store card memberships.
                    mutex.lockStoreEntitlements();
                    var existing =
                            identities.selectOne(GymMobileIdentityDO::getUserId, user.getId());
                    if (existing != null) {
                        var member = members.selectById(requireMemberId());
                        return new Initialization(member.getId().toString(), stage(member), false);
                    }
                    var store = stores.selectById(req.getStoreId());
                    if (store == null || !"active".equals(store.getStatus()))
                        throw exception(MEMBER_STORE_INACTIVE);
                    var member = members.selectOne(GymMemberDO::getPhone, user.getMobile());
                    boolean created = member == null;
                    if (created) {
                        member = new GymMemberDO();
                        member.setStoreId(store.getId());
                        member.setMemberNo("M-" + UUID.randomUUID().toString().replace("-", ""));
                        member.setName(
                                user.getNickname() == null
                                        ? "会员" + user.getMobile().substring(7)
                                        : user.getNickname());
                        member.setNickname(member.getName());
                        member.setPhone(user.getMobile());
                        member.setGender("unspecified");
                        member.setStatus("active");
                        member.setCustomerStage("lead");
                        member.setRegisteredAt(clock.now());
                        members.insert(member);
                        var relation = new GymMemberStoreDO();
                        relation.setMemberId(member.getId());
                        relation.setStoreId(store.getId());
                        relation.setJoinedAt(clock.now());
                        memberships.insert(relation);
                    }
                    if (!"active".equals(member.getStatus()))
                        throw exception(MOBILE_IDENTITY_INVALID);
                    var identity = new GymMobileIdentityDO();
                    identity.setUserId(user.getId());
                    identity.setMemberId(member.getId());
                    identity.setDefaultStoreId(store.getId());
                    identity.setSource(req.getSource());
                    identity.setInviteCode(req.getInviteCode());
                    identity.setFaceStatus("unregistered");
                    try {
                        identities.insert(identity);
                    } catch (DuplicateKeyException e) {
                        throw exception(MOBILE_IDENTITY_CONFLICT);
                    }
                    return new Initialization(member.getId().toString(), stage(member), created);
                });
    }

    private String stage(GymMemberDO member) {
        return "lead".equals(member.getCustomerStage()) ? "prospect" : member.getCustomerStage();
    }

    public Profile profile() {
        var identity = requireIdentity();
        var member = members.selectById(identity.getMemberId());
        String phone = member.getPhone();
        String masked =
                phone != null && phone.length() == 11
                        ? phone.substring(0, 3) + "****" + phone.substring(7)
                        : "";
        return new Profile(
                member.getId().toString(),
                member.getNickname(),
                identity.getAvatarFileId() == null ? null : identity.getAvatarFileId().toString(),
                masked,
                identity.getFaceStatus());
    }

    @Transactional(rollbackFor = Exception.class)
    public Profile updateProfile(ProfilePatch req) {
        validate(req);
        var identity = requireIdentity();
        // Same member lock as management-side profile edits; only explicitly writable columns
        // change.
        var member = members.selectForUpdate(identity.getMemberId());
        if (req.getNickname() != null) {
            if (req.getNickname().isBlank()) throw exception(MOBILE_REQUEST_INVALID);
            members.update(
                    null,
                    new LambdaUpdateWrapper<GymMemberDO>()
                            .eq(GymMemberDO::getId, member.getId())
                            .set(GymMemberDO::getNickname, req.getNickname()));
        }
        if (req.isAvatarPresent()) {
            if (req.getAvatarFileId() != null) {
                var owner =
                        fileOwners.selectOne(
                                new LambdaQueryWrapper<GymMobileFileOwnerDO>()
                                        .eq(GymMobileFileOwnerDO::getFileId, req.getAvatarFileId())
                                        .eq(GymMobileFileOwnerDO::getMemberId, member.getId())
                                        .eq(GymMobileFileOwnerDO::getPurpose, "avatar"));
                var file = files.selectById(req.getAvatarFileId());
                if (owner == null
                        || file == null
                        || !("image/jpeg".equals(file.getMimeType())
                                || "image/png".equals(file.getMimeType())))
                    throw exception(MOBILE_FILE_NOT_OWNED);
            }
            identities.update(
                    null,
                    new LambdaUpdateWrapper<GymMobileIdentityDO>()
                            .eq(GymMobileIdentityDO::getId, identity.getId())
                            .set(GymMobileIdentityDO::getAvatarFileId, req.getAvatarFileId()));
        }
        return profile();
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
}
