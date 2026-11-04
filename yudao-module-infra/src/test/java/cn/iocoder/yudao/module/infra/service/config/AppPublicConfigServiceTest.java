package cn.iocoder.yudao.module.infra.service.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;

class AppPublicConfigServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private AppPublicConfigService service;
    @Mock private ConfigService configs;
    private final Map<String, ConfigDO> values = new HashMap<>();

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        when(configs.getConfigByKey(anyString()))
                .thenAnswer(call -> values.get(call.getArgument(0)));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private void put(String key, String value, boolean visible) {
        ConfigDO config = new ConfigDO();
        config.setValue(value);
        config.setVisible(visible);
        values.put("jss.app.public.1." + key, config);
    }

    @Test
    void missingConfigurationHasNoDemoContactsImagesOrPolicies() {
        var result = service.getPublicConfig();
        assertEquals("JSS", result.getAppName());
        assertEquals("", result.getSupportPhone());
        assertEquals("", result.getAgreement());
        assertTrue(result.getBanners().isEmpty());
    }

    @Test
    void hiddenValuesAreNeverExposedAndOnlyFixedKeysAreQueried() {
        put("name", "Invisible Name", false);
        put("support-phone", "13800000000", false);
        var result = service.getPublicConfig();
        assertEquals("JSS", result.getAppName());
        assertEquals("", result.getSupportPhone());
        verify(configs, atLeastOnce()).getConfigByKey(anyString());
        assertTrue(
                mockingDetails(configs).getInvocations().stream()
                        .allMatch(
                                call ->
                                        call.getMethod().getName().equals("getConfigByKey")
                                                && ((String) call.getArgument(0))
                                                        .startsWith("jss.app.public.1.")));
    }

    @Test
    void readsVisibleMetadataAndRejectsActiveOrCredentialBearingUrls() {
        put("name", "配置中的应用", true);
        put("support-phone", "+86 400-123-4567", true);
        put("logo", "https://user:secret@example.invalid/image.png", true);
        put("support-qr", "javascript:alert(1)", true);
        put("banner.1.image", "/static/banner.png", true);
        put("banner.1.link", "/pages/goods/index?id=9007199254740993", true);
        put("agreement", "source-template", true);
        put("privacy", "https://example.invalid/privacy", true);
        var result = service.getPublicConfig();
        assertEquals("配置中的应用", result.getAppName());
        assertEquals("+86 400-123-4567", result.getSupportPhone());
        assertEquals("", result.getLogoUrl());
        assertEquals("", result.getSupportQrCode());
        assertEquals(
                "/pages/goods/index?id=9007199254740993", result.getBanners().get(0).getLink());
        assertEquals("source-template", result.getAgreement());
        assertEquals("https://example.invalid/privacy", result.getPrivacy());
    }

    @Test
    void tenantSwitchCannotReadThePreviousTenantsPublicKeys() {
        put("name", "Tenant One", true);
        TenantContextHolder.setTenantId(2L);
        assertEquals("JSS", service.getPublicConfig().getAppName());
        verify(configs, never()).getConfigByKey(startsWith("jss.app.public.1."));
    }
}
