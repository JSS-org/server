package cn.iocoder.yudao.module.gym.service.member;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.gym.service.common.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import java.util.stream.Collectors;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
@Service
public class GymMemberService {
    @Resource private GymMemberMapper gymMemberMapper;
    @Resource private GymStoreService stores;
    @Resource private GymDataScope scope;
    @Resource private Validator validator;
    @Resource private GymMemberStoreMapper gymMemberStoreMapper;
    @Resource private GymMemberCoachService coaches;
    public PageResult<GymMemberRespVO> page(GymBusinessPageReqVO req) {
        if (req.getStoreId()!=null) coaches.checkListStore(req.getStoreId());
        var range=GymDateRange.of(req);
        var q=new LambdaQueryWrapper<GymMemberDO>()
                .eq(req.getMemberId()!=null,GymMemberDO::getId,req.getMemberId())
                .eq(req.getStatus()!=null,GymMemberDO::getStatus,req.getStatus())
                .ge(range.from()!=null,GymMemberDO::getRegisteredAt,range.from())
                .lt(range.to()!=null,GymMemberDO::getRegisteredAt,range.to());
        String relation="SELECT 1 FROM gym_member_store ms WHERE ms.member_id=gym_member.id AND ms.deleted=0 AND ms.tenant_id={0} AND "+coaches.predicate("gym_member.id","ms.store_id");
        if(req.getStoreId()!=null) q.exists(relation+" AND ms.store_id={1}",TenantContextHolder.getRequiredTenantId(),req.getStoreId());
        else q.exists(relation,TenantContextHolder.getRequiredTenantId());
        if (req.getKeyword()!=null && !req.getKeyword().isBlank()) q.and(w->w.like(GymMemberDO::getName,req.getKeyword())
                .or().like(GymMemberDO::getPhone,req.getKeyword()).or().like(GymMemberDO::getMemberNo,req.getKeyword()));
        Instant now=Instant.now();
        String selected=req.getStoreId()!=null?"="+req.getStoreId():null;
        String cardScope=" AND (c.access_scope='all' OR EXISTS(SELECT 1 FROM gym_member_card_store cs WHERE cs.card_id=c.id AND cs.deleted=0 AND cs.tenant_id={0} AND "+coaches.predicate("gym_member.id","cs.store_id")+(selected==null?"":" AND cs.store_id"+selected)+"))";
        if(req.getCardExpiresWithinDays()!=null) q.exists("SELECT 1 FROM gym_member_card c WHERE c.member_id=gym_member.id AND c.tenant_id={0} AND c.deleted=0 AND c.status='active' AND c.expires_at>{1} AND c.expires_at<={2}"+cardScope,
                TenantContextHolder.getRequiredTenantId(),now,now.plus(req.getCardExpiresWithinDays(),ChronoUnit.DAYS));
        String pack="SELECT 1 FROM gym_course_pack p WHERE p.member_id=gym_member.id AND p.tenant_id={0} AND p.deleted=0 AND p.status='active' AND p.remaining_sessions>0 AND "+coaches.predicate("gym_member.id","p.store_id")+(selected==null?"":" AND p.store_id"+selected);
        if(req.getCourseExpiresWithinDays()!=null) q.exists(pack+" AND p.expires_at>{1} AND p.expires_at<={2}",TenantContextHolder.getRequiredTenantId(),now,now.plus(req.getCourseExpiresWithinDays(),ChronoUnit.DAYS));
        if(req.getHasPersonalTraining()!=null) {
            String active=pack+" AND p.starts_at<={1} AND (p.expires_at IS NULL OR p.expires_at>{1})";
            if(req.getHasPersonalTraining()) q.exists(active,TenantContextHolder.getRequiredTenantId(),now);
            else q.notExists(active,TenantContextHolder.getRequiredTenantId(),now);
        }
        q.orderByDesc(GymMemberDO::getId);
        var page=gymMemberMapper.selectPage(req,q);
        return new PageResult<>(page.getList().stream().map(this::response).toList(),page.getTotal());
    }
    public GymMemberDO requireMember(Long id, boolean lock) {
        var row=lock?gymMemberMapper.selectForUpdate(id):gymMemberMapper.selectById(id);
        if(row==null) throw exception(MEMBER_NOT_EXISTS);
        var ids=coaches.readableMemberStores(id);
        if(ids!=null && ids.isEmpty()) throw exception(STORE_NO_PERMISSION);
        return row;
    }
    public GymMemberRespVO get(Long id) { return response(requireMember(id,false)); }
    @Transactional(rollbackFor=Exception.class)
    public GymMemberRespVO create(GymMemberSaveReqVO req) {
        if(req.getGender()==null) req.setGender("unspecified");
        if(req.getStatus()==null) req.setStatus("active"); validate(req);
        var store=stores.requireStore(req.getStoreId(),false);
        if(!"active".equals(store.getStatus())) throw exception(MEMBER_STORE_INACTIVE);
        var row=BeanUtils.toBean(req,GymMemberDO.class);
        row.setMemberNo(UUID.randomUUID().toString()); row.setCustomerStage("lead"); row.setRegisteredAt(Instant.now());
        try { gymMemberMapper.insert(row); } catch(DuplicateKeyException e) { throw exception(MEMBER_PHONE_DUPLICATE); }
        String suffix="-"+String.format(Locale.ROOT,"%08d",row.getId()); String code=store.getCode();
        int prefixLength=Math.min(code.length(),64-suffix.length());
        if(prefixLength>0 && prefixLength<code.length() && Character.isHighSurrogate(code.charAt(prefixLength-1))) prefixLength--;
        row.setMemberNo(code.substring(0,prefixLength)+suffix); gymMemberMapper.updateById(row);
        var relation=new GymMemberStoreDO(); relation.setMemberId(row.getId()); relation.setStoreId(row.getStoreId()); relation.setJoinedAt(row.getRegisteredAt()); gymMemberStoreMapper.insert(relation);
        return response(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymMemberRespVO patch(Long id,GymMemberSaveReqVO patch) {
        var row=requireMember(id,true);
        scope.checkAll(memberships(id).stream().map(GymMemberStoreDO::getStoreId).toList());
        if(patch.getStoreId()!=null && !patch.getStoreId().equals(row.getStoreId())) throw exception(MEMBER_STORE_IMMUTABLE);
        var req=BeanUtils.toBean(row,GymMemberSaveReqVO.class); GymPatchUtils.merge(patch,req); validate(req);
        var update=BeanUtils.toBean(req,GymMemberDO.class); update.setId(id);
        try { gymMemberMapper.updateById(update); } catch(DuplicateKeyException e) { throw exception(MEMBER_PHONE_DUPLICATE); }
        return get(id);
    }
    private void validate(GymMemberSaveReqVO req) {
        var errors=validator.validate(req); if(!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
    private GymMemberRespVO response(GymMemberDO row) {
        var result=BeanUtils.toBean(row,GymMemberRespVO.class);
        var ids=coaches.readableMemberStores(row.getId());
        var visible=memberships(row.getId()).stream().filter(ms->ids==null||ids.contains(ms.getStoreId())).map(ms->{
            var item=new GymMemberRespVO.Store(); item.setId(ms.getStoreId().toString()); item.setName(coaches.memberStoreName(row.getId(),ms.getStoreId())); return item;
        }).toList();
        result.setStores(visible); result.setStoreIds(visible.stream().map(GymMemberRespVO.Store::getId).toList());
        result.setStoreNames(visible.stream().map(GymMemberRespVO.Store::getName).collect(Collectors.joining("、"))); return result;
    }
    public List<GymMemberStoreDO> memberships(Long memberId) {
        return gymMemberStoreMapper.selectList(new LambdaQueryWrapper<GymMemberStoreDO>().eq(GymMemberStoreDO::getMemberId,memberId).orderByAsc(GymMemberStoreDO::getId));
    }
    public void checkMemberStore(Long memberId,Long storeId) {
        coaches.checkMemberStore(memberId,storeId); requireMember(memberId,false);
        if(memberships(memberId).stream().noneMatch(ms->storeId.equals(ms.getStoreId()))) throw exception(STORE_NO_PERMISSION);
    }
    public Set<Long> readableMemberStores(Long memberId) {return coaches.readableMemberStores(memberId);}
    public void checkMemberScope(Long memberId,Long storeId) {coaches.checkMemberStore(memberId,storeId);}
    public String memberStoreName(Long memberId,Long storeId) {return coaches.memberStoreName(memberId,storeId);}
    public String memberPredicate(String memberColumn,String storeColumn) {return coaches.predicate(memberColumn,storeColumn);}
    public void checkMemberListStore(Long storeId) {coaches.checkListStore(storeId);}
}
