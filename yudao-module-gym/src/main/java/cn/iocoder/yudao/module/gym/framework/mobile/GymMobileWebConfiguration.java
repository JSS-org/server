package cn.iocoder.yudao.module.gym.framework.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_PHONE_IMMUTABLE;

import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;

/** Gym-specific mobile routing rules; shared tenant/security filters remain in force. */
@Configuration(proxyBeanMethods = false)
public class GymMobileWebConfiguration {
    @Bean
    public FilterRegistrationBean<GymPublicMediaTenantFilter> gymPublicMediaTenantSelector(
            WebProperties web) {
        var registration = new FilterRegistrationBean<GymPublicMediaTenantFilter>();
        registration.setFilter(new GymPublicMediaTenantFilter(web.getAppApi().getPrefix()));
        registration.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER - 1);
        return registration;
    }

    @Bean
    public MappedInterceptor gymImmutableMemberPhone(WebProperties web) {
        String prefix = web.getAppApi().getPrefix();
        HandlerInterceptor reject =
                new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response,
                            Object handler) {
                        throw exception(MOBILE_PHONE_IMMUTABLE);
                    }
                };
        return new MappedInterceptor(
                new String[] {
                    prefix + "/member/user/update-mobile",
                    prefix + "/member/user/update-mobile-by-weixin"
                },
                reject);
    }
}
