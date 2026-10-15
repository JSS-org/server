package cn.iocoder.yudao.module.gym.service.member;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymTrainingPlanMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymTrainingPlanService {
 @Resource private GymTrainingPlanMapper plans;
 @Resource private GymFileMapper files;
 @Resource private GymMemberService members;
 @Resource private GymDataScope scope;
 @Resource private Validator validator;
 public PageResult<GymTrainingPlanRespVO> page(GymBusinessPageReqVO req) {
  if(req.getStoreId()!=null)members.checkMemberListStore(req.getStoreId());
  if(req.getMemberId()!=null)members.requireMember(req.getMemberId(),false);
  if(req.getStatus()!=null&&!java.util.Set.of("active","archived").contains(req.getStatus()))throw exception(TRAINING_PLAN_INVALID);
  var range=GymDateRange.of(req);
  var q=new LambdaQueryWrapper<GymTrainingPlanDO>().apply(members.memberPredicate("gym_training_plan.member_id","gym_training_plan.store_id"))
    .eq(req.getStoreId()!=null,GymTrainingPlanDO::getStoreId,req.getStoreId()).eq(req.getMemberId()!=null,GymTrainingPlanDO::getMemberId,req.getMemberId())
    .eq(req.getStatus()!=null,GymTrainingPlanDO::getStatus,req.getStatus())
    .ge(range.from()!=null,GymTrainingPlanDO::getCreateTime,range.from()==null?null:range.from().atZone(GymDateRange.ZONE).toLocalDateTime())
    .lt(range.to()!=null,GymTrainingPlanDO::getCreateTime,range.to()==null?null:range.to().atZone(GymDateRange.ZONE).toLocalDateTime())
    .orderByDesc(GymTrainingPlanDO::getId);
  if(req.getKeyword()!=null&&!req.getKeyword().isBlank())q.and(w->w.like(GymTrainingPlanDO::getTitle,req.getKeyword()).or().like(GymTrainingPlanDO::getContent,req.getKeyword()));
  var result=plans.selectPage(req,q);return new PageResult<>(result.getList().stream().map(this::response).toList(),result.getTotal());
 }
 public GymTrainingPlanRespVO get(Long id){return response(require(id,false));}
 private GymTrainingPlanDO require(Long id,boolean lock) {
  var row=lock?plans.selectForUpdate(id):plans.selectById(id);if(row==null)throw exception(TRAINING_PLAN_NOT_EXISTS);
  members.checkMemberScope(row.getMemberId(),row.getStoreId());members.requireMember(row.getMemberId(),false);return row;
 }
 @Transactional(rollbackFor=Exception.class)
 public GymTrainingPlanRespVO create(GymTrainingPlanSaveReqVO req) {
  if(req.getStatus()==null)req.setStatus("active");validate(req);
  var row=BeanUtils.toBean(req,GymTrainingPlanDO.class);plans.insert(row);return response(row);
 }
 @Transactional(rollbackFor=Exception.class)
 public GymTrainingPlanRespVO patch(Long id,GymTrainingPlanSaveReqVO patch) {
  var row=require(id,true);
  if((patch.getMemberId()!=null&&!patch.getMemberId().equals(row.getMemberId()))||(patch.getStoreId()!=null&&!patch.getStoreId().equals(row.getStoreId())))throw exception(TRAINING_PLAN_INVALID);
  var req=BeanUtils.toBean(row,GymTrainingPlanSaveReqVO.class);GymPatchUtils.merge(patch,req);
  if(patch.isFileIdPresent())req.setFileId(patch.getFileId());validate(req);
  var update=BeanUtils.toBean(req,GymTrainingPlanDO.class);update.setId(id);
  // Explicit null must remove the attachment; updateById skips null fields.
  plans.update(update,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GymTrainingPlanDO>().eq(GymTrainingPlanDO::getId,id).set(patch.isFileIdPresent()&&patch.getFileId()==null,GymTrainingPlanDO::getFileId,null));
  return get(id);
 }
 private void validate(GymTrainingPlanSaveReqVO req) {
  var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);
  members.requireMember(req.getMemberId(),false);members.checkMemberStore(req.getMemberId(),req.getStoreId());
  if(req.getFileId()==null&&(req.getContent()==null||req.getContent().isBlank()))throw exception(TRAINING_PLAN_INVALID);
  if(req.getFileId()==null)return;
  var file=files.selectById(req.getFileId());
  if(file==null||!"training_plan".equals(file.getCategory())||!req.getStoreId().equals(file.getStoreId()))throw exception(TRAINING_PLAN_FILE_INVALID);
  var broad=scope.readableStoreIds();
  if(broad!=null&&!broad.contains(req.getStoreId())) {
   if(plans.selectCount(new LambdaQueryWrapper<GymTrainingPlanDO>().eq(GymTrainingPlanDO::getFileId,file.getId()).ne(GymTrainingPlanDO::getMemberId,req.getMemberId()))>0)throw exception(TRAINING_PLAN_FILE_INVALID);
   boolean uploadedBySelf=Objects.equals(file.getUploaderId(),SecurityFrameworkUtils.getLoginUserId());
   if(!uploadedBySelf&&plans.selectCount(new LambdaQueryWrapper<GymTrainingPlanDO>().eq(GymTrainingPlanDO::getFileId,file.getId()).eq(GymTrainingPlanDO::getMemberId,req.getMemberId()).eq(GymTrainingPlanDO::getStoreId,req.getStoreId()))==0)throw exception(TRAINING_PLAN_FILE_INVALID);
  }
 }
 private GymTrainingPlanRespVO response(GymTrainingPlanDO row) {
  var result=BeanUtils.toBean(row,GymTrainingPlanRespVO.class);result.setMemberName(members.requireMember(row.getMemberId(),false).getName());result.setStoreName(members.memberStoreName(row.getMemberId(),row.getStoreId()));return result;
 }
}
