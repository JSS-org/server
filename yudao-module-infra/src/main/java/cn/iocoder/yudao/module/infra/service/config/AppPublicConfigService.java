package cn.iocoder.yudao.module.infra.service.config;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.controller.app.config.vo.AppPublicConfigRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;

@Service
public class AppPublicConfigService {
    @Resource private ConfigService configService;

    public AppPublicConfigRespVO getPublicConfig() {
        String prefix = "jss.app.public." + TenantContextHolder.getRequiredTenantId() + ".";
        AppPublicConfigRespVO result = new AppPublicConfigRespVO();
        String name = value(prefix, "name", 80);
        result.setAppName(name.isEmpty() ? "JSS" : name);
        result.setLogoUrl(image(value(prefix, "logo", 500)));
        String phone = value(prefix, "support-phone", 32);
        result.setSupportPhone(phone.matches("[+0-9][0-9 ()-]{2,31}") ? phone : "");
        result.setSupportEmail(value(prefix, "support-email", 100));
        result.setSupportQrCode(image(value(prefix, "support-qr", 500)));
        result.setServiceHours(value(prefix, "service-hours", 200));
        result.setCopyright(value(prefix, "copyright", 300));
        result.setCopytime(value(prefix, "copytime", 100));
        result.setStaticBaseUrl(http(value(prefix, "static-base-url", 500), false));
        result.setAgreement(policy(value(prefix, "agreement", 500)));
        result.setPrivacy(policy(value(prefix, "privacy", 500)));
        result.setAgreementUpdatedAt(value(prefix, "agreement-updated-at", 40));
        result.setPrivacyUpdatedAt(value(prefix, "privacy-updated-at", 40));
        var banners = new ArrayList<AppPublicConfigRespVO.Banner>();
        for (int index = 1; index <= 3; index++) {
            String image = image(value(prefix, "banner." + index + ".image", 500));
            if (image.isEmpty()) continue;
            var banner = new AppPublicConfigRespVO.Banner();
            banner.setImageUrl(image);
            String link = value(prefix, "banner." + index + ".link", 500);
            banner.setLink(
                    link.startsWith("/pages/") && validCharacters(link) ? link : http(link, true));
            banners.add(banner);
        }
        result.setBanners(banners);
        return result;
    }

    private String value(String prefix, String suffix, int maximum) {
        ConfigDO config = configService.getConfigByKey(prefix + suffix);
        if (config == null
                || !Boolean.TRUE.equals(config.getVisible())
                || config.getValue() == null) return "";
        String value = config.getValue().trim();
        return value.length() <= maximum ? value : "";
    }

    private static boolean validCharacters(String value) {
        return !value.contains("\\")
                && value.chars()
                        .noneMatch(
                                character ->
                                        Character.isWhitespace(character)
                                                || Character.isISOControl(character));
    }

    private static String image(String value) {
        if (value.startsWith("/static/") && validCharacters(value) && !value.contains(".."))
            return value;
        return http(value, false);
    }

    private static String policy(String value) {
        return value.equals("source-template") ? value : http(value, true);
    }

    private static String http(String value, boolean httpsOnly) {
        if (!validCharacters(value) || value.isEmpty()) return "";
        try {
            URI uri = URI.create(value);
            boolean scheme =
                    "https".equalsIgnoreCase(uri.getScheme())
                            || (!httpsOnly && "http".equalsIgnoreCase(uri.getScheme()));
            return scheme && uri.getHost() != null && uri.getUserInfo() == null ? value : "";
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }
}
