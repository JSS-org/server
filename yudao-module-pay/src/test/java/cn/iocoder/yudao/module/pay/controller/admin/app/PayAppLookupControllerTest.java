package cn.iocoder.yudao.module.pay.controller.admin.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real method-security proxy and workspace permission intersection; RBAC/data adapters mocked. */
class PayAppLookupControllerTest {
    private AnnotationConfigApplicationContext context;
    private PayAppController controller;
    private PayAppService apps;
    private PermissionCommonApi permissions;
    private final Set<String> grants = new HashSet<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(PayAppController.class);
        apps = context.getBean(PayAppService.class);
        permissions = context.getBean(PermissionCommonApi.class);
        assertTrue(AopUtils.isAopProxy(controller));
        when(permissions.hasAnyPermissions(eq(20L), any(String[].class)))
                .thenAnswer(
                        invocation ->
                                grants.contains("*:*:*")
                                        || Arrays.stream((String[]) invocation.getRawArguments()[1])
                                                .anyMatch(grants::contains));
        when(apps.getAppList()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private void login(Set<String> ceiling) {
        LoginUser user =
                new LoginUser()
                        .setId(20L)
                        .setUserType(UserTypeEnum.ADMIN.getValue())
                        .setTenantId(1L)
                        .setPermissionLimits(ceiling);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void eachActualPaymentQueryCanReadLabelsWithinItsOwnWorkspaceCeiling() {
        for (String permission :
                List.of(
                        "pay:app:query",
                        "pay:channel:query",
                        "pay:order:query",
                        "pay:refund:query",
                        "pay:notify:query",
                        "pay:transfer:query")) {
            grants.clear();
            grants.add(permission);
            login(Set.of(permission));
            assertEquals(List.of(), controller.getSimpleAppList().getData());
        }
        verify(apps, times(6)).getAppList();
        verifyNoInteractions(context.getBean(PayChannelService.class));
    }

    @Test
    void projectionContainsOnlyExactLongIdAndNameForEnabledAndDisabledApps() throws Exception {
        grants.add("pay:order:query");
        login(Set.of("pay:order:query"));
        when(apps.getAppList())
                .thenReturn(
                        List.of(
                                new PayAppDO()
                                        .setId(9007199254740993L)
                                        .setName("Current app")
                                        .setStatus(0)
                                        .setAppKey("internal-key")
                                        .setOrderNotifyUrl("https://private.test/order")
                                        .setRemark("internal note"),
                                new PayAppDO()
                                        .setId(42L)
                                        .setName("Historical app")
                                        .setStatus(1)
                                        .setTransferNotifyUrl("https://private.test/transfer")));
        var result = controller.getSimpleAppList().getData();
        assertEquals(2, result.size());
        assertEquals(9007199254740993L, result.get(0).getId());
        assertEquals("Historical app", result.get(1).getName());
        var mapper =
                JsonMapper.builder()
                        .addModule(new YudaoJacksonAutoConfiguration().timestampSupportModuleBean())
                        .build();
        List<?> json = mapper.readValue(mapper.writeValueAsString(result), List.class);
        Map<?, ?> first = (Map<?, ?>) json.get(0);
        assertEquals(Set.of("id", "name"), first.keySet());
        assertEquals("9007199254740993", first.get("id"));
        assertEquals(Set.of("id", "name"), ((Map<?, ?>) json.get(1)).keySet());
    }

    @Test
    void anonymousOrUnrelatedPermissionsNeverReachTheDataService() {
        Exception error = assertThrows(Exception.class, () -> controller.getSimpleAppList());
        assertTrue(
                error instanceof AccessDeniedException
                        || error instanceof AuthenticationCredentialsNotFoundException);
        login(null);
        grants.add("pay:merchant:query");
        assertThrows(AccessDeniedException.class, () -> controller.getSimpleAppList());
        grants.clear();
        grants.add("pay:app:create");
        assertThrows(AccessDeniedException.class, () -> controller.getSimpleAppList());
        verify(apps, never()).getAppList();
    }

    @Test
    void independentNativeAndWorkspacePermissionsCannotSatisfyDifferentBranchesOfTheOr() {
        grants.add("pay:order:query");
        login(Set.of("pay:app:query"));
        assertThrows(AccessDeniedException.class, () -> controller.getSimpleAppList());
        verify(apps, never()).getAppList();
    }

    @Test
    void emptyWorkspaceCeilingDeniesEvenNativeWildcardPermission() {
        grants.add("*:*:*");
        login(Set.of());
        assertThrows(AccessDeniedException.class, () -> controller.getSimpleAppList());
        verify(apps, never()).getAppList();
        verifyNoInteractions(permissions);
    }

    @Test
    void queryMetadataDoesNotGrantFullApplicationDetailOrLegacyListAccess() {
        grants.add("pay:order:query");
        login(Set.of("pay:order:query"));
        assertEquals(List.of(), controller.getSimpleAppList().getData());
        assertThrows(AccessDeniedException.class, () -> controller.getApp(42L));
        assertThrows(AccessDeniedException.class, () -> controller.getAppList());
        verify(apps, never()).getApp(anyLong());
        verify(apps).getAppList();
    }

    @Test
    void nativeAdminWithoutAnExtraCeilingStillRequiresAnActualQueryGrant() {
        login(null);
        assertThrows(AccessDeniedException.class, () -> controller.getSimpleAppList());
        grants.add("pay:refund:query");
        assertEquals(List.of(), controller.getSimpleAppList().getData());
        verify(apps).getAppList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean
        PayAppService appService() {
            return mock(PayAppService.class);
        }

        @Bean
        PayChannelService channelService() {
            return mock(PayChannelService.class);
        }

        @Bean
        PermissionCommonApi permissionApi() {
            return mock(PermissionCommonApi.class);
        }

        @Bean("ss")
        SecurityFrameworkService security(PermissionCommonApi permissions) {
            return new SecurityFrameworkServiceImpl(permissions);
        }

        @Bean
        PayAppController controller(PayAppService apps, PayChannelService channels) {
            var controller = new PayAppController();
            ReflectionTestUtils.setField(controller, "appService", apps);
            ReflectionTestUtils.setField(controller, "channelService", channels);
            return controller;
        }
    }
}
