package cn.iocoder.yudao.module.gym.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMeasurementDO;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMeasurementMapper;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** A file ID or uploader identity alone never grants SELF access to a private attachment. */
@Service
public class GymMemberFileAccess {
 @Resource private GymMemberCoachService coaches;
 @Resource private GymContractMapper contracts;
 @Resource private GymMeasurementMapper measurements;
 @Resource private PermissionApi permissions;
 @Resource private cn.iocoder.yudao.module.gym.dal.mysql.member.GymTrainingPlanMapper plans;

 /** Uploading a report does not grant access to it: a member-scoped measurement must bind it first. */
 public boolean canUploadReport(Long storeId, String category) {
  return storeId != null && ("body_report".equals(category)||"training_plan".equals(category))
    && permissions.hasAnyPermissions(SecurityFrameworkUtils.getLoginUserId(), "gym:measurements:write")
    && coaches.selfResponsibilities().stream().anyMatch(row -> storeId.equals(row.getStoreId()));
 }

 public boolean canRead(GymFileDO file) {
  if(file.getStoreId()==null||coaches.selfResponsibilities().isEmpty())return false;
  Long user=SecurityFrameworkUtils.getLoginUserId();
  if("contract".equals(file.getCategory())&&permissions.hasAnyPermissions(user,"gym:contracts:read")) {
   String access=coaches.predicate("gym_contract.member_id","gym_contract.store_id");
   var visible=contracts.selectCount(contractFiles(file).eq(GymContractDO::getStoreId,file.getStoreId()).apply(access));
   return visible>0&&contracts.selectCount(contractFiles(file).apply("NOT ("+access+")"))==0;
  }
  if("body_report".equals(file.getCategory())&&permissions.hasAnyPermissions(user,"gym:measurements:read")) {
   String access=coaches.predicate("gym_measurement.member_id","gym_measurement.store_id");
   var visible=measurements.selectCount(reportFiles(file).eq(GymMeasurementDO::getStoreId,file.getStoreId()).apply(access));
   return visible>0&&measurements.selectCount(reportFiles(file).apply("NOT ("+access+")"))==0;
  }
  if("training_plan".equals(file.getCategory())&&permissions.hasAnyPermissions(user,"gym:measurements:read")) {
   String access=coaches.predicate("gym_training_plan.member_id","gym_training_plan.store_id");
   var visible=plans.selectCount(planFiles(file).eq(cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO::getStoreId,file.getStoreId()).apply(access));
   return visible>0&&plans.selectCount(planFiles(file).apply("NOT ("+access+")"))==0;
  }
  return false;
 }
 private LambdaQueryWrapper<cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO> planFiles(GymFileDO file) {
  return new LambdaQueryWrapper<cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO>().eq(cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO::getFileId,file.getId());
 }
 private LambdaQueryWrapper<GymContractDO> contractFiles(GymFileDO file) {
  return new LambdaQueryWrapper<GymContractDO>()
   .and(q->q.eq(GymContractDO::getUnsignedFileId,file.getId()).or().eq(GymContractDO::getSignedFileId,file.getId()));
 }
 private LambdaQueryWrapper<GymMeasurementDO> reportFiles(GymFileDO file) {
  return new LambdaQueryWrapper<GymMeasurementDO>().eq(GymMeasurementDO::getReportFileId,file.getId());
 }
}
