package cn.iocoder.yudao.module.gym.framework.mobile;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

/** Keep binding failures and explicit field rejections out of the generic server-error path. */
@RestControllerAdvice(
        basePackages = {
            "cn.iocoder.yudao.module.gym.controller.app.mobile",
            "cn.iocoder.yudao.module.gym.controller.admin.mobile"
        })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GymMobileExceptionAdvice {
    @ExceptionHandler(MissingRequestHeaderException.class)
    public CommonResult<?> missingHeader(MissingRequestHeaderException e) {
        return CommonResult.error(400, "缺少请求头：" + e.getHeaderName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public CommonResult<?> invalidJson(HttpMessageNotReadableException e) {
        Throwable cause = e;
        // Jackson wraps exceptions from @JsonAnySetter. Preserve only intentional business codes.
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof ServiceException business) return CommonResult.error(business);
        }
        return CommonResult.error(400, "请求JSON格式或字段类型不合法");
    }
}
