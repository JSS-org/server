package cn.iocoder.yudao.module.jss.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssUserIdentityDO;
import cn.iocoder.yudao.module.jss.dal.mysql.JssUserIdentityMapper;
import cn.iocoder.yudao.module.jss.dal.redis.JssWorkspaceSessionDAO;
import cn.iocoder.yudao.module.jss.framework.JssProperties;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JssWorkspaceServiceTest {
    @InjectMocks private JssWorkspaceService service;
    @Mock private OAuth2TokenCommonApi tokens;
    @Mock private JssUserIdentityMapper identities;
    @Mock private JssWorkspaceSessionDAO sessions;
    @Mock private MemberUserApi members;
    @Mock private AdminUserApi admins;
    @Mock private PermissionApi permissions;
    @Mock private TenantFrameworkService tenants;
    @Spy private JssProperties properties = new JssProperties();
    private JssUserIdentityDO binding;
    private OAuth2AccessTokenCheckRespDTO memberToken;
    private MemberUserRespDTO member;
    private AdminUserRespDTO admin;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        properties.setEnabled(true);
        memberToken = new OAuth2AccessTokenCheckRespDTO();
        memberToken.setTenantId(1L);
        memberToken.setUserId(10L);
        memberToken.setUserType(UserTypeEnum.MEMBER.getValue());
        memberToken.setExpiresTime(LocalDateTime.now().plusHours(1));
        when(tokens.checkAccessToken("member-token")).thenReturn(memberToken);
        member = new MemberUserRespDTO();
        member.setId(10L);
        member.setStatus(0);
        when(members.getUser(10L)).thenReturn(member);
        admin = new AdminUserRespDTO();
        admin.setId(20L);
        admin.setStatus(0);
        when(admins.getUser(20L)).thenReturn(admin);
        binding = new JssUserIdentityDO();
        binding.setId(100L);
        binding.setTenantId(1L);
        binding.setMemberUserId(10L);
        binding.setAdminUserId(20L);
        binding.setStatus(0);
        binding.setBindingVersion(1L);
        when(identities.findByMember(10L)).thenReturn(binding);
        when(permissions.hasAnyPermissions(20L, JssWorkspaceService.ACCESS_PERMISSION))
                .thenReturn(true);
        when(sessions.isCurrent(any())).thenReturn(true);
        doAnswer(
                        call -> {
                            when(sessions.get(call.getArgument(0))).thenReturn(call.getArgument(1));
                            return null;
                        })
                .when(sessions)
                .save(anyString(), any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private String issue() {
        return service.createSession("member-token").accessToken();
    }

    private void assertUnauthorized(String token) {
        var ex = assertThrows(ServiceException.class, () -> service.checkAccessToken(token));
        assertEquals(401, ex.getCode());
        verify(sessions).delete(any());
    }

    @Test
    void ordinaryMemberAndMembershipLevelDoNotGrantWorkspace() {
        member.setLevelId(999L);
        when(identities.findByMember(10L)).thenReturn(null);
        assertFalse(service.context("member-token").capabilities().workspace());
        var ex = assertThrows(ServiceException.class, this::issue);
        assertEquals(403, ex.getCode());
        verifyNoInteractions(sessions);
    }

    @Test
    void permissionServiceAloneDeterminesEntryRegardlessOfRoleNames() {
        assertTrue(service.context("member-token").capabilities().workspace());
        when(permissions.hasAnyPermissions(20L, JssWorkspaceService.ACCESS_PERMISSION))
                .thenReturn(false);
        assertFalse(service.context("member-token").capabilities().workspace());
    }

    @Test
    void signedTokenUsesBoundStaffAndShortLifetime() {
        var issued = service.createSession("member-token");
        assertTrue(issued.accessToken().startsWith("jssw_"));
        assertTrue(issued.expiresTime() <= System.currentTimeMillis() + 300_000);
        var checked = service.checkAccessToken(issued.accessToken());
        assertEquals(20L, checked.getUserId());
        assertEquals(UserTypeEnum.ADMIN.getValue(), checked.getUserType());
        assertEquals(1L, checked.getTenantId());
        assertFalse(service.supports("ordinary-admin-token"));
    }

    @Test
    void bindingRevocationImmediatelyRejectsPreviouslyIssuedToken() {
        String token = issue();
        binding.setStatus(1);
        assertUnauthorized(token);
    }

    @Test
    void rebindVersionNeverRevivesAnOldToken() {
        String token = issue();
        binding.setBindingVersion(3L);
        assertUnauthorized(token);
    }

    @Test
    void revokedRbacRejectsOldTokenWithoutWaitingForClientRefresh() {
        String token = issue();
        when(permissions.hasAnyPermissions(20L, JssWorkspaceService.ACCESS_PERMISSION))
                .thenReturn(false);
        assertUnauthorized(token);
    }

    @Test
    void disabledStaffRejectsOldToken() {
        String token = issue();
        admin.setStatus(1);
        assertUnauthorized(token);
    }

    @Test
    void disabledMemberRejectsOldToken() {
        String token = issue();
        member.setStatus(1);
        assertUnauthorized(token);
    }

    @Test
    void parentLogoutRejectsOldToken() {
        String token = issue();
        when(tokens.checkAccessToken("member-token"))
                .thenThrow(new ServiceException(401, "expired"));
        assertUnauthorized(token);
    }

    @Test
    void tenantHeaderCannotSelectAnotherTenant() {
        String token = issue();
        TenantContextHolder.setTenantId(2L);
        assertEquals(
                403,
                assertThrows(ServiceException.class, () -> service.checkAccessToken(token))
                        .getCode());
        assertEquals(2L, TenantContextHolder.getTenantId());
        verify(sessions, never()).delete(any());
    }

    @Test
    void disabledTenantRejectsWorkspace() {
        String token = issue();
        doThrow(new ServiceException(403, "tenant disabled")).when(tenants).validTenant(1L);
        assertThrows(ServiceException.class, () -> service.checkAccessToken(token));
        verify(sessions).delete(any());
    }

    @Test
    void replacedSessionRejectsOldResponseToken() {
        String token = issue();
        when(sessions.isCurrent(any())).thenReturn(false);
        assertUnauthorized(token);
    }

    @Test
    void disabledFeatureInvalidatesExistingSession() {
        String token = issue();
        properties.setEnabled(false);
        assertUnauthorized(token);
    }

    @Test
    void workspaceTokenCannotCreateAnotherWorkspaceSession() {
        assertThrows(ServiceException.class, () -> service.createSession(issue()));
    }

    @Test
    void adminTokenCannotBeUsedAsMemberIdentity() {
        memberToken.setUserType(UserTypeEnum.ADMIN.getValue());
        assertThrows(ServiceException.class, this::issue);
    }

    @Test
    void logoutRevokesParentAndIsIdempotent() {
        service.logout("member-token");
        service.logout(null);
        service.logout("");
        verify(tokens).removeAccessToken("member-token");
    }

    @Test
    void forgedTokenAndExpiredRedisEntryAreRejected() {
        assertEquals(
                401,
                assertThrows(ServiceException.class, () -> service.checkAccessToken("jssw_forged"))
                        .getCode());
    }
}
