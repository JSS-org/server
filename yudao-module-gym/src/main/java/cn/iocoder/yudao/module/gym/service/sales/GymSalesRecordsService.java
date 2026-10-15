package cn.iocoder.yudao.module.gym.service.sales;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
/** Read models for genuine commerce records; no admin endpoint can write a paid/authorized/redeemed state. */
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class GymSalesRecordsService {
 @Resource private GymBusinessOrderMapper gymBusinessOrderMapper;
 @Resource private GymOrderItemMapper gymOrderItemMapper;
 @Resource private GymOrderPolicyMapper gymOrderPolicyMapper;
 @Resource private GymOrderPaymentMapper gymOrderPaymentMapper;
 @Resource private GymOrderRefundMapper gymOrderRefundMapper;
 @Resource private GymSubscriptionMapper gymSubscriptionMapper;
 @Resource private GymSubscriptionCycleMapper gymSubscriptionCycleMapper;
 @Resource private GymVerificationMapper gymVerificationMapper;
 @Resource private GymDataScope scope;
 @Resource private GymStoreService stores;
 @Resource private GymRefundReviewService refundReviews;
 @Resource private GymFulfillmentQueueService fulfillment;
 private Set<Long> allowed(GymSalesRecordPageReqVO req){if(req.getStoreId()!=null)scope.check(req.getStoreId());return scope.readableStoreIds();}
 private boolean keyword(String value){return value!=null&&!value.isBlank();}
 public PageResult<GymSalesOrderRespVO> orders(GymSalesRecordPageReqVO req){
  var allowed=allowed(req);var range=GymDateRange.of(req);if(allowed!=null&&allowed.isEmpty())return new PageResult<>(List.of(),0L);
  var q=new LambdaQueryWrapper<GymBusinessOrderDO>().eq(GymBusinessOrderDO::getBusinessType,"card_sale").in(GymBusinessOrderDO::getChannel,List.of("miniapp","app"))
   .eq(req.getStoreId()!=null,GymBusinessOrderDO::getStoreId,req.getStoreId()).in(allowed!=null,GymBusinessOrderDO::getStoreId,allowed)
   .eq(req.getMemberId()!=null,GymBusinessOrderDO::getMemberId,req.getMemberId()).eq(req.getStatus()!=null,GymBusinessOrderDO::getStatus,req.getStatus()).eq(req.getChannel()!=null,GymBusinessOrderDO::getChannel,req.getChannel())
   .ge(range.from()!=null,GymBusinessOrderDO::getOrderedAt,range.from()).lt(range.to()!=null,GymBusinessOrderDO::getOrderedAt,range.to())
   .and(keyword(req.getKeyword()),w->w.like(GymBusinessOrderDO::getOrderNo,req.getKeyword()).or().like(GymBusinessOrderDO::getMemberName,req.getKeyword())).orderByDesc(GymBusinessOrderDO::getId);
  var page=gymBusinessOrderMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(r->orderResponse(r,false)).toList(),page.getTotal());
 }
 public GymSalesOrderRespVO order(Long id){
  var row=gymBusinessOrderMapper.selectById(id);if(row==null||!"card_sale".equals(row.getBusinessType())||!Set.of("miniapp","app").contains(row.getChannel()))throw exception(SALES_ORDER_NOT_EXISTS);scope.check(row.getStoreId());return orderResponse(row,true);
 }
 private GymSalesOrderRespVO orderResponse(GymBusinessOrderDO row,boolean detail){
  var result=BeanUtils.toBean(row,GymSalesOrderRespVO.class);result.setStoreName(stores.get(row.getStoreId()).getName());
  if(detail){
   result.setFulfillmentTask(fulfillment.get(row.getId()));
   result.setRefundReviews(refundReviews.list(row.getId()));
   var policy=gymOrderPolicyMapper.selectOne(new LambdaQueryWrapper<GymOrderPolicyDO>().eq(GymOrderPolicyDO::getOrderId,row.getId()));
   if(policy!=null){result.setApprovalRequired(policy.getApprovalRequired());result.setContractRequired(policy.getContractRequired());result.setFulfilledAt(policy.getFulfilledAt());}
   var items=gymOrderItemMapper.selectList(new LambdaQueryWrapper<GymOrderItemDO>().eq(GymOrderItemDO::getOrderId,row.getId()).orderByAsc(GymOrderItemDO::getId));
   result.setMemberCardIds(items.stream().filter(item->item.getEntitlementIds()!=null).flatMap(item->cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(item.getEntitlementIds(),String.class).stream()).toList());
   result.setItems(items.stream().map(r->BeanUtils.toBean(r,GymSalesOrderRespVO.Item.class)).toList());
   result.setPayments(gymOrderPaymentMapper.selectList(new LambdaQueryWrapper<GymOrderPaymentDO>().eq(GymOrderPaymentDO::getOrderId,row.getId()).orderByAsc(GymOrderPaymentDO::getId)).stream().map(r->BeanUtils.toBean(r,GymSalesOrderRespVO.Payment.class)).toList());
   result.setRefunds(gymOrderRefundMapper.selectList(new LambdaQueryWrapper<GymOrderRefundDO>().eq(GymOrderRefundDO::getOrderId,row.getId()).orderByAsc(GymOrderRefundDO::getId)).stream().map(r->BeanUtils.toBean(r,GymSalesOrderRespVO.Refund.class)).toList());
  }return result;
 }
 public PageResult<GymSubscriptionRespVO> subscriptions(GymSalesRecordPageReqVO req){
  var allowed=allowed(req);var range=GymDateRange.of(req);if(allowed!=null&&allowed.isEmpty())return new PageResult<>(List.of(),0L);
  var q=new LambdaQueryWrapper<GymSubscriptionDO>().eq(req.getStoreId()!=null,GymSubscriptionDO::getStoreId,req.getStoreId()).in(allowed!=null,GymSubscriptionDO::getStoreId,allowed)
   .eq(req.getMemberId()!=null,GymSubscriptionDO::getMemberId,req.getMemberId()).eq(req.getStatus()!=null,GymSubscriptionDO::getStatus,req.getStatus()).eq(req.getChannel()!=null,GymSubscriptionDO::getChannel,req.getChannel())
   .ge(range.from()!=null,GymSubscriptionDO::getRequestedAt,range.from()).lt(range.to()!=null,GymSubscriptionDO::getRequestedAt,range.to())
   .like(keyword(req.getKeyword()),GymSubscriptionDO::getMemberName,req.getKeyword()).orderByDesc(GymSubscriptionDO::getId);
  var page=gymSubscriptionMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(this::subscriptionResponse).toList(),page.getTotal());
 }
 private GymSubscriptionDO requireSubscription(Long id){var row=gymSubscriptionMapper.selectById(id);if(row==null)throw exception(SUBSCRIPTION_NOT_EXISTS);scope.check(row.getStoreId());return row;}
 public GymSubscriptionRespVO subscription(Long id){return subscriptionResponse(requireSubscription(id));}
 private GymSubscriptionRespVO subscriptionResponse(GymSubscriptionDO row){var result=BeanUtils.toBean(row,GymSubscriptionRespVO.class);result.setStoreName(stores.get(row.getStoreId()).getName());return result;}
 public PageResult<GymSubscriptionCycleRespVO> cycles(Long id,GymSalesRecordPageReqVO req){
  var parent=requireSubscription(id);if(req.getStoreId()!=null){scope.check(req.getStoreId());if(!req.getStoreId().equals(parent.getStoreId()))return new PageResult<>(List.of(),0L);}var range=GymDateRange.of(req);
  if(req.getMemberId()!=null&&!req.getMemberId().equals(parent.getMemberId())||req.getChannel()!=null&&!req.getChannel().equals(parent.getChannel())||keyword(req.getKeyword())&&!parent.getMemberName().contains(req.getKeyword()))return new PageResult<>(List.of(),0L);
  // Cycle filtering refers to its Beijing period start, including pending/failed cycles without chargedAt.
  var from=range.from()==null?null:cycleBoundary(range.from());var to=range.to()==null?null:cycleBoundary(range.to());
  var q=new LambdaQueryWrapper<GymSubscriptionCycleDO>().eq(GymSubscriptionCycleDO::getSubscriptionId,id).eq(req.getStatus()!=null,GymSubscriptionCycleDO::getStatus,req.getStatus()).ge(from!=null,GymSubscriptionCycleDO::getPeriodStart,from).lt(to!=null,GymSubscriptionCycleDO::getPeriodStart,to).orderByDesc(GymSubscriptionCycleDO::getPeriodStart).orderByDesc(GymSubscriptionCycleDO::getId);
  var page=gymSubscriptionCycleMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(r->BeanUtils.toBean(r,GymSubscriptionCycleRespVO.class)).toList(),page.getTotal());
 }
 private java.time.LocalDate cycleBoundary(java.time.Instant at){var day=at.atZone(GymDateRange.ZONE).toLocalDate();return at.equals(day.atStartOfDay(GymDateRange.ZONE).toInstant())?day:day.plusDays(1);}
 public PageResult<GymVerificationRespVO> verifications(GymSalesRecordPageReqVO req){
  var allowed=allowed(req);var range=GymDateRange.of(req);if(allowed!=null&&allowed.isEmpty())return new PageResult<>(List.of(),0L);
  var q=new LambdaQueryWrapper<GymVerificationDO>().eq(req.getStoreId()!=null,GymVerificationDO::getStoreId,req.getStoreId()).in(allowed!=null,GymVerificationDO::getStoreId,allowed)
   .eq(req.getMemberId()!=null,GymVerificationDO::getMemberId,req.getMemberId()).eq(req.getStatus()!=null,GymVerificationDO::getStatus,req.getStatus()).eq(req.getType()!=null,GymVerificationDO::getType,req.getType()).eq(req.getChannel()!=null,GymVerificationDO::getChannel,req.getChannel())
   .ge(range.from()!=null,GymVerificationDO::getVerifiedAt,range.from()).lt(range.to()!=null,GymVerificationDO::getVerifiedAt,range.to()).like(keyword(req.getKeyword()),GymVerificationDO::getMemberName,req.getKeyword()).orderByDesc(GymVerificationDO::getId);
  var page=gymVerificationMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(r->{var result=BeanUtils.toBean(r,GymVerificationRespVO.class);result.setStoreName(stores.get(r.getStoreId()).getName());return result;}).toList(),page.getTotal());
 }
 // TODO-INTEGRATION-COMMERCE: debit authorization/cycles and coupon redemption still need trusted provider adapters.
 // Card order payment/refund writers live in GymCommerceService; a signing approval never authorizes a debit.
}
