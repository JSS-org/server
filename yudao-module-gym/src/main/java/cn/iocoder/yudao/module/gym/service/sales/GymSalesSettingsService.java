package cn.iocoder.yudao.module.gym.service.sales;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSalesSettingDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymCardTemplateDO;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSalesSettingMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymCardTemplateMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymSalesSettingsService {
 @Resource private GymSalesSettingMapper gymSalesSettingMapper;
 @Resource private GymCardTemplateMapper gymCardTemplateMapper;
 @Resource private GymStoreMapper gymStoreMapper;
 @Resource private GymStoreService stores;
 @Resource private GymDataScope scope;
 @Resource private Validator validator;
 private static final Set<String> KINDS=Set.of("online-sales-settings","subscription-settings","verification-settings");
 public Map<String,Object> get(Long store,String kind){
  if(!KINDS.contains(kind))throw exception(SALES_SETTINGS_INVALID);
  stores.requireStore(store,false);return response(store,kind,find(store,kind));
 }
 private GymSalesSettingDO find(Long store,String kind){return gymSalesSettingMapper.selectOne(new LambdaQueryWrapper<GymSalesSettingDO>().eq(GymSalesSettingDO::getStoreId,store).eq(GymSalesSettingDO::getKind,kind));}
 private void validate(Object req){var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);}
 private void lockStore(Long store){scope.check(store);if(gymStoreMapper.selectForUpdate(store)==null)throw exception(STORE_NOT_EXISTS);}
 private GymCardTemplateDO template(Long store,Long id,boolean enabled,boolean subscription){
  var row=gymCardTemplateMapper.selectForUpdate(id);
  if(row==null||(enabled&&!"published".equals(row.getStatus()))||!"all".equals(row.getAccessScope())&&!JsonUtils.parseArray(row.getStoreIds(),Long.class).contains(store))throw exception(SALES_SETTINGS_CARD_INVALID);
  if(subscription&&(!"period".equals(row.getKind())||row.getDurationDays()==null||row.getDurationDays()<28||row.getDurationDays()>31))throw exception(SALES_SETTINGS_CARD_INVALID);
  return row;
 }
 private void checkApproval(Boolean contract,Boolean approval){if(approval&&!contract)throw exception(SALES_SETTINGS_INVALID);}
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public Map<String,Object> saveOnline(Long store,GymOnlineSalesSettingsReqVO req){
  validate(req);lockStore(store);var ids=new HashSet<Long>();
  for(var offer:req.getOffers()){if(!ids.add(offer.getCardTemplateId()))throw exception(SALES_SETTINGS_INVALID);checkApproval(offer.getContractRequired(),offer.getApprovalRequired());
   if((offer.getStartsAt()==null)!=(offer.getEndsAt()==null)||offer.getStartsAt()!=null&&!offer.getEndsAt().isAfter(offer.getStartsAt()))throw exception(SALES_SETTINGS_INVALID);
  }
  for(var offer:req.getOffers().stream().sorted(Comparator.comparing(GymOnlineSalesSettingsReqVO.Offer::getCardTemplateId)).toList())template(store,offer.getCardTemplateId(),offer.getEnabled(),false);
  return save(store,"online-sales-settings",req);
 }
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public Map<String,Object> saveSubscription(Long store,GymSubscriptionSettingsReqVO req){
  validate(req);lockStore(store);var ids=new HashSet<Long>();
  for(var item:req.getItems()){if(!ids.add(item.getCardTemplateId()))throw exception(SALES_SETTINGS_INVALID);checkApproval(item.getContractRequired(),item.getApprovalRequired());}
  for(var item:req.getItems().stream().sorted(Comparator.comparing(GymSubscriptionSettingsReqVO.Item::getCardTemplateId)).toList())template(store,item.getCardTemplateId(),item.getEnabled(),true);
  return save(store,"subscription-settings",req);
 }
 @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
 public Map<String,Object> saveVerification(Long store,GymVerificationSettingsReqVO req){
  validate(req);lockStore(store);
  if(new HashSet<>(req.getSupportedChannels()).size()!=req.getSupportedChannels().size()||req.getEnabled()&&req.getSupportedChannels().isEmpty()
   ||(req.getAllowedStart()==null)!=(req.getAllowedEnd()==null)||req.getAllowedStart()!=null&&req.getAllowedStart().compareTo(req.getAllowedEnd())>=0)throw exception(SALES_SETTINGS_INVALID);
  return save(store,"verification-settings",req);
 }
 private Map<String,Object> save(Long store,String kind,Object req){
  String content=JsonUtils.toJsonString(req);var row=find(store,kind);
  if(row==null){row=new GymSalesSettingDO();row.setStoreId(store);row.setKind(kind);row.setContent(content);row.setRevision(1L);gymSalesSettingMapper.insert(row);}
  else if(!content.equals(row.getContent())){row.setContent(content);row.setRevision(row.getRevision()+1);gymSalesSettingMapper.updateById(row);}
  return response(store,kind,row);
 }
 private Map<String,Object> response(Long store,String kind,GymSalesSettingDO row){
  Map<String,Object> value=row==null?defaults(kind):new LinkedHashMap<>(JsonUtils.parseObject(row.getContent(),Map.class));
  for(String key:List.of("offers","items"))if(value.get(key) instanceof List<?> entries)for(Object entry:entries)if(entry instanceof Map<?,?> raw){@SuppressWarnings("unchecked")var item=(Map<String,Object>)raw;if(item.get("cardTemplateId")!=null)item.put("cardTemplateId",item.get("cardTemplateId").toString());}
  value.put("storeId",store.toString());value.put("revision",row==null?0L:row.getRevision());return value;
 }
 private Map<String,Object> defaults(String kind){
  var result=new LinkedHashMap<String,Object>();
  switch(kind){case "online-sales-settings"->result.put("offers",List.of());case "subscription-settings"->result.put("items",List.of());default->{result.put("enabled",false);result.put("requireCheckin",true);result.put("supportedChannels",List.of());result.put("allowedStart",null);result.put("allowedEnd",null);}}
  return result;
 }
}
