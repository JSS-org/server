package cn.iocoder.yudao.module.gym.service.approval;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileRespVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.gym.dal.mysql.approval.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import cn.iocoder.yudao.module.gym.service.course.GymCourseService;
import cn.iocoder.yudao.module.gym.service.member.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymApprovalService {
 @Resource private GymApprovalMapper gymApprovalMapper;
 @Resource private GymApprovalTaskMapper gymApprovalTaskMapper;
 @Resource private GymApprovalTemplateService templates;
 @Resource private GymEmployeeMapper gymEmployeeMapper;
 @Resource private GymFileMapper gymFileMapper;
 @Resource private GymDataScope scope;
 @Resource private GymMemberCoachService memberCoaches;
 @Resource private cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper storeMapper;
 @Resource private GymStoreService stores;
 @Resource private GymMemberService members;
 @Resource private GymMemberRecordsService records;
 @Resource private GymCardService cards;
 @Resource private cn.iocoder.yudao.module.gym.service.sales.GymCommerceService commerce;
 @Resource private GymCourseService courses;
 @Resource private cn.iocoder.yudao.module.gym.service.contract.GymContractService contracts;
 @Resource private AdminUserService adminUserService;
 @Resource private PermissionApi permissionApi;
 @Resource private GymBusinessMutex gymBusinessMutex;
 @Resource private GymBusinessClock gymBusinessClock;
 @Resource private Validator validator;
 private boolean storeReadable(Long storeId) {var ids=scope.readableStoreIds();return ids==null||ids.contains(storeId);}
 private String approvalPredicate() {
  var ids=scope.readableStoreIds();if(ids==null)return "1=1";
  String broad=ids.isEmpty()?"1=0":"gym_approval.store_id IN ("+ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+")";
  Long user=SecurityFrameworkUtils.getLoginUserId();if(user==null)return broad;
  return "("+broad+" OR (gym_approval.applicant_user_id="+user+" AND gym_approval.business_type='price_change' AND "+memberCoaches.coachPredicate("gym_approval.source_coach_id","gym_approval.store_id")+"))";
 }
 private void checkApprovalScope(GymApprovalDO row) {
  if(storeReadable(row.getStoreId()))return;
  if(!Objects.equals(row.getApplicantUserId(),SecurityFrameworkUtils.getLoginUserId())||!"price_change".equals(row.getBusinessType())||row.getSourceCoachId()==null)throw exception(STORE_NO_PERMISSION);
  memberCoaches.checkCoachScope(row.getSourceCoachId(),row.getStoreId());
 }
 private String approvalStoreName(GymApprovalDO row) {
  checkApprovalScope(row);var store=storeMapper.selectById(row.getStoreId());if(store==null)throw exception(STORE_NO_PERMISSION);return store.getName();
 }
 public PageResult<GymApprovalRespVO> page(GymBusinessPageReqVO req) {
  if(req.getStoreId()!=null) memberCoaches.checkListStore(req.getStoreId());
  var range=GymDateRange.of(req);
  var q=new LambdaQueryWrapper<GymApprovalDO>().apply(approvalPredicate())
   .eq(req.getStoreId()!=null,GymApprovalDO::getStoreId,req.getStoreId())
   .eq(req.getBusinessType()!=null,GymApprovalDO::getBusinessType,req.getBusinessType())
   .eq(req.getStatus()!=null,GymApprovalDO::getStatus,req.getStatus())
   .like(req.getKeyword()!=null&&!req.getKeyword().isBlank(),GymApprovalDO::getTitle,req.getKeyword())
   .ge(range.from()!=null,GymApprovalDO::getSubmittedAt,range.from()).lt(range.to()!=null,GymApprovalDO::getSubmittedAt,range.to())
   .orderByDesc(GymApprovalDO::getId);
  var page=gymApprovalMapper.selectPage(req,q);
  return new PageResult<>(page.getList().stream().map(this::response).toList(),page.getTotal());
 }
 public GymApprovalDO require(Long id,boolean lock) {
  var row=lock?gymApprovalMapper.selectForUpdate(id):gymApprovalMapper.selectById(id);
  if(row==null) throw exception(APPROVAL_NOT_EXISTS); checkApprovalScope(row); return row;
 }
 public GymApprovalRespVO get(Long id) {return response(require(id,false));}
 @Transactional(rollbackFor=Exception.class)
 public GymApprovalRespVO create(GymApprovalCreateReqVO req,String key) {
  validate(req); if(key!=null&&(key.isBlank()||key.length()>128)) throw exception(APPROVAL_REQUEST_INVALID);
  gymBusinessMutex.lockStoreEntitlements();
  var userId=SecurityFrameworkUtils.getLoginUserId(); if(key==null) key=UUID.randomUUID().toString();
  String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(req));
  var existing=gymApprovalMapper.selectList(new LambdaQueryWrapper<GymApprovalDO>().eq(GymApprovalDO::getApplicantUserId,userId)
   .eq(GymApprovalDO::getRequestKey,key).last("FOR UPDATE"));
  if(!existing.isEmpty()) {
   var old=existing.getFirst(); checkApprovalScope(old);
   if(!hash.equals(old.getRequestHash())) throw exception(APPROVAL_IDEMPOTENCY_CONFLICT); return response(old);
  }
  boolean personal=!storeReadable(req.getStoreId());
  if(personal) {
   if(!"price_change".equals(req.getBusinessType())||!"course-products".equals(req.getSourceType())||req.getSourceId()==null)throw exception(STORE_NO_PERMISSION);
   var ownProduct=courses.readableProductForPriceRequest(req.getSourceId(),true);
   if(!req.getStoreId().equals(ownProduct.getStoreId()))throw exception(STORE_NO_PERMISSION);
  } else scope.check(req.getStoreId());
  var store=storeMapper.selectById(req.getStoreId());if(store==null||!"active".equals(store.getStatus()))throw exception(APPROVAL_REQUEST_INVALID);
  var template=templates.requireForSubmission(req.getTemplateId(),personal);
  if(!"published".equals(template.getStatus())||!req.getBusinessType().equals(template.getBusinessType())) throw exception(APPROVAL_REQUEST_INVALID);
  var attachments=new ArrayList<Long>(req.getAttachmentIds()==null?List.of():req.getAttachmentIds());
  if(new HashSet<>(attachments).size()!=attachments.size()) throw exception(APPROVAL_ATTACHMENT_INVALID);
  // Price requests need no attachment. SELF cannot borrow unrelated store-private document IDs.
  if(personal&&!attachments.isEmpty())throw exception(APPROVAL_ATTACHMENT_INVALID);
  for(Long id:attachments) {
   var file=gymFileMapper.selectById(id);
   if(file==null||!req.getStoreId().equals(file.getStoreId())||!Set.of("approval","contract").contains(file.getCategory())) throw exception(APPROVAL_ATTACHMENT_INVALID);
  }
  var snapshot=new LinkedHashMap<String,Object>(); snapshot.put("reason",req.getReason());Long sourceCoachId=null;
  switch(req.getBusinessType()) {
   case "card_opening" -> {
    if("orders".equals(req.getSourceType())) {
     if(req.getSourceId()==null||req.getMemberId()!=null||req.getCardTemplateId()!=null||req.getRequestedPrice()!=null)throw exception(APPROVAL_REQUEST_INVALID);
     snapshot.putAll(commerce.openingSnapshot(req.getSourceId(),req.getStoreId()));
    } else {
    if(req.getMemberId()==null||req.getCardTemplateId()==null||req.getRequestedPrice()!=null) throw exception(APPROVAL_REQUEST_INVALID);
    if(req.getSourceType()!=null&&!Set.of("members","card-templates").contains(req.getSourceType())) throw exception(APPROVAL_SOURCE_UNAVAILABLE);
    if(req.getSourceType()!=null&&(req.getSourceId()==null||!req.getSourceId().equals("members".equals(req.getSourceType())?req.getMemberId():req.getCardTemplateId()))) throw exception(APPROVAL_REQUEST_INVALID);
    members.checkMemberStore(req.getMemberId(),req.getStoreId()); var member=members.requireMember(req.getMemberId(),true); records.checkPurchase(member.getId());
    var card=cards.requireTemplate(req.getCardTemplateId(),true);
    if(!"active".equals(member.getStatus())||!"published".equals(card.getStatus())||(!"all".equals(card.getAccessScope())&&!cards.templateStoreIds(card).contains(req.getStoreId()))) throw exception(APPROVAL_REQUEST_INVALID);
    snapshot.put("memberId",member.getId().toString()); snapshot.put("cardTemplateId",card.getId().toString());
    snapshot.put("memberName",member.getName()); snapshot.put("memberPhone",member.getPhone()); snapshot.put("cardName",card.getName());
    snapshot.put("cardType",card.getKind()); snapshot.put("amount",card.getPrice());
    }
   }
   case "price_change" -> {
    if(!"course-products".equals(req.getSourceType())||req.getSourceId()==null||req.getRequestedPrice()==null||req.getMemberId()!=null||req.getCardTemplateId()!=null) throw exception(APPROVAL_REQUEST_INVALID);
    var product=courses.readableProductForPriceRequest(req.getSourceId(),true);sourceCoachId=product.getCoachId();
    if(!req.getStoreId().equals(product.getStoreId())||!"active".equals(product.getStatus())||product.getPrice().compareTo(req.getRequestedPrice())==0) throw exception(APPROVAL_REQUEST_INVALID);
    snapshot.put("sourceId",product.getId().toString()); snapshot.put("sourceType","course-products"); snapshot.put("courseName",product.getName());
    snapshot.put("originalPrice",product.getPrice()); snapshot.put("requestedPrice",req.getRequestedPrice());
   }
   case "contract" -> {
    if(req.getSourceId()==null||req.getMemberId()!=null||req.getCardTemplateId()!=null||req.getRequestedPrice()!=null)throw exception(APPROVAL_REQUEST_INVALID);
    cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO contract;
    if("subscriptions".equals(req.getSourceType())) {
     contract=contracts.subscriptionSigningSource(req.getSourceId(),req.getStoreId());
     if(!attachments.contains(contract.getUnsignedFileId())) {
      if(attachments.size()>=10)throw exception(APPROVAL_ATTACHMENT_INVALID);
      attachments.add(contract.getUnsignedFileId());
     }
     snapshot.put("subscriptionId",req.getSourceId().toString());
    } else if("contracts".equals(req.getSourceType()))contract=contracts.require(req.getSourceId(),true);
    else throw exception(APPROVAL_REQUEST_INVALID);
    if(!req.getStoreId().equals(contract.getStoreId())||!"unsigned".equals(contract.getStatus())||contract.getApprovedApprovalId()!=null||!attachments.contains(contract.getUnsignedFileId()))throw exception(APPROVAL_REQUEST_INVALID);
    snapshot.put("sourceId",contract.getId().toString());snapshot.put("sourceType",req.getSourceType());snapshot.put("unsignedFileId",contract.getUnsignedFileId().toString());
    snapshot.put("contractNo",contract.getContractNo());snapshot.put("contractName",contract.getName());snapshot.put("memberName",members.requireMember(contract.getMemberId(),false).getName());
   }
   case "other_configured" -> {
    if(req.getSourceType()!=null||req.getSourceId()!=null||req.getMemberId()!=null||req.getCardTemplateId()!=null||req.getRequestedPrice()!=null) throw exception(APPROVAL_REQUEST_INVALID);
   }
   default -> throw exception(APPROVAL_REQUEST_INVALID);
  }
  var tasks=new ArrayList<GymApprovalTaskDO>(); var accountIds=new HashSet<Long>();
  for(var step:templates.steps(template)) {
   var employee=gymEmployeeMapper.selectById(step.getApproverEmployeeId());
   // The published template selects the approver; the applicant need not be able to browse that account.
   // Ignore only department data filtering for this internal validity check; tenant isolation stays enabled.
   var account=employee==null||employee.getUserId()==null?null:DataPermissionUtils.executeIgnore(()->adminUserService.getUser(employee.getUserId()));
   if(employee==null||!"active".equals(employee.getStatus())||account==null||!TenantContextHolder.getRequiredTenantId().equals(account.getTenantId())||!Integer.valueOf(0).equals(account.getStatus())||!accountIds.add(account.getId())) throw exception(APPROVAL_APPROVER_INVALID);
   var permission=permissionApi.getDeptDataPermission(account.getId());
   if(permission==null||(!Boolean.TRUE.equals(permission.getAll())&&(permission.getDeptIds()==null||!permission.getDeptIds().contains(req.getStoreId())))
    ||!permissionApi.hasAnyPermissions(account.getId(),"gym:approvals:decide")) throw exception(APPROVAL_APPROVER_INVALID);
   var task=new GymApprovalTaskDO(); task.setStepNo(step.getStepNo()); task.setApproverEmployeeId(employee.getId());
   task.setApproverUserId(account.getId()); task.setApproverName(employee.getName()); task.setStatus(step.getStepNo()==1?"pending":"waiting"); tasks.add(task);
  }
  var applicant=adminUserService.getUser(userId);
  var row=new GymApprovalDO(); row.setStoreId(req.getStoreId()); row.setTemplateId(template.getId()); row.setBusinessType(req.getBusinessType());
  row.setSourceCoachId(sourceCoachId);row.setTitle(req.getTitle().trim()); row.setApplicantUserId(userId); row.setApplicantName(applicant==null?String.valueOf(userId):applicant.getNickname());
  row.setRequestKey(key); row.setRequestHash(hash); row.setRequestSnapshot(JsonUtils.toJsonString(snapshot)); row.setAttachmentIds(JsonUtils.toJsonString(attachments));
  row.setTemplateSnapshot(JsonUtils.toJsonString(template)); row.setStatus("pending"); row.setCurrentStep(1); row.setSubmittedAt(now());
  gymApprovalMapper.insert(row); tasks.forEach(t->{t.setApprovalId(row.getId());gymApprovalTaskMapper.insert(t);}); return response(row);
 }
 @Transactional(rollbackFor=Exception.class)
 public GymApprovalRespVO decide(Long id,GymApprovalDecisionReqVO req) {
  validate(req); gymBusinessMutex.lockStoreEntitlements(); var row=require(id,true);scope.check(row.getStoreId()); var tasks=tasks(id,true);
  var userId=SecurityFrameworkUtils.getLoginUserId(); var owned=tasks.stream().filter(t->userId.equals(t.getApproverUserId())).findFirst().orElseThrow(()->exception(APPROVAL_DECISION_FORBIDDEN));
  String outcome="approve".equals(req.getDecision())?"approved":"rejected";
  if(!"pending".equals(owned.getStatus())) {
   if(outcome.equals(owned.getStatus())) return response(row); throw exception(APPROVAL_DECISION_INVALID);
  }
  if(!"pending".equals(row.getStatus())||!row.getCurrentStep().equals(owned.getStepNo())) throw exception(APPROVAL_DECISION_INVALID);
  var employee=gymEmployeeMapper.selectById(owned.getApproverEmployeeId()); var account=adminUserService.getUser(userId);
  if(employee==null||!"active".equals(employee.getStatus())||!userId.equals(employee.getUserId())||account==null||!Integer.valueOf(0).equals(account.getStatus())) throw exception(APPROVAL_APPROVER_INVALID);
  owned.setStatus(outcome); owned.setComment(req.getComment()); owned.setDecidedAt(now());
  if("rejected".equals(outcome)) {
   row.setStatus("rejected"); row.setCompletedAt(now());
   tasks.stream().filter(t->"waiting".equals(t.getStatus())).forEach(t->{t.setStatus("cancelled");gymApprovalTaskMapper.updateById(t);});
  } else if(owned.getStepNo()<tasks.size()) {
   var next=tasks.get(owned.getStepNo()); next.setStatus("pending"); gymApprovalTaskMapper.updateById(next); row.setCurrentStep(next.getStepNo());
  } else {
   // The business effect and every audit mutation share the surrounding transaction. Failure leaves the step pending.
   var snapshot=JsonUtils.parseObject(row.getRequestSnapshot(),Map.class); var result=new LinkedHashMap<String,Object>();
   switch(row.getBusinessType()) {
    case "card_opening" -> {
     if("orders".equals(snapshot.get("sourceType")))result.putAll(commerce.fulfillApprovedOrder(Long.valueOf(snapshot.get("sourceId").toString()),row.getId()));
     else {
     var card=cards.issue(Long.valueOf(snapshot.get("memberId").toString()),row.getStoreId(),Long.valueOf(snapshot.get("cardTemplateId").toString()),now(),"approval",row.getId().toString());
     result.put("memberCardId",card.getId().toString()); result.put("cardNo",card.getCardNo()); result.put("startsAt",card.getStartsAt()); result.put("expiresAt",card.getExpiresAt());
     }
    }
    case "price_change" -> {
     courses.applyApprovedPrice(Long.valueOf(snapshot.get("sourceId").toString()),new java.math.BigDecimal(snapshot.get("originalPrice").toString()),new java.math.BigDecimal(snapshot.get("requestedPrice").toString()));
     result.put("courseProductId",snapshot.get("sourceId")); result.put("price",snapshot.get("requestedPrice"));
    }
    case "contract" -> {
     var contract="subscriptions".equals(snapshot.get("sourceType"))
      ?contracts.approveSubscriptionSigning(Long.valueOf(snapshot.get("sourceId").toString()),Long.valueOf(snapshot.get("subscriptionId").toString()),row.getId(),Long.valueOf(snapshot.get("unsignedFileId").toString()))
      :contracts.approveSigning(Long.valueOf(snapshot.get("sourceId").toString()),row.getId(),Long.valueOf(snapshot.get("unsignedFileId").toString()));
     result.put("contractId",contract.getId().toString());result.put("signingStatus",contract.getSigningStatus());result.put("status",contract.getStatus());
    }
    case "other_configured" -> result.put("manualReviewCompleted",true);
    default -> throw exception(APPROVAL_SOURCE_UNAVAILABLE);
   }
   row.setBusinessResult(JsonUtils.toJsonString(result)); row.setStatus("approved"); row.setCompletedAt(now());
  }
  gymApprovalTaskMapper.updateById(owned); gymApprovalMapper.updateById(row); return response(row);
 }
 private Instant now() {return gymBusinessClock.now().truncatedTo(ChronoUnit.MICROS);}
 private void validate(Object req) {var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);}
 private List<GymApprovalTaskDO> tasks(Long id,boolean lock) {
  var q=new LambdaQueryWrapper<GymApprovalTaskDO>().eq(GymApprovalTaskDO::getApprovalId,id).orderByAsc(GymApprovalTaskDO::getStepNo);if(lock)q.last("FOR UPDATE");return gymApprovalTaskMapper.selectList(q);
 }
 private GymApprovalRespVO response(GymApprovalDO row) {
  var result=new GymApprovalRespVO(); org.springframework.beans.BeanUtils.copyProperties(row,result,"id","storeId","templateId","requestSnapshot","businessResult");
  result.setId(row.getId().toString()); result.setStoreId(row.getStoreId().toString()); result.setTemplateId(row.getTemplateId().toString()); result.setStoreName(approvalStoreName(row));
  result.setRequestSnapshot(JsonUtils.parseObject(row.getRequestSnapshot(),Map.class)); result.setBusinessResult(row.getBusinessResult()==null?Map.of():JsonUtils.parseObject(row.getBusinessResult(),Map.class));
  var tasks=tasks(row.getId(),false); var active=tasks.stream().filter(t->row.getCurrentStep().equals(t.getStepNo())).findFirst().orElse(null);
  result.setApproverName("pending".equals(row.getStatus())&&active!=null?active.getApproverName():null);
  result.setCanDecide(storeReadable(row.getStoreId())&&"pending".equals(row.getStatus())&&active!=null&&SecurityFrameworkUtils.getLoginUserId().equals(active.getApproverUserId())
   &&permissionApi.hasAnyPermissions(SecurityFrameworkUtils.getLoginUserId(),"gym:approvals:decide"));
  result.setTasks(tasks.stream().map(t->BeanUtils.toBean(t,GymApprovalRespVO.Task.class)).toList());
  result.setAttachments(JsonUtils.parseArray(row.getAttachmentIds(),Long.class).stream().map(gymFileMapper::selectById).filter(Objects::nonNull).map(f->BeanUtils.toBean(f,GymFileRespVO.class)).toList());return result;
 }
}
