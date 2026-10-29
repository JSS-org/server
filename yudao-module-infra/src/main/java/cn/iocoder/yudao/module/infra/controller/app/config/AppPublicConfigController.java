package cn.iocoder.yudao.module.infra.controller.app.config;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.controller.app.config.vo.AppPublicConfigRespVO;
import cn.iocoder.yudao.module.infra.service.config.AppPublicConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户 App - 公开展示配置")
@RestController
@RequestMapping("/infra/config")
public class AppPublicConfigController {
    @Resource private AppPublicConfigService publicConfigService;

    @GetMapping("/app-public")
    @PermitAll
    @Operation(summary = "读取当前租户固定公开展示配置")
    public CommonResult<AppPublicConfigRespVO> getPublicConfig() {
        return success(publicConfigService.getPublicConfig());
    }
}
