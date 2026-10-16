package cn.iocoder.yudao.module.gym.controller.admin.content;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.content.vo.*;
import cn.iocoder.yudao.module.gym.service.content.GymContentService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - gym 展示配置与场景码")
@RestController
@RequestMapping("/gym")
@Validated
public class GymContentController {
    @Resource private GymContentService content;

    @GetMapping("/client-settings")
    @PreAuthorize("@ss.hasPermission('gym:content:manage')")
    public CommonResult<Map<String, Object>> settings(
            @RequestParam(name = "platform", defaultValue = "miniapp")
                    @Pattern(regexp = "miniapp|app")
                    String platform,
            @RequestParam(name = "storeId", required = false) @Positive Long store) {
        return success(content.settings(platform, store));
    }

    @PutMapping("/client-settings")
    @PreAuthorize("@ss.hasPermission('gym:content:manage')")
    public CommonResult<Map<String, Object>> save(@Valid @RequestBody GymClientSettingsReqVO req) {
        return success(content.save(req));
    }

    @GetMapping("/qr-codes")
    @PreAuthorize("@ss.hasPermission('gym:stores:read')")
    public CommonResult<PageResult<GymQrCodeRespVO>> codes(@Valid GymQrCodePageReqVO req) {
        return success(content.codes(req));
    }

    @GetMapping("/qr-codes/{id}")
    @PreAuthorize("@ss.hasPermission('gym:stores:read')")
    public CommonResult<GymQrCodeRespVO> code(@PathVariable("id") @Positive Long id) {
        return success(content.code(id));
    }

    @PostMapping("/qr-codes")
    @PreAuthorize("@ss.hasPermission('gym:stores:write')")
    public CommonResult<GymQrCodeRespVO> createCode(@Valid @RequestBody GymQrCodeReqVO req) {
        return success(content.createCode(req));
    }
}
