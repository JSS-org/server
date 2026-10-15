package cn.iocoder.yudao.module.gym.service.sales;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymOnlineSalesSettingsReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.finance.GymFinancialEventDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.finance.GymFinancialEventMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.*;
import cn.iocoder.yudao.module.gym.service.sales.payment.GymPaymentReceiptVerifier;
import cn.iocoder.yudao.module.gym.service.sales.payment.GymPaymentReceiptVerifier.Receipt;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

/** Internal purchase and verified receipt processing. No admin endpoint accepts payment/refund success fields. */
@Service
public class GymCommerceService {
 @Resource private GymBusinessOrderMapper gymBusinessOrderMapper;
 @Resource private GymOrderItemMapper gymOrderItemMapper;
 @Resource private GymOrderPolicyMapper gymOrderPolicyMapper;
 @Resource private GymOrderPaymentMapper gymOrderPaymentMapper;
 @Resource private GymOrderRefundMapper gymOrderRefundMapper;
 @Resource private GymFinancialEventMapper gymFinancialEventMapper;
 @Resource private cn.iocoder.yudao.module.gym.dal.mysql.finance.GymOrderItemFinancialEventMapper itemFinancialEvents;
 @Resource private GymSalesSettingMapper gymSalesSettingMapper;
 @Resource private GymStoreMapper gymStoreMapper;
 @Resource private GymMemberService members;
 @Resource private GymMemberRecordsService memberRecords;
 @Resource private GymCardService cards;
 @Resource private cn.iocoder.yudao.module.gym.service.contract.GymContractService contracts;
 @Resource private GymStoreService stores;
 @Resource private GymDataScope scope;
 @Resource private GymBusinessMutex mutex;
 @Resource private GymBusinessClock clock;
 @Resource private GymPaymentReceiptVerifier receipts;
 @Resource private GymRefundReviewService refundReviews;
 @Resource private GymFulfillmentQueueService fulfillmentQueue;
 private static final BigDecimal ZERO=new BigDecimal("0.00");

