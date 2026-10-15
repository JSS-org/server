package cn.iocoder.yudao.module.gym.controller.admin.sales;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.*;
import cn.iocoder.yudao.module.gym.service.sales.GymSalesSettingsService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 售卡核销设置") @RestController @RequestMapping("/gym/stores/{storeId}") @Validated
@PreAuthorize("@ss.hasPermission('gym:sales:settings')")
public class GymSalesSettingsController {
 @Resource private GymSalesSettingsService settings;
 @GetMapping("/online-sales-settings") public CommonResult<Map<String,Object>> online(@PathVariable("storeId") @Positive Long store){return success(settings.get(store,"online-sales-settings"));}
 @GetMapping("/subscription-settings") public CommonResult<Map<String,Object>> subscription(@PathVariable("storeId") @Positive Long store){return success(settings.get(store,"subscription-settings"));}
 @GetMapping("/verification-settings") public CommonResult<Map<String,Object>> verification(@PathVariable("storeId") @Positive Long store){return success(settings.get(store,"verification-settings"));}
 @PutMapping("/online-sales-settings") public CommonResult<Map<String,Object>> saveOnline(@PathVariable("storeId") @Positive Long store,@Valid @RequestBody GymOnlineSalesSettingsReqVO req){return success(settings.saveOnline(store,req));}
 @PutMapping("/subscription-settings") public CommonResult<Map<String,Object>> saveSubscription(@PathVariable("storeId") @Positive Long store,@Valid @RequestBody GymSubscriptionSettingsReqVO req){return success(settings.saveSubscription(store,req));}
 @PutMapping("/verification-settings") public CommonResult<Map<String,Object>> saveVerification(@PathVariable("storeId") @Positive Long store,@Valid @RequestBody GymVerificationSettingsReqVO req){return success(settings.saveVerification(store,req));}
}
