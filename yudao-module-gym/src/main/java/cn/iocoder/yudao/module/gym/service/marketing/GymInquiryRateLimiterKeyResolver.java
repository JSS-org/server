package cn.iocoder.yudao.module.gym.service.marketing;
import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver;
import org.aspectj.lang.JoinPoint;
import org.springframework.stereotype.Component;
/** A caller cannot bypass the IP limit by changing contact details or idempotency keys, or by spoofing forwarding headers. */
@Component public class GymInquiryRateLimiterKeyResolver implements RateLimiterKeyResolver {
 @Override public String resolver(JoinPoint point,RateLimiter limiter){return SecureUtil.sha256("gym-franchise:"+TenantContextHolder.getRequiredTenantId()+":"+ServletUtils.getRequest().getRemoteAddr());}
}
