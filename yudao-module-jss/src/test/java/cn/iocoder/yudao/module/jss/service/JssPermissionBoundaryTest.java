package cn.iocoder.yudao.module.jss.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

@ExtendWith(MockitoExtension.class)
class JssPermissionBoundaryTest {
    @Mock private PermissionCommonApi permissions;
    private SecurityFrameworkServiceImpl service;
    private LoginUser user;

    @BeforeEach
    void login() {
        user = new LoginUser().setId(20L).setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        service = new SecurityFrameworkServiceImpl(permissions);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nativeTokenContinuesToUseItsExistingRbacWithoutACeiling() {
        when(permissions.hasAnyPermissions(20L, "native:action")).thenReturn(true);
        assertTrue(service.hasPermission("native:action"));
        verify(permissions).hasAnyPermissions(20L, "native:action");
    }

    @Test
    void nativeCrossTenantPolicyRemainsUnchanged() {
        user.setVisitTenantId(2L);
        assertTrue(service.hasPermission("native:action"));
        verifyNoInteractions(permissions);
    }

    @Test
    void emptyCeilingDeniesEvenAPrivilegedUnderlyingAccount() {
        user.setPermissionLimits(Set.of());
        assertFalse(service.hasPermission("native:action"));
        verifyNoInteractions(permissions);
    }

    @Test
    void boundedCandidateStillRequiresTheOriginalRbacGrant() {
        user.setPermissionLimits(Set.of("allowed"));
        when(permissions.hasAnyPermissions(20L, "allowed")).thenReturn(true);
        assertTrue(service.hasPermission("allowed"));
        assertFalse(service.hasPermission("unbounded"));
        verify(permissions).hasAnyPermissions(20L, "allowed");
        verifyNoMoreInteractions(permissions);
    }

    @Test
    void orChecksCannotCombineDifferentPermissionsAcrossTheCeilingAndRbac() {
        user.setPermissionLimits(Set.of("bounded-but-not-granted"));
        assertFalse(
                service.hasAnyPermissions("bounded-but-not-granted", "granted-but-not-bounded"));
        verify(permissions).hasAnyPermissions(20L, "bounded-but-not-granted");
        verifyNoMoreInteractions(permissions);
    }

    @Test
    void crossTenantSkipCannotBypassAnApplicationCeiling() {
        user.setVisitTenantId(2L).setPermissionLimits(Set.of("allowed"));
        assertFalse(service.hasPermission("allowed"));
        verify(permissions).hasAnyPermissions(20L, "allowed");
    }
}
