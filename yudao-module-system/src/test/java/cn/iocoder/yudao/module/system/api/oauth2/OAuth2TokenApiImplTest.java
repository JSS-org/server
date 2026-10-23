package cn.iocoder.yudao.module.system.api.oauth2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenExtension;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class OAuth2TokenApiImplTest {
    @InjectMocks private OAuth2TokenApiImpl api;
    @Mock private OAuth2TokenService oauth2TokenService;
    @Mock private ObjectProvider<OAuth2TokenExtension> tokenExtensions;
    @Mock private OAuth2TokenExtension extension;

    @BeforeEach
    void setup() {
        when(tokenExtensions.orderedStream()).thenAnswer(call -> Stream.of(extension));
    }

    @Test
    void ordinaryAdminTokensRetainExistingValidation() {
        var token = new OAuth2AccessTokenDO();
        token.setUserId(7L);
        token.setUserType(2);
        token.setTenantId(1L);
        when(oauth2TokenService.checkAccessToken("ordinary")).thenReturn(token);
        var result = api.checkAccessToken("ordinary");
        assertEquals(7L, result.getUserId());
        assertEquals(2, result.getUserType());
        assertEquals(1L, result.getTenantId());
        verify(extension, never()).checkAccessToken(anyString());
    }

    @Test
    void applicationTokensUseTheirRevocationValidator() {
        when(extension.supports("jssw_test")).thenReturn(true);
        var token = new OAuth2AccessTokenCheckRespDTO();
        token.setUserId(20L);
        when(extension.checkAccessToken("jssw_test")).thenReturn(token);
        assertSame(token, api.checkAccessToken("jssw_test"));
        verifyNoInteractions(oauth2TokenService);
    }

    @Test
    void rejectedApplicationTokenNeverFallsBackToOrdinaryOAuth() {
        when(extension.supports("jssw_test")).thenReturn(true);
        when(extension.checkAccessToken("jssw_test"))
                .thenThrow(new ServiceException(401, "revoked"));
        assertThrows(ServiceException.class, () -> api.checkAccessToken("jssw_test"));
        verifyNoInteractions(oauth2TokenService);
    }

    @Test
    void applicationRemovalUsesExtensionWithoutDeletingOrdinarySessions() {
        when(extension.supports("jssw_test")).thenReturn(true);
        var result = new OAuth2AccessTokenRespDTO();
        when(extension.removeAccessToken("jssw_test")).thenReturn(result);
        assertSame(result, api.removeAccessToken("jssw_test"));
        verifyNoInteractions(oauth2TokenService);
    }
}
