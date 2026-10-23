package cn.iocoder.yudao.module.jss.controller.app.vo;

/** New JSS contracts use decimal strings for identifiers. */
public record JssAuthContextRespVO(
        String tenantId, String memberUserId, Capabilities capabilities, String contextVersion) {
    public record Capabilities(boolean workspace) {}
}
