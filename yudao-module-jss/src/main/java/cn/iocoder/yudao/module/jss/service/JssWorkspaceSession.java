package cn.iocoder.yudao.module.jss.service;

import lombok.Data;

/** Server-side only. Never serialize this object in an HTTP response or log. */
@Data
public class JssWorkspaceSession {
    private Long tenantId;
    private Long memberUserId;
    private Long adminUserId;
    private Long identityId;
    private Long bindingVersion;
    private String memberAccessToken;
    private String tokenDigest;
    private long expiresAt;
}
