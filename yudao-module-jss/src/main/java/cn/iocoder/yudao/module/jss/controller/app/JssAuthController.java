package cn.iocoder.yudao.module.jss.controller.app;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.jss.controller.app.vo.JssAuthContextRespVO;
import cn.iocoder.yudao.module.jss.controller.app.vo.JssWorkspaceSessionRespVO;
import cn.iocoder.yudao.module.jss.service.JssWorkspaceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;

@Tag(name = "用户 APP - JSS 统一身份")
@RestController
@RequestMapping("/jss/auth")
public class JssAuthController {
    @Resource private JssWorkspaceService service;
    @Resource private SecurityProperties security;

    @GetMapping("/context")
    @Operation(summary = "获取当前会员的工作台能力")
    public CommonResult<JssAuthContextRespVO> context(HttpServletRequest request) {
        return success(service.context(token(request)));
    }

    @PostMapping("/workspace-session")
    @Operation(summary = "经服务端绑定与权限校验签发短期工作台会话")
    public CommonResult<JssWorkspaceSessionRespVO> workspaceSession(HttpServletRequest request) {
        return success(service.createSession(token(request)));
    }

    @PostMapping("/logout")
    @PermitAll
    @Operation(summary = "幂等撤销当前会员及关联工作台会话")
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        service.logout(token(request));
        return success(true);
    }

    private String token(HttpServletRequest request) {
        return SecurityFrameworkUtils.obtainAuthorization(
                request, security.getTokenHeader(), security.getTokenParameter());
    }
}
