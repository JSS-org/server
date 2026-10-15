package cn.iocoder.yudao.module.gym.service.approval;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.GymApprovalTemplateDO;
import cn.iocoder.yudao.module.gym.dal.mysql.approval.GymApprovalTemplateMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymApprovalTemplateService {
    @Resource private GymApprovalTemplateMapper gymApprovalTemplateMapper;
    @Resource private GymEmployeeMapper gymEmployeeMapper;
    @Resource private GymDataScope scope;
    @Resource private cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService memberCoaches;
    @Resource private AdminUserService adminUserService;
    @Resource private Validator validator;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    public PageResult<GymApprovalTemplateRespVO> page(GymBusinessPageReqVO req) {
        var allowed=scope.readableStoreIds();
        if(allowed!=null&&allowed.isEmpty()) return new PageResult<>(List.of(),0L);
        var q=new LambdaQueryWrapper<GymApprovalTemplateDO>()
                .eq(allowed!=null,GymApprovalTemplateDO::getStatus,"published")
                .eq(req.getStatus()!=null,GymApprovalTemplateDO::getStatus,req.getStatus())
                .eq(req.getBusinessType()!=null,GymApprovalTemplateDO::getBusinessType,req.getBusinessType())
                .like(req.getKeyword()!=null&&!req.getKeyword().isBlank(),GymApprovalTemplateDO::getName,req.getKeyword())
                .orderByDesc(GymApprovalTemplateDO::getId);
        var page=gymApprovalTemplateMapper.selectPage(req,q);
        return new PageResult<>(page.getList().stream().map(this::response).toList(),page.getTotal());
    }
    public List<GymApprovalTemplateRespVO> options() {
        var allowed=scope.readableStoreIds();boolean personal=allowed!=null&&allowed.isEmpty(); if(personal&&memberCoaches.selfResponsibilities().isEmpty()) return List.of();
        return gymApprovalTemplateMapper.selectList(new LambdaQueryWrapper<GymApprovalTemplateDO>()
                .eq(personal,GymApprovalTemplateDO::getBusinessType,"price_change").eq(GymApprovalTemplateDO::getStatus,"published").orderByDesc(GymApprovalTemplateDO::getId)).stream().map(this::response).toList();
    }
    public GymApprovalTemplateDO require(Long id,boolean lock) {
        var row=lock?gymApprovalTemplateMapper.selectForUpdate(id):gymApprovalTemplateMapper.selectById(id);
        if(row==null) throw exception(APPROVAL_TEMPLATE_NOT_EXISTS);
        var allowed=scope.readableStoreIds();
        if(allowed!=null&&(allowed.isEmpty()||!"published".equals(row.getStatus()))) throw exception(STORE_NO_PERMISSION);
        return row;
    }
    /** SELF may use a published price-change workflow; template administration remains unchanged. */
    public GymApprovalTemplateDO requireForSubmission(Long id,boolean personal) {
        if(!personal)return require(id,true);
        if(memberCoaches.selfResponsibilities().isEmpty())throw exception(STORE_NO_PERMISSION);
        var row=gymApprovalTemplateMapper.selectForUpdate(id);
        if(row==null)throw exception(APPROVAL_TEMPLATE_NOT_EXISTS);
        if(!"published".equals(row.getStatus())||!"price_change".equals(row.getBusinessType()))throw exception(STORE_NO_PERMISSION);
        return row;
    }
    public GymApprovalTemplateRespVO get(Long id) { return response(require(id,false)); }
    @Transactional(rollbackFor=Exception.class)
    public GymApprovalTemplateRespVO create(GymApprovalTemplateSaveReqVO req) {
        scope.requireHeadquarters(); if(req.getStatus()==null) req.setStatus("draft"); validate(req);
        var row=row(req); row.setVersion(1); gymApprovalTemplateMapper.insert(row);
        row.setFamilyId(row.getId()); gymApprovalTemplateMapper.updateById(row); return response(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymApprovalTemplateRespVO patch(Long id,GymApprovalTemplateSaveReqVO patch) {
        scope.requireHeadquarters(); var old=require(id,true);
        if(!"draft".equals(old.getStatus())&&(patch.getName()!=null||patch.getBusinessType()!=null||patch.getSteps()!=null||"draft".equals(patch.getStatus())))
            throw exception(APPROVAL_TEMPLATE_IMMUTABLE);
        var req=request(old); GymPatchUtils.merge(patch,req); validate(req);
        var row=row(req); row.setId(id); row.setFamilyId(old.getFamilyId()); row.setVersion(old.getVersion());
        gymApprovalTemplateMapper.updateById(row); return response(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymApprovalTemplateRespVO version(Long id,GymApprovalTemplateSaveReqVO patch) {
        scope.requireHeadquarters(); var source=require(id,false); require(source.getFamilyId(),true);
        var latest=gymApprovalTemplateMapper.selectList(new LambdaQueryWrapper<GymApprovalTemplateDO>()
                .eq(GymApprovalTemplateDO::getFamilyId,source.getFamilyId()).orderByDesc(GymApprovalTemplateDO::getVersion).last("FOR UPDATE")).getFirst();
        var req=request(source); GymPatchUtils.merge(patch,req); req.setStatus("draft"); validate(req);
        var row=row(req); row.setFamilyId(source.getFamilyId()); row.setVersion(latest.getVersion()+1);
        gymApprovalTemplateMapper.insert(row); return response(row);
    }
    private void validate(GymApprovalTemplateSaveReqVO req) {
        var errors=validator.validate(req); if(!errors.isEmpty()) throw new ConstraintViolationException(errors);
        var ids=new HashSet<Long>(); int number=1;
        for(var step:req.getSteps()) {
            if(step.getStepNo()!=number++||!ids.add(step.getApproverEmployeeId())) throw exception(APPROVAL_STEPS_INVALID);
            var employee=gymEmployeeMapper.selectById(step.getApproverEmployeeId());
            if(employee==null) throw exception(APPROVAL_APPROVER_INVALID);
            // Drafts can retain historical employees; publishing must be actionable by a live account.
            if("published".equals(req.getStatus())) {
                var user=employee.getUserId()==null?null:adminUserService.getUser(employee.getUserId());
                if(!"active".equals(employee.getStatus())||user==null||!Integer.valueOf(0).equals(user.getStatus())
                        ||!permissionApi.hasAnyPermissions(user.getId(),"gym:approvals:decide")) throw exception(APPROVAL_APPROVER_INVALID);
            }
        }
    }
    public List<GymApprovalTemplateSaveReqVO.Step> steps(GymApprovalTemplateDO row) { return JsonUtils.parseArray(row.getSteps(),GymApprovalTemplateSaveReqVO.Step.class); }
    private GymApprovalTemplateSaveReqVO request(GymApprovalTemplateDO row) {
        var req=new GymApprovalTemplateSaveReqVO(); org.springframework.beans.BeanUtils.copyProperties(row,req,"steps"); req.setSteps(steps(row)); return req;
    }
    private GymApprovalTemplateDO row(GymApprovalTemplateSaveReqVO req) {
        var row=new GymApprovalTemplateDO(); org.springframework.beans.BeanUtils.copyProperties(req,row,"steps"); row.setSteps(JsonUtils.toJsonString(req.getSteps())); return row;
    }
    private GymApprovalTemplateRespVO response(GymApprovalTemplateDO row) {
        var result=new GymApprovalTemplateRespVO(); org.springframework.beans.BeanUtils.copyProperties(row,result,"id","familyId","steps");
        result.setId(row.getId().toString()); result.setFamilyId(row.getFamilyId().toString());
        result.setSteps(steps(row).stream().map(s->{
            var step=new GymApprovalTemplateRespVO.Step(); step.setStepNo(s.getStepNo()); step.setApproverEmployeeId(s.getApproverEmployeeId().toString());
            var employee=gymEmployeeMapper.selectById(s.getApproverEmployeeId()); step.setApproverName(employee==null?"已删除员工":employee.getName()); return step;
        }).toList()); return result;
    }
}
