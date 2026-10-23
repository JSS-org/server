package cn.iocoder.yudao.framework.common.biz.system.oauth2;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;

/** Extension for application-scoped tokens. Ordinary OAuth tokens retain their existing behavior. */
public interface OAuth2TokenExtension {
    boolean supports(String token);

    OAuth2AccessTokenCheckRespDTO checkAccessToken(String token);

    OAuth2AccessTokenRespDTO removeAccessToken(String token);
}
