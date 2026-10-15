package cn.iocoder.yudao.module.gym.service.member;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberCoachDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

/** Member responsibility augments member-scoped access only; it never grants a whole store. */
@Service
public class GymMemberCoachService {
 @Resource private GymMemberCoachMapper coaches;
 @Resource private GymMemberMapper members;
 @Resource private GymMemberStoreMapper memberships;
 @Resource private GymEmployeeMapper employees;
 @Resource private GymEmployeeStoreMapper employments;
 @Resource private GymStoreMapper stores;
 @Resource private GymDataScope scope;
 @Resource private PermissionApi permissions;
 @Resource private AdminUserService users;
 @Resource private GymBusinessMutex mutex;
 @Resource private Validator validator;
 @Resource private GymBusinessClock clock;

 public List<GymMemberCoachRespVO> list(Long memberId,Long storeId) {
  requireManagedMember(memberId,storeId,false);
  return coaches.selectList(new LambdaQueryWrapper<GymMemberCoachDO>().eq(GymMemberCoachDO::getMemberId,memberId)
    .eq(GymMemberCoachDO::getStoreId,storeId).eq(GymMemberCoachDO::getActive,true).orderByAsc(GymMemberCoachDO::getEmployeeId))
   .stream().map(row->{var employee=employees.selectById(row.getEmployeeId());return new GymMemberCoachRespVO(row.getEmployeeId().toString(),employee==null?null:employee.getName());}).toList();
 }
 @Transactional(rollbackFor=Exception.class)
 public List<GymMemberCoachRespVO> replace(Long memberId,Long storeId,GymMemberCoachesReqVO req) {
  var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);
  if(new HashSet<>(req.getEmployeeIds()).size()!=req.getEmployeeIds().size())throw exception(MEMBER_COACH_INVALID);
  mutex.lockStoreEntitlements();requireManagedMember(memberId,storeId,true);
  var store=stores.selectById(storeId);if(store==null||(!req.getEmployeeIds().isEmpty()&&!"active".equals(store.getStatus())))throw exception(MEMBER_COACH_INVALID);
  for(Long employeeId:req.getEmployeeIds().stream().sorted().toList()) {
   var employee=employees.selectForUpdate(employeeId);
   if(employee==null||!"active".equals(employee.getStatus())||employee.getUserId()==null)throw exception(MEMBER_COACH_INVALID);
   var account=users.getUser(employee.getUserId());
   if(account==null||!Integer.valueOf(0).equals(account.getStatus())||!TenantContextHolder.getRequiredTenantId().equals(account.getTenantId())
     ||employments.selectCount(new LambdaQueryWrapper<GymEmployeeStoreDO>().eq(GymEmployeeStoreDO::getEmployeeId,employeeId).eq(GymEmployeeStoreDO::getStoreId,storeId))==0)throw exception(MEMBER_COACH_INVALID);
  }
  var existing=coaches.selectList(new LambdaQueryWrapper<GymMemberCoachDO>().eq(GymMemberCoachDO::getMemberId,memberId).eq(GymMemberCoachDO::getStoreId,storeId));
  var desired=new HashSet<>(req.getEmployeeIds());var present=new HashSet<Long>();
  for(var row:existing) {present.add(row.getEmployeeId());boolean active=desired.contains(row.getEmployeeId());if(!Boolean.valueOf(active).equals(row.getActive())){row.setActive(active);row.setUpdater(SecurityFrameworkUtils.getLoginUserId().toString());row.setUpdateTime(null);coaches.updateById(row);}}
  for(Long employeeId:desired)if(!present.contains(employeeId)){var row=new GymMemberCoachDO();row.setMemberId(memberId);row.setStoreId(storeId);row.setEmployeeId(employeeId);row.setActive(true);coaches.insert(row);}
  return list(memberId,storeId);
 }
 private void requireManagedMember(Long memberId,Long storeId,boolean lock) {
  scope.check(storeId);var member=lock?members.selectForUpdate(memberId):members.selectById(memberId);if(member==null)throw exception(MEMBER_NOT_EXISTS);
  if(memberships.selectCount(new LambdaQueryWrapper<GymMemberStoreDO>().eq(GymMemberStoreDO::getMemberId,memberId).eq(GymMemberStoreDO::getStoreId,storeId))==0)throw exception(STORE_NO_PERMISSION);
 }
 public List<GymMemberCoachDO> selfResponsibilities() {
  var login=SecurityFrameworkUtils.getLoginUser();if(login==null||!Integer.valueOf(2).equals(login.getUserType()))return List.of();
  Long userId=login.getId();if(userId==null)return List.of();
  var permission=permissions.getDeptDataPermission(userId);if(permission==null||!Boolean.TRUE.equals(permission.getSelf()))return List.of();
  var account=users.getUser(userId);if(account==null||!Integer.valueOf(0).equals(account.getStatus())||!TenantContextHolder.getRequiredTenantId().equals(account.getTenantId()))return List.of();
  return coaches.currentResponsibilities(TenantContextHolder.getRequiredTenantId(),userId,clock.now().atZone(GymDateRange.ZONE).toLocalDate());
 }
 /** Both column expressions are internal constants supplied by services, never client strings. */
 public String predicate(String memberColumn,String storeColumn) {
  var ids=scope.readableStoreIds();if(ids==null)return "1=1";
  String broad=ids.isEmpty()?"1=0":storeColumn+" IN ("+numbers(ids)+")";
  var personal=selfResponsibilities().stream().collect(Collectors.groupingBy(GymMemberCoachDO::getMemberId,Collectors.mapping(GymMemberCoachDO::getStoreId,Collectors.toSet())));
  var parts=new ArrayList<String>();parts.add(broad);personal.forEach((member,stores)->parts.add("("+memberColumn+"="+member+" AND "+storeColumn+" IN ("+numbers(stores)+"))"));
  return "("+String.join(" OR ",parts)+")";
 }
 /** Course selection exposes only the current employee's teaching resources, never other coaches' schedules. */
 public String coachPredicate(String coachColumn,String storeColumn) {
  var ids=scope.readableStoreIds();if(ids==null)return "1=1";
  var parts=new ArrayList<String>();parts.add(ids.isEmpty()?"1=0":storeColumn+" IN ("+numbers(ids)+")");
  var personal=selfResponsibilities().stream().collect(Collectors.groupingBy(GymMemberCoachDO::getEmployeeId,Collectors.mapping(GymMemberCoachDO::getStoreId,Collectors.toSet())));
  personal.forEach((coach,stores)->parts.add("("+coachColumn+"="+coach+" AND "+storeColumn+" IN ("+numbers(stores)+"))"));return "("+String.join(" OR ",parts)+")";
 }
 public void checkCoachScope(Long coachId,Long storeId) {
  var ids=scope.readableStoreIds();if(ids==null||ids.contains(storeId))return;
  if(selfResponsibilities().stream().noneMatch(row->coachId.equals(row.getEmployeeId())&&storeId.equals(row.getStoreId())))throw exception(STORE_NO_PERMISSION);
 }
 public String coachStoreName(Long coachId,Long storeId) {
  checkCoachScope(coachId,storeId);var store=stores.selectById(storeId);if(store==null)throw exception(STORE_NO_PERMISSION);return store.getName();
 }
 private String numbers(Collection<Long> ids){return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));}
 public Set<Long> readableMemberStores(Long memberId) {
  if(scope.readableStoreIds()==null)return null;
  return memberships.selectList(new LambdaQueryWrapper<GymMemberStoreDO>().eq(GymMemberStoreDO::getMemberId,memberId)
   .apply(predicate("gym_member_store.member_id","gym_member_store.store_id"))).stream().map(GymMemberStoreDO::getStoreId).collect(Collectors.toSet());
 }
 public void checkMemberStore(Long memberId,Long storeId) {
  var ids=readableMemberStores(memberId);if(ids!=null&&!ids.contains(storeId))throw exception(STORE_NO_PERMISSION);
 }
 public void checkListStore(Long storeId) {
  var ids=scope.readableStoreIds();if(ids==null||ids.contains(storeId))return;
  if(selfResponsibilities().stream().noneMatch(row->storeId.equals(row.getStoreId())))throw exception(STORE_NO_PERMISSION);
 }
 public String memberStoreName(Long memberId,Long storeId) {
  checkMemberStore(memberId,storeId);var store=stores.selectById(storeId);if(store==null)throw exception(STORE_NO_PERMISSION);return store.getName();
 }
}
