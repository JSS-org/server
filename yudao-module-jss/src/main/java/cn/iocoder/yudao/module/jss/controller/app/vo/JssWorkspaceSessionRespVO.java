package cn.iocoder.yudao.module.jss.controller.app.vo;

/** Epoch milliseconds; deliberately no refresh token or arbitrary staff identity parameter. */
public record JssWorkspaceSessionRespVO(String accessToken, long expiresTime) {}
