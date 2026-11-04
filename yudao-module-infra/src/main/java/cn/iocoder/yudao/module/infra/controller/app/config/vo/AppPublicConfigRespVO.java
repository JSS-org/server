package cn.iocoder.yudao.module.infra.controller.app.config.vo;

import lombok.Data;

import java.util.List;

/** Fixed public display fields. No arbitrary parameter keys or configuration metadata are returned. */
@Data
public class AppPublicConfigRespVO {
    private String appName;
    private String logoUrl;
    private String supportPhone;
    private String supportEmail;
    private String supportQrCode;
    private String serviceHours;
    private String copyright;
    private String copytime;
    private String staticBaseUrl;
    private String agreement;
    private String privacy;
    private String agreementUpdatedAt;
    private String privacyUpdatedAt;
    private List<Banner> banners;

    @Data
    public static class Banner {
        private String imageUrl;
        private String link;
    }
}