 /** Called by an authenticated purchase flow after resolving its member identity; never accepts a client-supplied price. */
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymBusinessOrderDO prepareCardOrder(Long storeId,Long memberId,Long templateId,int quantity,String channel,String key){
  if(storeId==null||memberId==null||templateId==null||quantity<1||quantity>99||channel==null||!Set.of("miniapp","app").contains(channel)||key==null||key.length()<8||key.length()>128||key.isBlank())throw exception(COMMERCE_REQUEST_INVALID);
  scope.check(storeId);mutex.lockStoreEntitlements();var store=gymStoreMapper.selectForUpdate(storeId);if(store==null)throw exception(STORE_NOT_EXISTS);
  String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(storeId,memberId,templateId,quantity,channel)));
  var old=gymBusinessOrderMapper.selectOne(new LambdaQueryWrapper<GymBusinessOrderDO>().eq(GymBusinessOrderDO::getStoreId,storeId).eq(GymBusinessOrderDO::getChannel,channel).eq(GymBusinessOrderDO::getRequestKey,key).last("FOR UPDATE"));
  if(old!=null){var policy=policy(old.getId());if(policy==null||!hash.equals(policy.getRequestHash()))throw exception(COMMERCE_IDEMPOTENCY_CONFLICT);return old;}
  if(!"active".equals(store.getStatus()))throw exception(COMMERCE_REQUEST_INVALID);
  members.checkMemberStore(memberId,storeId);var member=members.requireMember(memberId,true);memberRecords.checkPurchase(memberId);if(!"active".equals(member.getStatus()))throw exception(COMMERCE_REQUEST_INVALID);
  var template=cards.requireTemplate(templateId,true);if(!"published".equals(template.getStatus())||!"all".equals(template.getAccessScope())&&!cards.templateStoreIds(template).contains(storeId))throw exception(COMMERCE_REQUEST_INVALID);
  var setting=gymSalesSettingMapper.selectOne(new LambdaQueryWrapper<GymSalesSettingDO>().eq(GymSalesSettingDO::getStoreId,storeId).eq(GymSalesSettingDO::getKind,"online-sales-settings"));
  if(setting==null)throw exception(COMMERCE_REQUEST_INVALID);
  var offer=JsonUtils.parseObject(setting.getContent(),GymOnlineSalesSettingsReqVO.class).getOffers().stream().filter(o->templateId.equals(o.getCardTemplateId())&&Boolean.TRUE.equals(o.getEnabled())).findFirst().orElseThrow(()->exception(COMMERCE_REQUEST_INVALID));
  Instant now=clock.now().truncatedTo(ChronoUnit.MICROS);if(offer.getStartsAt()!=null&&(now.isBefore(offer.getStartsAt())||!now.isBefore(offer.getEndsAt())))throw exception(COMMERCE_REQUEST_INVALID);
  if(!money(offer.getSalePrice()))throw exception(COMMERCE_REQUEST_INVALID);
  var row=new GymBusinessOrderDO();row.setStoreId(storeId);row.setMemberId(memberId);row.setMemberName(member.getName());row.setBusinessType("card_sale");row.setChannel(channel);row.setRequestKey(key);row.setOrderNo("G"+UUID.randomUUID().toString().replace("-",""));row.setStatus(offer.getSalePrice().signum()==0?"pending_fulfillment":"pending_payment");row.setPayableAmount(offer.getSalePrice().multiply(BigDecimal.valueOf(quantity)).setScale(2));row.setPaidAmount(ZERO);row.setRefundedAmount(ZERO);row.setOrderedAt(now);gymBusinessOrderMapper.insert(row);
  var item=new GymOrderItemDO();item.setOrderId(row.getId());item.setProductType("card");item.setProductId(templateId);item.setProductNameSnapshot(template.getName());item.setProductVersion(template.getVersion());item.setQuantity(quantity);item.setUnitPrice(offer.getSalePrice());item.setPayableAmount(row.getPayableAmount());gymOrderItemMapper.insert(item);
  var policy=new GymOrderPolicyDO();policy.setOrderId(row.getId());policy.setRequestHash(hash);policy.setApprovalRequired(offer.getApprovalRequired());policy.setContractRequired(offer.getContractRequired());policy.setTermsSnapshot(JsonUtils.toJsonString(Map.of("offer",offer,"template",template,"settingRevision",setting.getRevision())));gymOrderPolicyMapper.insert(policy);fulfillmentQueue.enqueue(row,policy);return row;
 }
 private GymOrderPolicyDO policy(Long id){return gymOrderPolicyMapper.selectOne(new LambdaQueryWrapper<GymOrderPolicyDO>().eq(GymOrderPolicyDO::getOrderId,id));}
 private GymBusinessOrderDO lockedOrder(Long id){
  mutex.lockStoreEntitlements();var row=gymBusinessOrderMapper.selectOne(new LambdaQueryWrapper<GymBusinessOrderDO>().eq(GymBusinessOrderDO::getId,id).last("FOR UPDATE"));
  if(row==null||!"card_sale".equals(row.getBusinessType()))throw exception(SALES_ORDER_NOT_EXISTS);scope.check(row.getStoreId());stores.requireStore(row.getStoreId(),false);return row;
 }
 private void checkBeforeVerification(Long id){var row=gymBusinessOrderMapper.selectById(id);if(row==null||!"card_sale".equals(row.getBusinessType()))throw exception(SALES_ORDER_NOT_EXISTS);scope.check(row.getStoreId());}
 private boolean money(BigDecimal value){return value!=null&&value.signum()>=0&&value.stripTrailingZeros().scale()<=2&&value.precision()-value.scale()<=16;}
 private boolean identity(String value){return value!=null&&!value.isBlank()&&value.length()<=128;}
 private void receipt(GymBusinessOrderDO order,Receipt receipt){
  if(receipt==null||!TenantContextHolder.getRequiredTenantId().equals(receipt.tenantId())||!identity(receipt.provider())||!identity(receipt.receiptId())||!order.getOrderNo().equals(receipt.orderNo())||!"CNY".equals(receipt.currency())||!money(receipt.amount())||receipt.amount().signum()<=0||receipt.occurredAt()==null||receipt.occurredAt().isBefore(order.getOrderedAt())||receipt.occurredAt().isAfter(clock.now().plusSeconds(300)))throw exception(COMMERCE_RECEIPT_INVALID);
 }
 private Instant time(Receipt receipt){return receipt.occurredAt().truncatedTo(ChronoUnit.MICROS);}
 private void event(GymBusinessOrderDO order,Long sourceId,String sourceType,String type,BigDecimal amount,Instant at){
  var event=new GymFinancialEventDO();event.setStoreId(order.getStoreId());event.setEmployeeId(order.getEmployeeId());event.setBusinessOrderId(order.getId());event.setProductType("card");event.setEventType(type);event.setAmount(amount);event.setSourceType(sourceType);event.setSourceId(sourceId.toString());event.setOccurredAt(at);gymFinancialEventMapper.insert(event);
  var items=items(order.getId());var total=items.stream().map(GymOrderItemDO::getPayableAmount).reduce(ZERO,BigDecimal::add);
  if(items.isEmpty()||total.signum()<=0||total.compareTo(order.getPayableAmount())!=0||items.stream().anyMatch(i->!"card".equals(i.getProductType())||i.getPayableAmount().signum()<0))throw exception(COMMERCE_RECEIPT_INVALID);
  BigDecimal before="payment".equals(type)?order.getPaidAmount():order.getRefundedAmount();var weights=items.stream().map(GymOrderItemDO::getPayableAmount).toList();var previous=GymMoneyAllocation.cumulative(before,weights);var next=GymMoneyAllocation.cumulative(before.add(amount),weights);
  for(int index=0;index<items.size();index++){
   var allocation=new cn.iocoder.yudao.module.gym.dal.dataobject.finance.GymOrderItemFinancialEventDO();allocation.setFinancialEventId(event.getId());allocation.setOrderItemId(items.get(index).getId());allocation.setAmount(next.get(index).subtract(previous.get(index)));itemFinancialEvents.insert(allocation);
  }
 }
 /** The adapter re-queries/authenticates the provider. The caller supplies only a reference, never a successful receipt body. */
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymBusinessOrderDO acceptPayment(Long orderId,String reference){
  if(!identity(reference))throw exception(COMMERCE_RECEIPT_INVALID);
  checkBeforeVerification(orderId);var receipt=receipts.payment(reference);var order=lockedOrder(orderId);receipt(order,receipt);
  var old=gymOrderPaymentMapper.selectOne(new LambdaQueryWrapper<GymOrderPaymentDO>().eq(GymOrderPaymentDO::getProvider,receipt.provider()).eq(GymOrderPaymentDO::getProviderPaymentId,receipt.receiptId()));
  if(old!=null){if(!orderId.equals(old.getOrderId())||!"paid".equals(old.getStatus())||old.getAmount().compareTo(receipt.amount())!=0||!old.getPaidAt().equals(time(receipt)))throw exception(COMMERCE_IDEMPOTENCY_CONFLICT);return order;}
  if(!Set.of("pending_payment","partially_paid").contains(order.getStatus())||order.getPaidAmount().add(receipt.amount()).compareTo(order.getPayableAmount())>0)throw exception(COMMERCE_RECEIPT_INVALID);
  var payment=new GymOrderPaymentDO();payment.setOrderId(orderId);payment.setProvider(receipt.provider());payment.setProviderPaymentId(receipt.receiptId());payment.setAmount(receipt.amount());payment.setStatus("paid");payment.setPaidAt(time(receipt));gymOrderPaymentMapper.insert(payment);
  event(order,payment.getId(),"gym_order_payment","payment",receipt.amount(),payment.getPaidAt());order.setPaidAmount(order.getPaidAmount().add(receipt.amount()));order.setStatus(order.getPaidAmount().compareTo(order.getPayableAmount())==0?"paid":"partially_paid");gymBusinessOrderMapper.updateById(order);if("paid".equals(order.getStatus()))fulfillmentQueue.ready(orderId);return order;
 }
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymBusinessOrderDO acceptRefund(Long orderId,String reference){
  if(!identity(reference))throw exception(COMMERCE_RECEIPT_INVALID);
  checkBeforeVerification(orderId);var receipt=receipts.refund(reference);var order=lockedOrder(orderId);receipt(order,receipt);if(!identity(receipt.paymentReceiptId()))throw exception(COMMERCE_RECEIPT_INVALID);
  var payment=gymOrderPaymentMapper.selectOne(new LambdaQueryWrapper<GymOrderPaymentDO>().eq(GymOrderPaymentDO::getProvider,receipt.provider()).eq(GymOrderPaymentDO::getProviderPaymentId,receipt.paymentReceiptId()));
  if(payment==null||!orderId.equals(payment.getOrderId())||!"paid".equals(payment.getStatus())||receipt.occurredAt().isBefore(payment.getPaidAt()))throw exception(COMMERCE_RECEIPT_INVALID);
  var old=gymOrderRefundMapper.selectOne(new LambdaQueryWrapper<GymOrderRefundDO>().eq(GymOrderRefundDO::getProvider,receipt.provider()).eq(GymOrderRefundDO::getProviderRefundId,receipt.receiptId()));
  if(old!=null){if(!orderId.equals(old.getOrderId())||!payment.getId().equals(old.getPaymentId())||!"succeeded".equals(old.getStatus())||old.getAmount().compareTo(receipt.amount())!=0||!old.getRefundedAt().equals(time(receipt)))throw exception(COMMERCE_IDEMPOTENCY_CONFLICT);return order;}
  var previous=gymOrderRefundMapper.selectList(new LambdaQueryWrapper<GymOrderRefundDO>().eq(GymOrderRefundDO::getPaymentId,payment.getId()).eq(GymOrderRefundDO::getStatus,"succeeded")).stream().map(GymOrderRefundDO::getAmount).reduce(ZERO,BigDecimal::add);
  if(previous.add(receipt.amount()).compareTo(payment.getAmount())>0||order.getRefundedAmount().add(receipt.amount()).compareTo(order.getPaidAmount())>0)throw exception(COMMERCE_RECEIPT_INVALID);
  var refund=new GymOrderRefundDO();refund.setOrderId(orderId);refund.setPaymentId(payment.getId());refund.setProvider(receipt.provider());refund.setProviderRefundId(receipt.receiptId());refund.setAmount(receipt.amount());refund.setStatus("succeeded");refund.setRefundedAt(time(receipt));gymOrderRefundMapper.insert(refund);
  event(order,refund.getId(),"gym_order_refund","refund",receipt.amount(),refund.getRefundedAt());order.setRefundedAmount(order.getRefundedAmount().add(receipt.amount()));order.setStatus(order.getRefundedAmount().compareTo(order.getPaidAmount())==0?"refunded":"partially_refunded");gymBusinessOrderMapper.updateById(order);refundReviews.onRefund(order,refund);fulfillmentQueue.cancel(orderId);return order;
 }

 private List<GymOrderItemDO> items(Long id){return gymOrderItemMapper.selectList(new LambdaQueryWrapper<GymOrderItemDO>().eq(GymOrderItemDO::getOrderId,id).orderByAsc(GymOrderItemDO::getId));}
 private GymOrderPolicyDO readyForFulfillment(GymBusinessOrderDO order){
  var policy=policy(order.getId());
  if(policy==null||order.getPaidAmount().compareTo(order.getPayableAmount())!=0||order.getRefundedAmount().signum()!=0||!Set.of("paid","pending_fulfillment").contains(order.getStatus())||policy.getFulfilledAt()!=null||policy.getApprovedApprovalId()!=null)throw exception(COMMERCE_FULFILLMENT_INVALID);
  return policy;
 }
 /** Used by the approval service under the same tenant mutex; sources are resolved on the server. */
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> openingSnapshot(Long id,Long storeId){
  var order=lockedOrder(id);if(!order.getStoreId().equals(storeId))throw exception(COMMERCE_FULFILLMENT_INVALID);readyForFulfillment(order);members.checkMemberStore(order.getMemberId(),storeId);memberRecords.checkPurchase(order.getMemberId());fulfillmentQueue.holdForApproval(id);
  var snapshot=new LinkedHashMap<String,Object>();snapshot.put("sourceType","orders");snapshot.put("sourceId",id.toString());snapshot.put("memberId",order.getMemberId().toString());snapshot.put("memberName",order.getMemberName());snapshot.put("orderNo",order.getOrderNo());snapshot.put("amount",order.getPayableAmount());return snapshot;
 }
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> fulfillApprovedOrder(Long id,Long approvalId){
  if(approvalId==null)throw exception(COMMERCE_FULFILLMENT_INVALID);
  return fulfill(id,approvalId,false);
 }
 /** Internal non-approval fulfillment; the frozen policy controls whether this route is allowed. */
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> fulfillAutomaticOrder(Long id){return fulfill(id,null,true);}
 private Map<String,Object> fulfill(Long id,Long approvalId,boolean automatic){
  var order=lockedOrder(id);var policy=policy(id);
  if(policy!=null&&policy.getFulfilledAt()!=null){
   if(order.getRefundedAmount().signum()!=0||!Objects.equals(policy.getApprovedApprovalId(),approvalId))throw exception(COMMERCE_FULFILLMENT_INVALID);
   return fulfillmentResult(order,policy,items(id));
  }
  policy=readyForFulfillment(order);if(automatic&&(Boolean.TRUE.equals(policy.getApprovalRequired())||fulfillmentQueue.heldForApproval(id)))throw exception(COMMERCE_FULFILLMENT_INVALID);
  var items=items(id);if(items.size()!=1)throw exception(COMMERCE_FULFILLMENT_INVALID);
  var terms=JsonUtils.parseObject(policy.getTermsSnapshot(),Map.class);var purchased=JsonUtils.parseObject(JsonUtils.toJsonString(terms.get("template")),cn.iocoder.yudao.module.gym.dal.dataobject.card.GymCardTemplateDO.class);var item=items.getFirst();
  if(purchased==null||!"card".equals(item.getProductType())||!item.getProductId().equals(purchased.getId())||!item.getProductVersion().equals(purchased.getVersion())||item.getQuantity()==null||item.getQuantity()<1||item.getQuantity()>99||!item.getPayableAmount().equals(order.getPayableAmount()))throw exception(COMMERCE_FULFILLMENT_INVALID);
  Instant now=clock.now().truncatedTo(ChronoUnit.MICROS);var issued=new ArrayList<String>();
  for(int unit=0;unit<item.getQuantity();unit++){
   var card=cards.issuePurchased(order.getMemberId(),order.getStoreId(),purchased,now,item.getId()+":"+unit);
   if(Boolean.TRUE.equals(policy.getContractRequired()))contracts.requireForEntitlement("member-cards",card.getId());issued.add(card.getId().toString());
  }
  item.setEntitlementIds(JsonUtils.toJsonString(issued));gymOrderItemMapper.updateById(item);policy.setApprovedApprovalId(approvalId);policy.setFulfilledAt(now);gymOrderPolicyMapper.updateById(policy);order.setStatus("fulfilled");gymBusinessOrderMapper.updateById(order);fulfillmentQueue.complete(id);return fulfillmentResult(order,policy,items);
 }
 private Map<String,Object> fulfillmentResult(GymBusinessOrderDO order,GymOrderPolicyDO policy,List<GymOrderItemDO> items){
  var result=new LinkedHashMap<String,Object>();result.put("orderId",order.getId().toString());result.put("orderNo",order.getOrderNo());result.put("fulfilledAt",policy.getFulfilledAt());result.put("memberCardIds",items.stream().flatMap(item->JsonUtils.parseArray(item.getEntitlementIds(),String.class).stream()).toList());return result;
 }
}
