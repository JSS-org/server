package cn.iocoder.yudao.module.gym.controller.admin.device;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.device.vo.*;
import cn.iocoder.yudao.module.gym.service.device.GymDeviceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 智能设备及器械") @RestController @RequestMapping("/gym") @Validated
public class GymDeviceController {
 @Resource private GymDeviceService devices;
 @GetMapping("/devices") @PreAuthorize("@ss.hasPermission('gym:devices:read')")
 public CommonResult<PageResult<GymDeviceRespVO>> devices(@Valid GymBusinessPageReqVO req){return success(devices.devices(req));}
 @GetMapping("/devices/{id}") @PreAuthorize("@ss.hasPermission('gym:devices:read')")
 public CommonResult<GymDeviceRespVO> device(@PathVariable("id") @Positive Long id){return success(devices.device(id));}
 @PostMapping("/devices") @PreAuthorize("@ss.hasPermission('gym:devices:write')")
 public CommonResult<GymDeviceRespVO> createDevice(@Valid @RequestBody GymDeviceSaveReqVO req){return success(devices.createDevice(req));}
 @PatchMapping("/devices/{id}") @PreAuthorize("@ss.hasPermission('gym:devices:write')")
 public CommonResult<GymDeviceRespVO> patchDevice(@PathVariable("id") @Positive Long id,@RequestBody GymDeviceSaveReqVO req){return success(devices.patchDevice(id,req));}
 @PutMapping("/devices/{id}/compartments") @PreAuthorize("@ss.hasPermission('gym:devices:write')")
 public CommonResult<GymDeviceRespVO> compartments(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymCompartmentsReqVO req){return success(devices.saveCompartments(id,req));}
 @GetMapping("/devices/{id}/commands") @PreAuthorize("@ss.hasPermission('gym:devices:read')")
 public CommonResult<PageResult<GymDeviceCommandRespVO>> commands(@PathVariable("id") @Positive Long id,@Valid GymBusinessPageReqVO req){return success(devices.commands(id,req));}
 @PostMapping("/devices/{id}/commands") @PreAuthorize("@ss.hasPermission('gym:devices:command')")
 public CommonResult<GymDeviceCommandRespVO> command(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymDeviceCommandReqVO req,@RequestHeader("Idempotency-Key") String key){return success(devices.command(id,req,key));}
 @GetMapping("/equipment") @PreAuthorize("@ss.hasPermission('gym:equipment:read')")
 public CommonResult<PageResult<GymEquipmentRespVO>> equipment(@Valid GymBusinessPageReqVO req){return success(devices.equipment(req));}
 @GetMapping("/equipment/{id}") @PreAuthorize("@ss.hasPermission('gym:equipment:read')")
 public CommonResult<GymEquipmentRespVO> equipment(@PathVariable("id") @Positive Long id){return success(devices.equipment(id));}
 @PostMapping("/equipment") @PreAuthorize("@ss.hasPermission('gym:equipment:write')")
 public CommonResult<GymEquipmentRespVO> createEquipment(@Valid @RequestBody GymEquipmentSaveReqVO req){return success(devices.createEquipment(req));}
 @PatchMapping("/equipment/{id}") @PreAuthorize("@ss.hasPermission('gym:equipment:write')")
 public CommonResult<GymEquipmentRespVO> patchEquipment(@PathVariable("id") @Positive Long id,@RequestBody GymEquipmentSaveReqVO req){return success(devices.patchEquipment(id,req));}
 @GetMapping("/equipment/{id}/tutorials") @PreAuthorize("@ss.hasPermission('gym:equipment:read')")
 public CommonResult<PageResult<GymTutorialRespVO>> tutorials(@PathVariable("id") @Positive Long id,@Valid GymBusinessPageReqVO req){return success(devices.tutorials(id,req));}
 @PostMapping("/equipment/{id}/tutorials") @PreAuthorize("@ss.hasPermission('gym:equipment:write')")
 public CommonResult<GymTutorialRespVO> createTutorial(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymTutorialSaveReqVO req){return success(devices.createTutorial(id,req));}
 @PatchMapping("/equipment/{id}/tutorials/{tutorialId}") @PreAuthorize("@ss.hasPermission('gym:equipment:write')")
 public CommonResult<GymTutorialRespVO> patchTutorial(@PathVariable("id") @Positive Long id,@PathVariable("tutorialId") @Positive Long tutorial,@Valid @RequestBody GymTutorialStatusReqVO req){return success(devices.patchTutorial(id,tutorial,req));}
}
