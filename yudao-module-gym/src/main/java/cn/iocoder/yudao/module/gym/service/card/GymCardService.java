package cn.iocoder.yudao.module.gym.service.card;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.admin.card.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.service.member.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymCardService {
    @Resource private GymCardTemplateMapper gymCardTemplateMapper;
    @Resource private GymMemberCardMapper gymMemberCardMapper;
    @Resource private GymMemberStoreMapper gymMemberStoreMapper;
    @Resource private GymMemberCardStoreMapper gymMemberCardStoreMapper;
    @Resource private GymMemberMapper gymMemberMapper;
    @Resource private GymStoreMapper gymStoreMapper;
    @Resource private GymMemberService members;
    @Resource private GymMemberRecordsService records;
    @Resource private GymStoreService stores;
    @Resource private GymDataScope scope;
    @Resource private Validator validator;
    @Resource private GymBusinessMutex gymBusinessMutex;
    @Resource private cn.iocoder.yudao.module.gym.service.contract.GymContractService contracts;
    public PageResult<GymCardTemplateRespVO> templates(GymBusinessPageReqVO req) {
        if(req.getStoreId()!=null) stores.requireStore(req.getStoreId(),false);
        var q=new LambdaQueryWrapper<GymCardTemplateDO>()
                .eq(req.getStatus()!=null,GymCardTemplateDO::getStatus,req.getStatus())
                .like(req.getKeyword()!=null&&!req.getKeyword().isBlank(),GymCardTemplateDO::getName,req.getKeyword())
                .orderByDesc(GymCardTemplateDO::getId);
        // Scope is evaluated before pagination so selectors receive correct totals and every page.
        var ids=scope.readableStoreIds();
        var all=gymCardTemplateMapper.selectList(q).stream().filter(t -> visible(t,req.getStoreId(),ids)).toList();
        int start=(int)Math.min(all.size(),((long)req.getPageNo()-1)*req.getPageSize()), end=Math.min(all.size(),start+req.getPageSize());
        return new PageResult<>(all.subList(start,end).stream().map(this::templateResponse).toList(),(long)all.size());
    }
    private boolean visible(GymCardTemplateDO row,Long storeId,Set<Long> allowed) {
        if(allowed!=null&&(allowed.isEmpty()||!"published".equals(row.getStatus()))) return false;
        if("all".equals(row.getAccessScope())) return true;
        var ids=templateStoreIds(row);
        if(storeId!=null) return ids.contains(storeId);
        return allowed==null||ids.stream().anyMatch(allowed::contains);
    }
    public GymCardTemplateDO requireTemplate(Long id,boolean lock) {
        var row=lock?gymCardTemplateMapper.selectForUpdate(id):gymCardTemplateMapper.selectById(id);
        if(row==null) throw exception(CARD_TEMPLATE_NOT_EXISTS);
        if(!visible(row,null,scope.readableStoreIds())) throw exception(STORE_NO_PERMISSION); return row;
    }
    public GymCardTemplateRespVO template(Long id) { return templateResponse(requireTemplate(id,false)); }
    @Transactional(rollbackFor=Exception.class)
    public GymCardTemplateRespVO create(GymCardTemplateSaveReqVO req) {
        scope.requireHeadquarters(); defaults(req); validate(req);
        var row=templateRow(req); row.setVersion(1); gymCardTemplateMapper.insert(row);
        row.setFamilyId(row.getId()); gymCardTemplateMapper.updateById(row); return templateResponse(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymCardTemplateRespVO patch(Long id,GymCardTemplateSaveReqVO patch) {
        scope.requireHeadquarters(); var old=requireTemplate(id,true); var req=templateRequest(old);
        if(!"draft".equals(old.getStatus()) && hasContent(patch)) throw exception(CARD_TEMPLATE_IMMUTABLE);
        if(!"draft".equals(old.getStatus()) && "draft".equals(patch.getStatus())) throw exception(CARD_TEMPLATE_IMMUTABLE);
        GymPatchUtils.merge(patch,req); normalizeKind(req); validate(req);
        var row=templateRow(req); row.setId(id); row.setFamilyId(old.getFamilyId()); row.setVersion(old.getVersion());
        gymCardTemplateMapper.updateById(row); return templateResponse(row);
    }
    private boolean hasContent(GymCardTemplateSaveReqVO patch) {
        return patch.getName()!=null||patch.getKind()!=null||patch.getDurationDays()!=null||patch.getIncludedUses()!=null
                ||patch.getPrice()!=null||patch.getMemberPrefix()!=null||patch.getAccessScope()!=null||patch.getStoreIds()!=null;
    }
    @Transactional(rollbackFor=Exception.class)
    public GymCardTemplateRespVO version(Long id,GymCardTemplateSaveReqVO patch) {
        scope.requireHeadquarters(); var source=requireTemplate(id,false); requireTemplate(source.getFamilyId(),true);
        var latest=gymCardTemplateMapper.selectList(new LambdaQueryWrapper<GymCardTemplateDO>().eq(GymCardTemplateDO::getFamilyId,source.getFamilyId())
                .orderByDesc(GymCardTemplateDO::getVersion).last("FOR UPDATE")).getFirst();
        var req=templateRequest(source); GymPatchUtils.merge(patch,req); req.setStatus("draft"); normalizeKind(req); validate(req);
        var row=templateRow(req); row.setFamilyId(source.getFamilyId()); row.setVersion(latest.getVersion()+1); gymCardTemplateMapper.insert(row); return templateResponse(row);
    }
    private void defaults(GymCardTemplateSaveReqVO req) {
        if(req.getStatus()==null) req.setStatus("draft"); if(req.getAccessScope()==null) req.setAccessScope("all");
        if(req.getKind()==null) req.setKind("period"); normalizeKind(req);
    }
    private void normalizeKind(GymCardTemplateSaveReqVO req) {
        if("period".equals(req.getKind())) req.setIncludedUses(null);
        if("count".equals(req.getKind())) req.setDurationDays(null);
        if("all".equals(req.getAccessScope())) req.setStoreIds(List.of());
    }
    private void validate(GymCardTemplateSaveReqVO req) {
        var errors=validator.validate(req); if(!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if(("period".equals(req.getKind())&&req.getDurationDays()==null)||("count".equals(req.getKind())&&req.getIncludedUses()==null)) throw exception(CARD_TEMPLATE_INVALID);
        if("selected".equals(req.getAccessScope())) {
            if(req.getStoreIds()==null||req.getStoreIds().isEmpty()||new HashSet<>(req.getStoreIds()).size()!=req.getStoreIds().size()) throw exception(CARD_TEMPLATE_INVALID);
            req.getStoreIds().forEach(s->stores.requireStore(s,false));
        }
    }
    public List<Long> templateStoreIds(GymCardTemplateDO row) { return JsonUtils.parseArray(row.getStoreIds(),Long.class); }
    private GymCardTemplateSaveReqVO templateRequest(GymCardTemplateDO row) {
        var req=new GymCardTemplateSaveReqVO(); org.springframework.beans.BeanUtils.copyProperties(row,req,"storeIds"); req.setStoreIds(templateStoreIds(row)); return req;
    }
    private GymCardTemplateDO templateRow(GymCardTemplateSaveReqVO req) {
        var row=new GymCardTemplateDO(); org.springframework.beans.BeanUtils.copyProperties(req,row,"storeIds"); row.setStoreIds(JsonUtils.toJsonString(req.getStoreIds()==null?List.of():req.getStoreIds())); return row;
    }
    public GymCardTemplateRespVO templateResponse(GymCardTemplateDO row) {
        var result=new GymCardTemplateRespVO(); org.springframework.beans.BeanUtils.copyProperties(row,result,"storeIds","id","familyId");
        result.setId(row.getId().toString()); result.setFamilyId(row.getFamilyId().toString());
        var allowed=scope.readableStoreIds(); result.setStoreIds(templateStoreIds(row).stream().filter(s->allowed==null||allowed.contains(s)).map(String::valueOf).toList()); return result;
    }
    /** Internal issuance, called only after approved opening or a verified payment; no public arbitrary rights endpoint. */
    @Transactional(rollbackFor=Exception.class)
    public GymMemberCardDO issue(Long memberId,Long storeId,Long templateId,Instant startsAt,String sourceType,String sourceId) {
        return issueInternal(memberId,storeId,templateId,startsAt,sourceType,sourceId,null);
    }
    /** Only a persisted, server-created purchase snapshot may use this internal fulfillment path. */
    @Transactional(rollbackFor=Exception.class)
    public GymMemberCardDO issuePurchased(Long memberId,Long storeId,GymCardTemplateDO purchased,Instant startsAt,String sourceId) {
        if(purchased==null||purchased.getId()==null||!cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId().equals(purchased.getTenantId())) throw exception(CARD_RIGHT_INVALID);
        return issueInternal(memberId,storeId,purchased.getId(),startsAt,"order-item",sourceId,purchased);
    }
    private GymMemberCardDO issueInternal(Long memberId,Long storeId,Long templateId,Instant startsAt,String sourceType,String sourceId,GymCardTemplateDO purchased) {
        gymBusinessMutex.lockStoreEntitlements();
        if(startsAt!=null) startsAt=startsAt.truncatedTo(ChronoUnit.MICROS);
        stores.requireStore(storeId,false); var member=members.requireMember(memberId,true); records.checkPurchase(memberId);
        if(!"active".equals(member.getStatus())||startsAt==null||sourceType==null||sourceId==null||sourceType.isBlank()||sourceId.isBlank()) throw exception(CARD_RIGHT_INVALID);
        var old=gymMemberCardMapper.selectList(new LambdaQueryWrapper<GymMemberCardDO>().eq(GymMemberCardDO::getSourceType,sourceType)
                .eq(GymMemberCardDO::getSourceId,sourceId).last("FOR UPDATE"));
        if(!old.isEmpty()) {
            var row=old.getFirst(); if(!Objects.equals(row.getMemberId(),memberId)||!Objects.equals(row.getTemplateId(),templateId)
                    ||!Objects.equals(row.getIssuedStoreId(),storeId)||!Objects.equals(row.getStartsAt(),startsAt)) throw exception(CARD_SOURCE_CONFLICT); return row;
        }
        var template=purchased==null?requireTemplate(templateId,true):purchased;
        if((purchased==null&&!"published".equals(template.getStatus()))||!visible(template,storeId,scope.readableStoreIds())) throw exception(CARD_RIGHT_INVALID);
        var entitled="all".equals(template.getAccessScope())?gymStoreMapper.selectList(new LambdaQueryWrapper<GymStoreDO>().eq(GymStoreDO::getStatus,"active").last("FOR UPDATE"))
                .stream().map(GymStoreDO::getId).toList():templateStoreIds(template);
        var row=new GymMemberCardDO(); row.setMemberId(memberId); row.setIssuedStoreId(storeId); row.setTemplateId(templateId);
        row.setCardNo(UUID.randomUUID().toString()); row.setName(template.getName()); row.setKind(template.getKind());
        row.setDisplayPrefix(template.getMemberPrefix()==null?stores.get(storeId).getName():template.getMemberPrefix());
        row.setAccessScope(template.getAccessScope()); row.setStoreIds(JsonUtils.toJsonString(entitled)); row.setStartsAt(startsAt);
        row.setExpiresAt(template.getDurationDays()==null?null:startsAt.plus(template.getDurationDays(),ChronoUnit.DAYS));
        row.setTotalUses(template.getIncludedUses()); row.setRemainingUses(template.getIncludedUses()); row.setReservedUses(0); row.setStatus("active");
        // Snapshot raw template including its full authorized store list, independent from response masking.
        var snapshot=cn.hutool.core.bean.BeanUtil.beanToMap(templateRequest(template));
        snapshot.put("id",template.getId().toString()); snapshot.put("familyId",template.getFamilyId().toString()); snapshot.put("version",template.getVersion());
        row.setTemplateSnapshot(JsonUtils.toJsonString(snapshot)); row.setSourceType(sourceType); row.setSourceId(sourceId);
        gymMemberCardMapper.insert(row); String suffix="-C"+String.format(Locale.ROOT,"%08d",row.getId()); String code=stores.get(storeId).getCode();
        int prefixLength=Math.min(code.length(),64-suffix.length());
        if(prefixLength>0 && prefixLength<code.length() && Character.isHighSurrogate(code.charAt(prefixLength-1))) prefixLength--;
        row.setCardNo(code.substring(0,prefixLength)+suffix); gymMemberCardMapper.updateById(row);
        for(Long store:entitled) {
            var link=new GymMemberCardStoreDO(); link.setCardId(row.getId()); link.setStoreId(store); gymMemberCardStoreMapper.insert(link);
            addMembership(memberId,store);
        }
        var update=new GymMemberDO(); update.setId(memberId); update.setCustomerStage("member"); gymMemberMapper.updateById(update); contracts.issueForEntitlement("member-cards",row.getId()); return row;
    }
    private void addMembership(Long memberId,Long storeId) {
        var existing=gymMemberStoreMapper.selectList(new LambdaQueryWrapper<GymMemberStoreDO>().eq(GymMemberStoreDO::getMemberId,memberId)
                .eq(GymMemberStoreDO::getStoreId,storeId).last("FOR UPDATE"));
        if(existing.isEmpty()) { var row=new GymMemberStoreDO(); row.setMemberId(memberId); row.setStoreId(storeId); row.setJoinedAt(Instant.now()); gymMemberStoreMapper.insert(row); }
    }
    public PageResult<GymMemberCardRespVO> cards(Long memberId,GymBusinessPageReqVO req) {
        var all=allCards(memberId,req);
        int start=(int)Math.min(all.size(),((long)req.getPageNo()-1)*req.getPageSize()),end=Math.min(all.size(),start+req.getPageSize());
        return new PageResult<>(all.subList(start,end),(long)all.size());
    }
    public List<GymMemberCardRespVO> allCards(Long memberId,GymBusinessPageReqVO req) {
        members.requireMember(memberId,false); if(req.getStoreId()!=null) members.checkMemberScope(memberId,req.getStoreId());
        var ids=members.readableMemberStores(memberId);
        var all=gymMemberCardMapper.selectList(new LambdaQueryWrapper<GymMemberCardDO>().eq(GymMemberCardDO::getMemberId,memberId)
                .orderByDesc(GymMemberCardDO::getId)).stream().filter(c->{ var stores=JsonUtils.parseArray(c.getStoreIds(),Long.class);
                    return (req.getStoreId()==null||"all".equals(c.getAccessScope())||stores.contains(req.getStoreId()))
                            &&(ids==null||"all".equals(c.getAccessScope())||stores.stream().anyMatch(ids::contains)); }).map(this::cardResponse)
                .filter(c->req.getStatus()==null||req.getStatus().equals(c.getStatus())).toList();
        return all;
    }
    public GymMemberCardRespVO cardResponse(GymMemberCardDO row) {
        var result=new GymMemberCardRespVO(); org.springframework.beans.BeanUtils.copyProperties(row,result,"storeIds","id","memberId","issuedStoreId","templateId");
        result.setId(row.getId().toString()); result.setMemberId(row.getMemberId().toString()); result.setIssuedStoreId(row.getIssuedStoreId().toString()); result.setTemplateId(row.getTemplateId().toString());
        var allowed=members.readableMemberStores(row.getMemberId()); result.setStoreIds(JsonUtils.parseArray(row.getStoreIds(),Long.class).stream().filter(s->allowed==null||allowed.contains(s)).map(String::valueOf).toList());
        if("active".equals(row.getStatus())&&row.getExpiresAt()!=null&&!row.getExpiresAt().isAfter(Instant.now())) result.setStatus("expired"); return result;
    }
    public GymMemberCardDO requireCard(Long id,Long memberId,Long storeId,Instant from,Instant to,boolean lock) {
        members.checkMemberScope(memberId,storeId); members.requireMember(memberId,false);
        var row=lock?gymMemberCardMapper.selectForUpdate(id):gymMemberCardMapper.selectById(id);
        if(row==null) throw exception(CARD_NOT_EXISTS);
        if(!memberId.equals(row.getMemberId())||!"active".equals(row.getStatus())||row.getStartsAt().isAfter(from)
                ||(row.getExpiresAt()!=null&&!row.getExpiresAt().isAfter(to))
                ||(!"all".equals(row.getAccessScope())&&!JsonUtils.parseArray(row.getStoreIds(),Long.class).contains(storeId))) throw exception(CARD_RIGHT_INVALID);
        return row;
    }
}
