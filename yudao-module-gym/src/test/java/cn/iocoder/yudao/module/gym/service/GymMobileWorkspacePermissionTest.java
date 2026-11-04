package cn.iocoder.yudao.module.gym.service;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDashboardVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileWorkFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileDashboardMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.file.GymWorkFileBindings;
import cn.iocoder.yudao.module.gym.service.mobile.*;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import jakarta.validation.Validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

class GymMobileWorkspacePermissionTest {
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final GymMobileWorkAccess access = mock(GymMobileWorkAccess.class);
    private final FileApi storage = mock(FileApi.class);
    private final GymFileMapper files = mock(GymFileMapper.class);
    private final GymWorkFileBindings bindings = mock(GymWorkFileBindings.class);
    private final GymMobileWorkFilesService service = new GymMobileWorkFilesService();

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        when(access.requireAdmin()).thenReturn(20L);
        when(permissions.hasAnyPermissions(eq(20L), any(String.class))).thenReturn(true);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(
                service, "security", new SecurityFrameworkServiceImpl(permissions));
        ReflectionTestUtils.setField(service, "files", files);
        ReflectionTestUtils.setField(service, "bindings", bindings);
        ReflectionTestUtils.setField(service, "storage", storage);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private void login(Set<String> limits) {
        SecurityFrameworkUtils.setLoginUser(
                new LoginUser()
                        .setId(20L)
                        .setUserType(2)
                        .setTenantId(1L)
                        .setPermissionLimits(limits),
                new MockHttpServletRequest());
    }

    @ParameterizedTest
    @CsvSource({
        "body_report,gym:measurements:read",
        "training_plan,gym:measurements:read",
        "contract,gym:contracts:templates"
    })
    void fileBusinessPermissionRequiresBothCeilingAndNativeRbac(
            String category, String permission) {
        // An unbound upload owned by this staff member can be read without a business record.
        var file =
                GymFileDO.builder()
                        .id(7L)
                        .storeId(1L)
                        .category(category)
                        .uploaderId(20L)
                        .name("report.png")
                        .mimeType("image/png")
                        .url("private://review")
                        .build();
        var link = new GymMobileWorkFileDO();
        link.setFileId(7L);
        link.setStoreId(1L);
        link.setPurpose(category);
        if (!"contract".equals(category)) link.setMemberId(123L);
        when(files.selectById(7L)).thenReturn(file);
        when(bindings.find(7L)).thenReturn(link);
        var scope = mock(GymDataScope.class);
        ReflectionTestUtils.setField(service, "scope", scope);
        if ("body_report".equals(category)) {
            ReflectionTestUtils.setField(
                    service,
                    "measurements",
                    mock(cn.iocoder.yudao.module.gym.dal.mysql.member.GymMeasurementMapper.class));
        } else if ("training_plan".equals(category)) {
            ReflectionTestUtils.setField(
                    service,
                    "plans",
                    mock(cn.iocoder.yudao.module.gym.dal.mysql.member.GymTrainingPlanMapper.class));
        }
        byte[] png =
                Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jV1sAAAAASUVORK5CYII=");
        when(storage.getFileContentByUrl(file.getUrl())).thenReturn(png);

        login(Set.of("gym:files:read"));
        assertServiceException(() -> service.content(7L), FORBIDDEN);
        verifyNoInteractions(storage);

        login(Set.of("gym:files:read", permission));
        when(permissions.hasAnyPermissions(20L, permission)).thenReturn(false);
        assertServiceException(() -> service.content(7L), FORBIDDEN);
        verifyNoInteractions(storage);

        when(permissions.hasAnyPermissions(20L, permission)).thenReturn(true);
        assertArrayEquals(png, service.content(7L).getContent());
        login(null);
        assertArrayEquals(png, service.content(7L).getContent());
    }

    @Test
    void dashboardOmitsAllUnauthorizedMetricsDespiteNativeWildcard() {
        var dashboard = new GymMobileDashboardService();
        var scope = mock(GymDataScope.class);
        when(scope.readableStoreIds()).thenReturn(null);
        var clock = mock(GymBusinessClock.class);
        var stores = mock(GymStoreMapper.class);
        var aggregates = mock(GymMobileDashboardMapper.class);
        when(clock.now()).thenReturn(Instant.parse("2026-11-04T04:00:00Z"));
        when(stores.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(dashboard, "access", access);
        ReflectionTestUtils.setField(dashboard, "scope", scope);
        ReflectionTestUtils.setField(dashboard, "permissions", permissions);
        ReflectionTestUtils.setField(
                dashboard, "security", new SecurityFrameworkServiceImpl(permissions));
        ReflectionTestUtils.setField(dashboard, "clock", clock);
        ReflectionTestUtils.setField(dashboard, "stores", stores);
        ReflectionTestUtils.setField(dashboard, "mapper", aggregates);
        login(Set.of());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ReflectionTestUtils.setField(dashboard, "validator", factory.getValidator());
            var result = dashboard.dashboard(new GymMobileDashboardVO.Query());
            assertNull(result.performance());
            assertNull(result.checkins());
            assertNull(result.lessons());
            assertNull(result.members());
            assertNull(result.pendingApprovalCount());
            verifyNoInteractions(aggregates);
        }
    }
}
