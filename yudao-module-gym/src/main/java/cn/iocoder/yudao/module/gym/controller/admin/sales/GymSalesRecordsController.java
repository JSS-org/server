package cn.iocoder.yudao.module.gym.controller.admin.sales;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.*;
import cn.iocoder.yudao.module.gym.service.sales.GymSalesRecordsService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/gym") @Validated @PreAuthorize("@ss.hasPermission('gym:sales:read')")
public class GymSalesRecordsController {
 @Resource private GymSalesRecordsService records;
 @Resource private cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentQueueService fulfillment;
 @Resource private cn.iocoder.yudao.module.gym.service.sales.GymRefundReviewService refundReviews;
 @GetMapping("/self-service-card-orders") public CommonResult<PageResult<GymSalesOrderRespVO>> orders(@Valid GymSalesRecordPageReqVO req){return success(records.orders(req));}
 @GetMapping("/self-service-card-orders/{id}") public CommonResult<GymSalesOrderRespVO> order(@PathVariable("id") @Positive Long id){return success(records.order(id));}
 @GetMapping("/subscriptions") public CommonResult<PageResult<GymSubscriptionRespVO>> subscriptions(@Valid GymSalesRecordPageReqVO req){return success(records.subscriptions(req));}
 @GetMapping("/subscriptions/{id}") public CommonResult<GymSubscriptionRespVO> subscription(@PathVariable("id") @Positive Long id){return success(records.subscription(id));}
 @GetMapping("/subscriptions/{id}/cycles") public CommonResult<PageResult<GymSubscriptionCycleRespVO>> cycles(@PathVariable("id") @Positive Long id,@Valid GymSalesRecordPageReqVO req){return success(records.cycles(id,req));}
 @GetMapping("/verifications") public CommonResult<PageResult<GymVerificationRespVO>> verifications(@Valid GymSalesRecordPageReqVO req){return success(records.verifications(req));}
 @GetMapping("/self-service-card-orders/{id}/refund-reviews")
 public CommonResult<java.util.List<GymRefundReviewRespVO>> refundReviews(@PathVariable("id") @Positive Long id){return success(refundReviews.list(id));}
 @PostMapping("/self-service-card-orders/{id}/refund-reviews/{reviewId}/decisions")
 @PreAuthorize("@ss.hasPermission('gym:sales:read') && @ss.hasPermission('gym:approvals:decide')")
 public CommonResult<GymRefundReviewRespVO> decideRefund(@PathVariable("id") @Positive Long id,@PathVariable("reviewId") @Positive Long reviewId,@Valid @RequestBody GymRefundReviewDecisionReqVO req){return success(refundReviews.decide(id,reviewId,req));}
 @GetMapping("/self-service-card-orders/{id}/fulfillment-task")
 public CommonResult<GymFulfillmentTaskRespVO> fulfillment(@PathVariable("id") @Positive Long id){return success(fulfillment.get(id));}
 @PostMapping("/self-service-card-orders/{id}/fulfillment-retries")
 @PreAuthorize("@ss.hasPermission('gym:sales:read') && @ss.hasPermission('gym:approvals:decide')")
 public CommonResult<GymFulfillmentTaskRespVO> retryFulfillment(@PathVariable("id") @Positive Long id){return success(fulfillment.retry(id));}
}
