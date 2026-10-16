package cn.iocoder.yudao.module.gym.controller.app.marketing;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymFranchiseSubmitReqVO;
import cn.iocoder.yudao.module.gym.service.marketing.GymInquiryRateLimiterKeyResolver;
import cn.iocoder.yudao.module.gym.service.marketing.GymMarketingService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户端 - gym 加盟意向提交")
@RestController
@RequestMapping("/gym/franchise-inquiries")
@Validated
public class AppGymFranchiseController {
    @Resource private GymMarketingService marketing;

    @PostMapping
    @PermitAll
    @RateLimiter(time = 60, count = 5, keyResolver = GymInquiryRateLimiterKeyResolver.class)
    public CommonResult<Map<String, String>> submit(
            @Valid @RequestBody GymFranchiseSubmitReqVO req,
            @RequestHeader("Idempotency-Key") String key) {
        // Anonymous receipts never disclose the submitted contact details or the current handling
        // status.
        return success(
                Map.of(
                        "id",
                        marketing.submitInquiry(req, key, "client").toString(),
                        "status",
                        "received"));
    }
}
