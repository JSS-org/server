package cn.iocoder.yudao.module.gym.service.contract;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gym.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractTemplateDO;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractTemplateMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymContractTemplateService {
 @Resource private GymContractTemplateMapper gymContractTemplateMapper;
 @Resource private GymFileMapper gymFileMapper;
 @Resource private GymDataScope scope;
 @Resource private Validator validator;
 public PageResult<GymContractTemplateRespVO> page(GymBusinessPageReqVO req) {
  var allowed=scope.readableStoreIds();if(allowed!=null&&allowed.isEmpty())return new PageResult<>(List.of(),0L);
  var q=new LambdaQueryWrapper<GymContractTemplateDO>().eq(allowed!=null,GymContractTemplateDO::getStatus,"published")
   .eq(req.getStatus()!=null,GymContractTemplateDO::getStatus,req.getStatus())
   .eq(req.getApplicableType()!=null,GymContractTemplateDO::getApplicableType,req.getApplicableType())
   .like(req.getKeyword()!=null&&!req.getKeyword().isBlank(),GymContractTemplateDO::getName,req.getKeyword()).orderByDesc(GymContractTemplateDO::getId);
  var page=gymContractTemplateMapper.selectPage(req,q);return new PageResult<>(page.getList().stream().map(this::response).toList(),page.getTotal());
 }
 public GymContractTemplateDO require(Long id,boolean lock) {
  var row=lock?gymContractTemplateMapper.selectForUpdate(id):gymContractTemplateMapper.selectById(id);
  if(row==null)throw exception(CONTRACT_TEMPLATE_NOT_EXISTS);
  var allowed=scope.readableStoreIds();if(allowed!=null&&(allowed.isEmpty()||!"published".equals(row.getStatus())))throw exception(STORE_NO_PERMISSION);return row;
 }
 public GymContractTemplateRespVO get(Long id){return response(require(id,false));}
 @Transactional(rollbackFor=Exception.class)
 public GymContractTemplateRespVO create(GymContractTemplateSaveReqVO req) {
  scope.requireHeadquarters();if(req.getStatus()==null)req.setStatus("draft");validate(req);
  var row=BeanUtils.toBean(req,GymContractTemplateDO.class);row.setVersion(1);gymContractTemplateMapper.insert(row);
  row.setFamilyId(row.getId());gymContractTemplateMapper.updateById(row);return response(row);
 }
 @Transactional(rollbackFor=Exception.class)
 public GymContractTemplateRespVO patch(Long id,GymContractTemplateSaveReqVO patch) {
  scope.requireHeadquarters();var old=require(id,true);
  if(!"draft".equals(old.getStatus())&&(patch.getName()!=null||patch.getApplicableType()!=null||patch.getReminderDays()!=null||patch.getFileId()!=null||"draft".equals(patch.getStatus())))throw exception(CONTRACT_TEMPLATE_IMMUTABLE);
  var req=BeanUtils.toBean(old,GymContractTemplateSaveReqVO.class);GymPatchUtils.merge(patch,req);validate(req);
  var row=BeanUtils.toBean(req,GymContractTemplateDO.class);row.setId(id);row.setFamilyId(old.getFamilyId());row.setVersion(old.getVersion());gymContractTemplateMapper.updateById(row);return response(row);
 }
 @Transactional(rollbackFor=Exception.class)
 public GymContractTemplateRespVO version(Long id,GymContractTemplateSaveReqVO patch) {
  scope.requireHeadquarters();var initial=require(id,false);require(initial.getFamilyId(),true);
  var source=require(id,true);
  var latest=gymContractTemplateMapper.selectList(new LambdaQueryWrapper<GymContractTemplateDO>().eq(GymContractTemplateDO::getFamilyId,source.getFamilyId()).orderByDesc(GymContractTemplateDO::getVersion).last("FOR UPDATE")).getFirst();
  var req=BeanUtils.toBean(source,GymContractTemplateSaveReqVO.class);GymPatchUtils.merge(patch,req);req.setStatus("draft");validate(req);
  var row=BeanUtils.toBean(req,GymContractTemplateDO.class);row.setFamilyId(source.getFamilyId());row.setVersion(latest.getVersion()+1);gymContractTemplateMapper.insert(row);return response(row);
 }
 private void validate(GymContractTemplateSaveReqVO req) {
  var errors=validator.validate(req);if(!errors.isEmpty())throw new ConstraintViolationException(errors);
  var file=gymFileMapper.selectById(req.getFileId());
  if(file==null||!"contract".equals(file.getCategory())||!"application/pdf".equals(file.getMimeType()))throw exception(CONTRACT_TEMPLATE_FILE_INVALID);
  // Headquarters writes; the referenced PDF remains a private gym file and its immutable ID is frozen on publication.
  scope.check(file.getStoreId());
 }
 private GymContractTemplateRespVO response(GymContractTemplateDO row){return BeanUtils.toBean(row,GymContractTemplateRespVO.class);}
}
