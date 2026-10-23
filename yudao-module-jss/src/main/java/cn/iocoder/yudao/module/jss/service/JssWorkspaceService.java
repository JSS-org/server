package cn.iocoder.yudao.module.jss.service;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenExtension;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.jss.controller.app.vo.JssAuthContextRespVO;
import cn.iocoder.yudao.module.jss.controller.app.vo.JssWorkspaceSessionRespVO;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssUserIdentityDO;
import cn.iocoder.yudao.module.jss.dal.mysql.JssUserIdentityMapper;
import cn.iocoder.yudao.module.jss.dal.redis.JssWorkspaceSessionDAO;
import cn.iocoder.yudao.module.jss.framework.JssProperties;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JssWorkspaceService implements OAuth2TokenExtension {
    public static final String ACCESS_PERMISSION = "jss:workspace:access";
    public static final String TOKEN_PREFIX = "jssw_";
    private static final long MAX_LIFETIME_MILLIS = 300_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private OAuth2TokenCommonApi tokens;
    @Resource private JssUserIdentityMapper identities;
    @Resource private JssWorkspaceSessionDAO sessions;
    @Resource private MemberUserApi members;
    @Resource private AdminUserApi admins;
    @Resource private PermissionApi permissions;
    @Resource private TenantFrameworkService tenants;
    @Resource private JssProperties properties;

    public JssAuthContextRespVO context(String memberToken) {
        var member = validateMember(memberToken);
        var identity = allowedIdentity(member.getUserId());
        return new JssAuthContextRespVO(
                String.valueOf(member.getTenantId()),
                String.valueOf(member.getUserId()),
                new JssAuthContextRespVO.Capabilities(identity != null),
                identity == null
                        ? "unavailable"
                        : identity.getId() + ":" + identity.getBindingVersion());
    }

    public JssWorkspaceSessionRespVO createSession(String memberToken) {
        var member = validateMember(memberToken);
        var identity = allowedIdentity(member.getUserId());
        if (identity == null) throw exception0(FORBIDDEN.getCode(), "没有工作台权限");
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token =
                TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        JssWorkspaceSession session = new JssWorkspaceSession();
        session.setTenantId(member.getTenantId());
        session.setMemberUserId(member.getUserId());
        session.setAdminUserId(identity.getAdminUserId());
        session.setIdentityId(identity.getId());
        session.setBindingVersion(identity.getBindingVersion());
        session.setMemberAccessToken(memberToken);
        long memberExpiry =
                member.getExpiresTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        session.setExpiresAt(
                Math.min(System.currentTimeMillis() + MAX_LIFETIME_MILLIS, memberExpiry));
        sessions.save(token, session);
        return new JssWorkspaceSessionRespVO(token, session.getExpiresAt());
    }

    @Override
    public boolean supports(String token) {
        return token != null && token.startsWith(TOKEN_PREFIX);
    }

    @Override
    public OAuth2AccessTokenCheckRespDTO checkAccessToken(String token) {
        var session = sessions.get(token);
        if (session == null || session.getExpiresAt() <= System.currentTimeMillis()) {
            throw exception0(UNAUTHORIZED.getCode(), "工作台会话已失效");
        }
        // Tenant header, parent token, identity version and current RBAC are checked on EVERY use.
        Long requestTenant = TenantContextHolder.getTenantId();
        if (requestTenant != null && !requestTenant.equals(session.getTenantId())) {
            throw exception0(FORBIDDEN.getCode(), "您无权访问该租户的数据");
        }
        final OAuth2AccessTokenCheckRespDTO[] result = new OAuth2AccessTokenCheckRespDTO[1];
        try {
            TenantUtils.execute(
                    session.getTenantId(),
                    () -> {
                        var member = validateMember(session.getMemberAccessToken());
                        var identity = allowedIdentity(member.getUserId());
                        if (!sessions.isCurrent(session)
                                || !Objects.equals(member.getUserId(), session.getMemberUserId())
                                || identity == null
                                || !Objects.equals(identity.getId(), session.getIdentityId())
                                || !Objects.equals(
                                        identity.getBindingVersion(), session.getBindingVersion())
                                || !Objects.equals(
                                        identity.getAdminUserId(), session.getAdminUserId())) {
                            throw exception0(UNAUTHORIZED.getCode(), "工作台授权已失效");
                        }
                        var dto = new OAuth2AccessTokenCheckRespDTO();
                        dto.setUserId(session.getAdminUserId());
                        dto.setUserType(UserTypeEnum.ADMIN.getValue());
                        dto.setTenantId(session.getTenantId());
                        var admin = admins.getUser(session.getAdminUserId());
                        if (admin == null || !CommonStatusEnum.isEnable(admin.getStatus())) {
                            throw exception0(UNAUTHORIZED.getCode(), "员工账号已失效");
                        }
                        Map<String, String> info = new HashMap<>();
                        if (admin.getNickname() != null) info.put("nickname", admin.getNickname());
                        if (admin.getDeptId() != null)
                            info.put("deptId", String.valueOf(admin.getDeptId()));
                        dto.setUserInfo(info);
                        dto.setScopes(List.of());
                        dto.setExpiresTime(
                                LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(session.getExpiresAt()),
                                        ZoneId.systemDefault()));
                        result[0] = dto;
                    });
        } catch (ServiceException denied) {
            sessions.delete(session);
            throw denied;
        }
        return result[0];
    }

    @Override
    public OAuth2AccessTokenRespDTO removeAccessToken(String token) {
        var session = sessions.get(token);
        if (session == null) return null;
        sessions.delete(session);
        var dto = new OAuth2AccessTokenRespDTO();
        dto.setUserId(session.getAdminUserId());
        dto.setUserType(UserTypeEnum.ADMIN.getValue());
        return dto;
    }

    public void logout(String memberToken) {
        if (memberToken == null || memberToken.isBlank()) return;
        // Removing the parent invalidates all children, including those minted concurrently.
        // This also covers the original /member/auth/logout endpoint, which uses the same API.
        if (!supports(memberToken)) tokens.removeAccessToken(memberToken);
    }

    private OAuth2AccessTokenCheckRespDTO validateMember(String token) {
        if (token == null || token.isBlank() || supports(token)) {
            throw exception0(UNAUTHORIZED.getCode(), "请先登录会员账号");
        }
        var member = tokens.checkAccessToken(token);
        if (member == null
                || !UserTypeEnum.MEMBER.getValue().equals(member.getUserType())
                || member.getTenantId() == null
                || !Objects.equals(TenantContextHolder.getTenantId(), member.getTenantId())) {
            throw exception0(UNAUTHORIZED.getCode(), "会员会话或租户不匹配");
        }
        tenants.validTenant(member.getTenantId());
        var user = members.getUser(member.getUserId());
        if (user == null || !CommonStatusEnum.isEnable(user.getStatus())) {
            throw exception0(UNAUTHORIZED.getCode(), "会员账号已停用");
        }
        return member;
    }

    private JssUserIdentityDO allowedIdentity(Long memberId) {
        if (!properties.isEnabled()) return null;
        var identity = identities.findByMember(memberId);
        if (identity == null || !CommonStatusEnum.isEnable(identity.getStatus())) return null;
        var admin = admins.getUser(identity.getAdminUserId());
        if (admin == null
                || !CommonStatusEnum.isEnable(admin.getStatus())
                || !permissions.hasAnyPermissions(admin.getId(), ACCESS_PERMISSION)) return null;
        return identity;
    }
}
