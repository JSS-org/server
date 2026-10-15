package cn.iocoder.yudao.module.gym.service.settlement;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.settlement.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.settlement.*;
import cn.iocoder.yudao.module.gym.dal.mysql.settlement.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymSettlementService {
 @Resource private GymPaymentAccountMapper gymPaymentAccountMapper;
 @Resource private GymSettlementRuleMapper gymSettlementRuleMapper;
 @Resource private GymSettlementMapper gymSettlementMapper;
 @Resource private GymStoreMapper gymStoreMapper;
 @Resource private GymStoreService stores;
 @Resource private GymDataScope scope;
 @Resource private GymBusinessClock clock;
 @Resource private Validator validator;
 private void validate(Object req){var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);}
 private void owner(Long store){if(store==null)scope.requireHeadquarters();else scope.check(store);}
 private GymPaymentAccountDO requireAccount(Long id,boolean lock){
  var q=new LambdaQueryWrapper<GymPaymentAccountDO>().eq(GymPaymentAccountDO::getId,id);if(lock)q.last("FOR UPDATE");
  var row=gymPaymentAccountMapper.selectOne(q);if(row==null)throw exception(PAYMENT_ACCOUNT_NOT_EXISTS);owner(row.getStoreId());return row;
 }
 public PageResult<GymPaymentAccountRespVO> accounts(GymBusinessPageReqVO req){
  if(req.getStoreId()!=null)scope.check(req.getStoreId());var allowed=scope.readableStoreIds();if(allowed!=null&&allowed.isEmpty())return new PageResult<>(List.of(),0L);
  var q=new LambdaQueryWrapper<GymPaymentAccountDO>().eq(req.getStoreId()!=null,GymPaymentAccountDO::getStoreId,req.getStoreId()).in(allowed!=null,GymPaymentAccountDO::getStoreId,allowed)
   .eq(req.getStatus()!=null,GymPaymentAccountDO::getBindingStatus,req.getStatus()).orderByDesc(GymPaymentAccountDO::getId);
  if(req.getKeyword()!=null&&!req.getKeyword().isBlank())q.and(w->w.like(GymPaymentAccountDO::getAccountName,req.getKeyword()).or().like(GymPaymentAccountDO::getMerchantId,req.getKeyword()));
  var page=gymPaymentAccountMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(this::accountResponse).toList(),page.getTotal());
 }
 public GymPaymentAccountRespVO account(Long id){return accountResponse(requireAccount(id,false));}
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymPaymentAccountRespVO createAccount(GymPaymentAccountCreateReqVO req){
  validate(req);owner(req.getStoreId());if(req.getStoreId()!=null)stores.requireStore(req.getStoreId(),false);
  var row=new GymPaymentAccountDO();row.setAccountName(req.getAccountName());row.setStoreId(req.getStoreId());row.setScopeKey(req.getStoreId()==null?0L:req.getStoreId());row.setProvider(req.getProvider());row.setMerchantId(req.getMerchantId());
  row.setLastFour(req.getAccountDetails().getLastFour());row.setCredentialRef(reference(req.getAccountDetails().getCredentialRef()));row.setBindingStatus("unconfigured");
  try{gymPaymentAccountMapper.insert(row);}catch(DuplicateKeyException e){throw exception(PAYMENT_ACCOUNT_DUPLICATE);}return accountResponse(row);
 }
 private String reference(String value){return value==null||value.isBlank()?null:value;}
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymPaymentAccountRespVO patchAccount(Long id,GymPaymentAccountPatchReqVO req){
  validate(req);var row=requireAccount(id,true);
  if(req.isStoreIdPresent()||req.getProvider()!=null||req.getMerchantId()!=null||req.getLastFour()!=null||req.getAccountDetails()!=null||req.getBindingStatus()!=null)throw exception(PAYMENT_ACCOUNT_IMMUTABLE);
  if(req.getAccountName()!=null){if(req.getAccountName().isBlank())throw exception(PAYMENT_ACCOUNT_IMMUTABLE);row.setAccountName(req.getAccountName());}
  if(req.isCredentialRefPresent()&&!Objects.equals(row.getCredentialRef(),reference(req.getCredentialRef()))){checkAccountReferenceUnused(id);row.setCredentialRef(reference(req.getCredentialRef()));row.setBindingStatus("unconfigured");row.setBindingAttemptedAt(null);row.setBindingFailureReason(null);}
  gymPaymentAccountMapper.updateById(row);return accountResponse(row);
 }
 private void checkAccountReferenceUnused(Long accountId){
  var today=clock.now().atZone(GymDateRange.ZONE).toLocalDate();
  var active=gymSettlementRuleMapper.selectList(new LambdaQueryWrapper<GymSettlementRuleDO>().eq(GymSettlementRuleDO::getStatus,"active").and(w->w.isNull(GymSettlementRuleDO::getEffectiveTo).or().gt(GymSettlementRuleDO::getEffectiveTo,today)));
  for(var rule:active)if(JsonUtils.parseArray(rule.getItems(),GymSettlementRuleSaveReqVO.Item.class).stream().anyMatch(i->accountId.equals(i.getPaymentAccountId())))throw exception(PAYMENT_ACCOUNT_IN_USE);
 }
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymPaymentAccountRespVO bind(Long id){
  var row=requireAccount(id,true);if("bound".equals(row.getBindingStatus()))return accountResponse(row);
  // TODO-INTEGRATION-PAYMENT: resolve the credential reference and obtain a verified provider receipt before setting bound.
  row.setBindingStatus("unconfigured");row.setBindingAttemptedAt(clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));row.setBindingFailureReason("支付渠道未配置，未绑定收款账户");gymPaymentAccountMapper.updateById(row);return accountResponse(row);
 }
 private GymPaymentAccountRespVO accountResponse(GymPaymentAccountDO row){
  var result=BeanUtils.toBean(row,GymPaymentAccountRespVO.class);result.setAccountMasked("**** "+row.getLastFour());result.setStoreName(row.getStoreId()==null?"总部":stores.get(row.getStoreId()).getName());return result;
 }
 private GymSettlementRuleDO requireRule(Long id,boolean lock){
  var q=new LambdaQueryWrapper<GymSettlementRuleDO>().eq(GymSettlementRuleDO::getId,id);if(lock)q.last("FOR UPDATE");
  var row=gymSettlementRuleMapper.selectOne(q);if(row==null)throw exception(SETTLEMENT_RULE_NOT_EXISTS);scope.check(row.getStoreId());return row;
 }
 public PageResult<GymSettlementRuleRespVO> rules(Long store,GymBusinessPageReqVO req){
  stores.requireStore(store,false);var q=new LambdaQueryWrapper<GymSettlementRuleDO>().eq(GymSettlementRuleDO::getStoreId,store).eq(req.getStatus()!=null,GymSettlementRuleDO::getStatus,req.getStatus()).orderByDesc(GymSettlementRuleDO::getVersion);
  var page=gymSettlementRuleMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(this::ruleResponse).toList(),page.getTotal());
 }
 public GymSettlementRuleRespVO rule(Long id){return ruleResponse(requireRule(id,false));}
 private void lockStore(Long store){scope.check(store);if(gymStoreMapper.selectForUpdate(store)==null)throw exception(STORE_NOT_EXISTS);}
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymSettlementRuleRespVO createRule(Long store,GymSettlementRuleSaveReqVO req){
  validate(req);if(req.getStatus()!=null&&!"draft".equals(req.getStatus()))throw exception(SETTLEMENT_RULE_INVALID);lockStore(store);
  validateRule(req);var items=snapshotItems(req,false);var last=gymSettlementRuleMapper.selectList(new LambdaQueryWrapper<GymSettlementRuleDO>().eq(GymSettlementRuleDO::getStoreId,store).orderByDesc(GymSettlementRuleDO::getVersion).last("LIMIT 1"));
  var row=new GymSettlementRuleDO();org.springframework.beans.BeanUtils.copyProperties(req,row,"items");row.setStoreId(store);row.setVersion(last.isEmpty()?1:last.getFirst().getVersion()+1);row.setItems(JsonUtils.toJsonString(items));row.setStatus("draft");gymSettlementRuleMapper.insert(row);return ruleResponse(row);
 }
 private void validateRule(GymSettlementRuleSaveReqVO req){
  validate(req);if(req.getEffectiveFrom().getYear()<1000||req.getEffectiveFrom().getYear()>9999||req.getEffectiveTo()!=null&&(req.getEffectiveTo().getYear()>9999||!req.getEffectiveTo().isAfter(req.getEffectiveFrom())))throw exception(SETTLEMENT_RULE_INVALID);
  var ids=new HashSet<Long>();BigDecimal sum=BigDecimal.ZERO;for(var item:req.getItems()){if(!ids.add(item.getPaymentAccountId()))throw exception(SETTLEMENT_RULE_INVALID);sum=sum.add(item.getRatio());}
  if(sum.compareTo(BigDecimal.ONE)!=0)throw exception(SETTLEMENT_RULE_INVALID);
 }
 private List<GymSettlementRuleRespVO.Item> snapshotItems(GymSettlementRuleSaveReqVO req,boolean bound){
  var accounts=new HashMap<Long,GymPaymentAccountDO>();String provider=null;
  for(var item:req.getItems().stream().sorted(Comparator.comparing(GymSettlementRuleSaveReqVO.Item::getPaymentAccountId)).toList()){
   var account=requireAccount(item.getPaymentAccountId(),true);if(bound&&(!"bound".equals(account.getBindingStatus())||account.getCredentialRef()==null))throw exception(SETTLEMENT_ACCOUNT_UNBOUND);
   if(provider!=null&&!provider.equals(account.getProvider()))throw exception(SETTLEMENT_ACCOUNT_CHANNEL_INVALID);provider=account.getProvider();accounts.put(account.getId(),account);
  }
  return req.getItems().stream().map(item->{var account=accounts.get(item.getPaymentAccountId());var result=new GymSettlementRuleRespVO.Item();result.setPaymentAccountId(account.getId().toString());result.setAccountName(account.getAccountName());result.setAccountMasked("**** "+account.getLastFour());result.setRatio(item.getRatio());return result;}).toList();
 }
 private boolean hasContent(GymSettlementRuleSaveReqVO req){return req.getCycleUnit()!=null||req.getCycleValue()!=null||req.getEffectiveFrom()!=null||req.isEffectiveToPresent()||req.getItems()!=null;}
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public GymSettlementRuleRespVO patchRule(Long id,GymSettlementRuleSaveReqVO patch){
  var first=requireRule(id,false);lockStore(first.getStoreId());var row=requireRule(id,true);
  if("active".equals(row.getStatus())){if(hasContent(patch)||patch.getStatus()!=null&&!"active".equals(patch.getStatus()))throw exception(SETTLEMENT_RULE_IMMUTABLE);return ruleResponse(row);}
  var req=new GymSettlementRuleSaveReqVO();req.setCycleUnit(row.getCycleUnit());req.setCycleValue(row.getCycleValue());req.setEffectiveFrom(row.getEffectiveFrom());req.setEffectiveTo(row.getEffectiveTo());req.setStatus(row.getStatus());
  req.setItems(JsonUtils.parseArray(row.getItems(),GymSettlementRuleSaveReqVO.Item.class));
  if(patch.getCycleUnit()!=null)req.setCycleUnit(patch.getCycleUnit());if(patch.getCycleValue()!=null)req.setCycleValue(patch.getCycleValue());if(patch.getEffectiveFrom()!=null)req.setEffectiveFrom(patch.getEffectiveFrom());
  if(patch.isEffectiveToPresent())req.setEffectiveTo(patch.getEffectiveTo());if(patch.getItems()!=null)req.setItems(patch.getItems());if(patch.getStatus()!=null)req.setStatus(patch.getStatus());validateRule(req);
  boolean active="active".equals(req.getStatus());var items=snapshotItems(req,active);
  if(active)activateInterval(row,req);
  row.setCycleUnit(req.getCycleUnit());row.setCycleValue(req.getCycleValue());row.setEffectiveFrom(req.getEffectiveFrom());row.setEffectiveTo(req.getEffectiveTo());row.setItems(JsonUtils.toJsonString(items));row.setStatus(req.getStatus());gymSettlementRuleMapper.updateById(row);return ruleResponse(row);
 }
 private void activateInterval(GymSettlementRuleDO row,GymSettlementRuleSaveReqVO req){
  LocalDate today=clock.now().atZone(GymDateRange.ZONE).toLocalDate();if(req.getEffectiveTo()!=null&&!req.getEffectiveTo().isAfter(today))throw exception(SETTLEMENT_RULE_INVALID);
  var active=gymSettlementRuleMapper.selectList(new LambdaQueryWrapper<GymSettlementRuleDO>().eq(GymSettlementRuleDO::getStoreId,row.getStoreId()).eq(GymSettlementRuleDO::getStatus,"active").ne(GymSettlementRuleDO::getId,row.getId()).orderByAsc(GymSettlementRuleDO::getId).last("FOR UPDATE"));
  for(var old:active)if((old.getEffectiveTo()==null||req.getEffectiveFrom().isBefore(old.getEffectiveTo()))&&(req.getEffectiveTo()==null||old.getEffectiveFrom().isBefore(req.getEffectiveTo()))){
   if(!req.getEffectiveFrom().isAfter(old.getEffectiveFrom())||req.getEffectiveFrom().isBefore(today))throw exception(SETTLEMENT_RULE_OVERLAP);
   old.setEffectiveTo(req.getEffectiveFrom());gymSettlementRuleMapper.updateById(old);
  }
 }
 private GymSettlementRuleRespVO ruleResponse(GymSettlementRuleDO row){var result=new GymSettlementRuleRespVO();org.springframework.beans.BeanUtils.copyProperties(row,result,"id","storeId","items");result.setId(row.getId().toString());result.setStoreId(row.getStoreId().toString());result.setItems(JsonUtils.parseArray(row.getItems(),GymSettlementRuleRespVO.Item.class));return result;}
 public PageResult<GymSettlementRespVO> records(GymBusinessPageReqVO req){
  if(req.getStoreId()!=null)scope.check(req.getStoreId());var allowed=scope.readableStoreIds();if(allowed!=null&&allowed.isEmpty())return new PageResult<>(List.of(),0L);var range=GymDateRange.of(req);
  var q=new LambdaQueryWrapper<GymSettlementDO>().eq(req.getStoreId()!=null,GymSettlementDO::getStoreId,req.getStoreId()).in(allowed!=null,GymSettlementDO::getStoreId,allowed)
   .eq(req.getStatus()!=null,GymSettlementDO::getStatus,req.getStatus()).ge(range.from()!=null,GymSettlementDO::getDueAt,range.from()).lt(range.to()!=null,GymSettlementDO::getDueAt,range.to())
   .like(req.getKeyword()!=null&&!req.getKeyword().isBlank(),GymSettlementDO::getOrderNo,req.getKeyword()).orderByDesc(GymSettlementDO::getId);
  var page=gymSettlementMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(r->recordResponse(r,false)).toList(),page.getTotal());
 }
 public GymSettlementRespVO record(Long id){var row=gymSettlementMapper.selectById(id);if(row==null)throw exception(SETTLEMENT_NOT_EXISTS);scope.check(row.getStoreId());return recordResponse(row,true);}
 private GymSettlementRespVO recordResponse(GymSettlementDO row,boolean detail){var result=new GymSettlementRespVO();org.springframework.beans.BeanUtils.copyProperties(row,result,"id","storeId","items");result.setId(row.getId().toString());result.setStoreId(row.getStoreId().toString());if(detail)result.setItems(JsonUtils.parseArray(row.getItems(),GymSettlementRespVO.Item.class));return result;}
 // TODO-INTEGRATION-PROFIT-SHARING: only verified order/provider callbacks may write settlement snapshots and settledAt; no public success fabrication API.
}
