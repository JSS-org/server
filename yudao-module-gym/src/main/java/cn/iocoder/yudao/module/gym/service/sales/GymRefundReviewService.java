package cn.iocoder.yudao.module.gym.service.sales;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymRefundReviewService {
 @Resource private GymBusinessOrderMapper gymBusinessOrderMapper;
 @Resource private GymOrderItemMapper gymOrderItemMapper;
 @Resource private GymOrderRefundReviewMapper gymOrderRefundReviewMapper;
 @Resource private GymMemberCardMapper gymMemberCardMapper;
 @Resource private GymDataScope scope;
 @Resource private GymBusinessMutex mutex;
 @Resource private GymBusinessClock clock;
 @Resource private Validator validator;
 private GymBusinessOrderDO order(Long id,boolean lock){
  var row=lock?gymBusinessOrderMapper.selectOne(new LambdaQueryWrapper<GymBusinessOrderDO>().eq(GymBusinessOrderDO::getId,id).last("FOR UPDATE")):gymBusinessOrderMapper.selectById(id);
  if(row==null||!"card_sale".equals(row.getBusinessType())||!Set.of("miniapp","app").contains(row.getChannel()))throw exception(SALES_ORDER_NOT_EXISTS);scope.check(row.getStoreId());return row;
 }
 private Map<Long,String> provenance(GymBusinessOrderDO order){
  var result=new TreeMap<Long,String>();for(var item:gymOrderItemMapper.selectList(new LambdaQueryWrapper<GymOrderItemDO>().eq(GymOrderItemDO::getOrderId,order.getId())))if(item.getEntitlementIds()!=null){var ids=JsonUtils.parseArray(item.getEntitlementIds(),Long.class);for(int unit=0;unit<ids.size();unit++)result.put(ids.get(unit),item.getId()+":"+unit);}return result;
 }
 private Set<Long> issued(GymBusinessOrderDO order){return provenance(order).keySet();}
 private void stop(GymBusinessOrderDO order,Collection<Long> ids){
  var expected=provenance(order);
  for(Long id:new TreeSet<>(ids)){
   var card=gymMemberCardMapper.selectForUpdate(id);
   if(card==null)continue; // An absent entitlement cannot grant access; preserve the independently verified refund fact.
   if(!order.getMemberId().equals(card.getMemberId())||!order.getStoreId().equals(card.getIssuedStoreId())||!"order-item".equals(card.getSourceType())||!Objects.equals(expected.get(id),card.getSourceId()))throw exception(REFUND_REVIEW_INVALID);
   card.setStatus("inactive");gymMemberCardMapper.updateById(card);
  }
 }
 /** User rule: full refund stops every issued card; partial refund creates an immutable review task per receipt. */
 @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
 public void onRefund(GymBusinessOrderDO order,GymOrderRefundDO refund){
  scope.check(order.getStoreId());
  if(order.getRefundedAmount().compareTo(order.getPaidAmount())==0){
   stop(order,issued(order));
   for(var row:gymOrderRefundReviewMapper.selectList(new LambdaQueryWrapper<GymOrderRefundReviewDO>().eq(GymOrderRefundReviewDO::getOrderId,order.getId()).eq(GymOrderRefundReviewDO::getStatus,"pending"))){row.setStatus("closed_full_refund");gymOrderRefundReviewMapper.updateById(row);}
  }else{
   var row=new GymOrderRefundReviewDO();row.setOrderId(order.getId());row.setRefundId(refund.getId());row.setStatus("pending");row.setMemberCardIds("[]");row.setCreatedAt(clock.now().truncatedTo(ChronoUnit.MICROS));gymOrderRefundReviewMapper.insert(row);
  }
 }
 public List<GymRefundReviewRespVO> list(Long id){order(id,false);return gymOrderRefundReviewMapper.selectList(new LambdaQueryWrapper<GymOrderRefundReviewDO>().eq(GymOrderRefundReviewDO::getOrderId,id).orderByDesc(GymOrderRefundReviewDO::getId)).stream().map(this::response).toList();}
 @Transactional(rollbackFor=Exception.class)
 public GymRefundReviewRespVO decide(Long orderId,Long reviewId,GymRefundReviewDecisionReqVO req){
  var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);
  var selected=new TreeSet<>(req.getMemberCardIds());if(selected.size()!=req.getMemberCardIds().size()||"retain".equals(req.getDecision())&&!selected.isEmpty()||"stop_selected".equals(req.getDecision())&&selected.isEmpty())throw exception(REFUND_REVIEW_INVALID);
  mutex.lockStoreEntitlements();var order=order(orderId,true);var row=gymOrderRefundReviewMapper.selectById(reviewId);if(row==null||!orderId.equals(row.getOrderId()))throw exception(REFUND_REVIEW_NOT_EXISTS);
  Long actor=SecurityFrameworkUtils.getLoginUserId();if(actor==null)throw exception(REFUND_REVIEW_INVALID);
  String ids=JsonUtils.toJsonString(selected.stream().map(Object::toString).toList());
  if("resolved".equals(row.getStatus())){
   if(actor.equals(row.getReviewerId())&&req.getDecision().equals(row.getDecision())&&req.getNote().equals(row.getNote())&&ids.equals(row.getMemberCardIds()))return response(row);throw exception(REFUND_REVIEW_INVALID);
  }
  if(!"pending".equals(row.getStatus())||!"partially_refunded".equals(order.getStatus())||!issued(order).containsAll(selected))throw exception(REFUND_REVIEW_INVALID);
  if("stop_selected".equals(req.getDecision()))stop(order,selected);
  row.setStatus("resolved");row.setDecision(req.getDecision());row.setMemberCardIds(ids);row.setNote(req.getNote());row.setReviewerId(actor);row.setReviewedAt(clock.now().truncatedTo(ChronoUnit.MICROS));gymOrderRefundReviewMapper.updateById(row);return response(row);
 }
 private GymRefundReviewRespVO response(GymOrderRefundReviewDO row){var result=BeanUtils.toBean(row,GymRefundReviewRespVO.class);result.setMemberCardIds(JsonUtils.parseArray(row.getMemberCardIds(),String.class));return result;}
}
