package cn.iocoder.yudao.module.gym.service.course;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.course.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import cn.iocoder.yudao.module.gym.service.member.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.gym.service.common.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

@Service
public class GymCourseService {
    @Resource private GymCourseProductMapper gymCourseProductMapper;
    @Resource private GymCourseSessionMapper gymCourseSessionMapper;
    @Resource private GymCoursePackMapper gymCoursePackMapper;
    @Resource private GymBookingMapper gymBookingMapper;
    @Resource private GymCheckinMapper gymCheckinMapper;
    @Resource private GymLessonRecordMapper gymLessonRecordMapper;
    @Resource private GymEmployeeMapper gymEmployeeMapper;
    @Resource private cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper gymMemberMapper;
    @Resource private GymEmployeeStoreMapper gymEmployeeStoreMapper;
    @Resource private GymMemberCardMapper gymMemberCardMapper;
    @Resource private GymMemberStoreMapper gymMemberStoreMapper;
    @Resource private GymMemberService members;
    @Resource private GymMemberCoachService memberCoaches;
    @Resource private GymMemberRecordsService records;
    @Resource private GymCardService cards;
    @Resource private GymStoreService stores;
    @Resource private cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper gymStoreMapper;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock gymBusinessClock;
    @Resource private cn.iocoder.yudao.module.gym.service.contract.GymContractService contracts;
    @Resource private Validator validator;

    private void coursePageScope(GymBusinessPageReqVO req) { if(req.getStoreId()!=null) memberCoaches.checkListStore(req.getStoreId()); }
    private void memberPageScope(GymBusinessPageReqVO req) {
        if(req.getStoreId()!=null)members.checkMemberListStore(req.getStoreId());
        if(req.getMemberId()!=null)members.requireMember(req.getMemberId(),false);
    }
    private GymCourseSessionDO memberSession(Long id,Long memberId,boolean lock) {
        var row=lock?gymCourseSessionMapper.selectForUpdate(id):gymCourseSessionMapper.selectById(id);
        if(row==null)throw exception(COURSE_SESSION_NOT_EXISTS);members.checkMemberScope(memberId,row.getStoreId());return row;
    }
    private boolean activeMemberStore(Long storeId) {var store=gymStoreMapper.selectById(storeId);return store!=null&&"active".equals(store.getStatus());}
    private boolean empty(Set<Long> ids) { return ids!=null&&ids.isEmpty(); }
    private boolean keyword(GymBusinessPageReqVO req) { return req.getKeyword()!=null&&!req.getKeyword().isBlank(); }
    private Instant normalized(Instant value) { return value==null?null:value.truncatedTo(ChronoUnit.MICROS); }
    private void validate(Object req) { var errors=validator.validate(req); if(!errors.isEmpty()) throw new ConstraintViolationException(errors); }
    private GymEmployeeDO requireCoach(Long id,Long storeId) {
        var employee=gymEmployeeMapper.selectForUpdate(id);
        var relation=gymEmployeeStoreMapper.selectList(new LambdaQueryWrapper<GymEmployeeStoreDO>().eq(GymEmployeeStoreDO::getEmployeeId,id)
                .eq(GymEmployeeStoreDO::getStoreId,storeId).last("FOR UPDATE"));
        if(employee==null||!"active".equals(employee.getStatus())||relation.isEmpty()
                ||employee.getHiredOn().isAfter(gymBusinessClock.now().atZone(GymDateRange.ZONE).toLocalDate())
                ||relation.getFirst().getJoinedOn().isAfter(gymBusinessClock.now().atZone(GymDateRange.ZONE).toLocalDate())) throw exception(COURSE_COACH_INVALID);
        return employee;
    }
    public PageResult<Map<String,Object>> coaches(GymBusinessPageReqVO req) {
        coursePageScope(req);
        var q=new LambdaQueryWrapper<GymEmployeeDO>().eq(GymEmployeeDO::getStatus,"active").like(keyword(req),GymEmployeeDO::getName,req.getKeyword());
        String relation="SELECT 1 FROM gym_employee_store es WHERE es.employee_id=gym_employee.id AND es.deleted=0 AND es.tenant_id={0} AND "+memberCoaches.coachPredicate("gym_employee.id","es.store_id");
        if(req.getStoreId()!=null) q.exists(relation+" AND es.store_id={1}",TenantContextHolder.getRequiredTenantId(),req.getStoreId());
        else q.exists(relation,TenantContextHolder.getRequiredTenantId());
        q.orderByAsc(GymEmployeeDO::getId); var page=gymEmployeeMapper.selectPage(req,q);
        return new PageResult<>(page.getList().stream().map(e->{Map<String,Object> row=new LinkedHashMap<>(); row.put("id",e.getId().toString()); row.put("name",e.getName()); return row;}).toList(),page.getTotal());
    }
    public PageResult<GymCourseProductRespVO> products(GymBusinessPageReqVO req) {
        coursePageScope(req); var range=GymDateRange.of(req);
        var q=new LambdaQueryWrapper<GymCourseProductDO>().apply(memberCoaches.coachPredicate("gym_course_product.coach_id","gym_course_product.store_id"))
                .eq(req.getStoreId()!=null,GymCourseProductDO::getStoreId,req.getStoreId()).eq(req.getCoachId()!=null,GymCourseProductDO::getCoachId,req.getCoachId())
                .eq(req.getKind()!=null,GymCourseProductDO::getKind,req.getKind()).eq(req.getStatus()!=null,GymCourseProductDO::getStatus,req.getStatus())
                .like(keyword(req),GymCourseProductDO::getName,req.getKeyword())
                .ge(range.from()!=null,GymCourseProductDO::getCreateTime,range.from()==null?null:range.from().atZone(GymDateRange.ZONE).toLocalDateTime())
                .lt(range.to()!=null,GymCourseProductDO::getCreateTime,range.to()==null?null:range.to().atZone(GymDateRange.ZONE).toLocalDateTime())
                .orderByDesc(GymCourseProductDO::getId);
        var page=gymCourseProductMapper.selectPage(req,q); return new PageResult<>(page.getList().stream().map(this::productResponse).toList(),page.getTotal());
    }
    public GymCourseProductDO requireProduct(Long id,boolean lock) {
        var row=lock?gymCourseProductMapper.selectForUpdate(id):gymCourseProductMapper.selectById(id);
        if(row==null) throw exception(COURSE_PRODUCT_NOT_EXISTS); scope.check(row.getStoreId()); return row;
    }
    public GymCourseProductDO readableProductForPriceRequest(Long id,boolean lock) {
        var row=lock?gymCourseProductMapper.selectForUpdate(id):gymCourseProductMapper.selectById(id);
        if(row==null)throw exception(COURSE_PRODUCT_NOT_EXISTS);memberCoaches.checkCoachScope(row.getCoachId(),row.getStoreId());return row;
    }
    public GymCourseProductRespVO product(Long id) { return productResponse(readableProductForPriceRequest(id,false)); }
    @Transactional(rollbackFor=Exception.class)
    public GymCourseProductRespVO createProduct(GymCourseProductSaveReqVO req) {
        if(req.getKind()==null) req.setKind("private"); if(req.getStatus()==null) req.setStatus("active"); validateProduct(req);
        if(!"active".equals(req.getStatus())) requireCoach(req.getCoachId(),req.getStoreId());
        var row=BeanUtils.toBean(req,GymCourseProductDO.class); gymCourseProductMapper.insert(row); return productResponse(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymCourseProductRespVO patchProduct(Long id,GymCourseProductSaveReqVO patch) {
        var initial=requireProduct(id,false); gymEmployeeMapper.selectForUpdate(initial.getCoachId());
        var row=requireProduct(id,true);
        if((patch.getStoreId()!=null&&!patch.getStoreId().equals(row.getStoreId()))||(patch.getCoachId()!=null&&!patch.getCoachId().equals(row.getCoachId()))) throw exception(COURSE_PRODUCT_INVALID);
        if(patch.getPrice()!=null&&patch.getPrice().compareTo(row.getPrice())!=0) throw exception(COURSE_PRICE_APPROVAL_REQUIRED);
        var req=BeanUtils.toBean(row,GymCourseProductSaveReqVO.class); GymPatchUtils.merge(patch,req); validateProduct(req);
        var update=BeanUtils.toBean(req,GymCourseProductDO.class); update.setId(id); gymCourseProductMapper.updateById(update); return product(id);
    }
    /** Internal final approval effect; apply the requested change only if its original-price snapshot is still current. */
    @Transactional(rollbackFor=Exception.class)
    public void applyApprovedPrice(Long id,BigDecimal original,BigDecimal requested) {
        var row=requireProduct(id,true);
        if(!"active".equals(row.getStatus())) throw exception(COURSE_PRODUCT_INVALID);
        if(requested==null||requested.signum()<0||requested.scale()>2||requested.precision()-requested.scale()>10
                ||original==null||row.getPrice().compareTo(original)!=0) throw exception(COURSE_PRICE_APPROVAL_REQUIRED);
        row.setPrice(requested); gymCourseProductMapper.updateById(row);
    }
    private void validateProduct(GymCourseProductSaveReqVO req) { validate(req); var store=stores.requireStore(req.getStoreId(),false);
        if("active".equals(req.getStatus())) {
            if(!"active".equals(store.getStatus())) throw exception(COURSE_PRODUCT_INVALID);
            requireCoach(req.getCoachId(),req.getStoreId());
        }
    }
    private GymCourseProductRespVO productResponse(GymCourseProductDO row) {
        var result=BeanUtils.toBean(row,GymCourseProductRespVO.class); result.setStoreName(memberCoaches.coachStoreName(row.getCoachId(),row.getStoreId())); result.setCoachName(coachName(row.getCoachId())); return result;
    }
    private String coachName(Long id) { var coach=gymEmployeeMapper.selectById(id); return coach==null?null:coach.getName(); }

    public PageResult<GymCourseSessionRespVO> sessions(GymBusinessPageReqVO req) {
        coursePageScope(req); var range=GymDateRange.of(req);
        var q=new LambdaQueryWrapper<GymCourseSessionDO>().apply(memberCoaches.coachPredicate("gym_course_session.coach_id","gym_course_session.store_id"))
                .eq(req.getStoreId()!=null,GymCourseSessionDO::getStoreId,req.getStoreId()).eq(req.getCoachId()!=null,GymCourseSessionDO::getCoachId,req.getCoachId())
                .eq(req.getSessionId()!=null,GymCourseSessionDO::getId,req.getSessionId()).eq(req.getStatus()!=null,GymCourseSessionDO::getStatus,req.getStatus())
                .ge(range.from()!=null,GymCourseSessionDO::getStartsAt,range.from()).lt(range.to()!=null,GymCourseSessionDO::getStartsAt,range.to())
                .orderByAsc(GymCourseSessionDO::getStartsAt).orderByAsc(GymCourseSessionDO::getId);
        if(req.getKind()!=null&&!"private".equals(req.getKind())) q.apply("1=0");
        if(keyword(req)) q.like(GymCourseSessionDO::getRoom,req.getKeyword());
        var page=gymCourseSessionMapper.selectPage(req,q); return new PageResult<>(page.getList().stream().map(this::sessionResponse).toList(),page.getTotal());
    }
    private GymCourseSessionDO requireSession(Long id,boolean lock) {
        var row=lock?gymCourseSessionMapper.selectForUpdate(id):gymCourseSessionMapper.selectById(id);
        if(row==null) throw exception(COURSE_SESSION_NOT_EXISTS); scope.check(row.getStoreId()); return row;
    }
    public GymCourseSessionRespVO session(Long id) { var row=gymCourseSessionMapper.selectById(id);if(row==null)throw exception(COURSE_SESSION_NOT_EXISTS);memberCoaches.checkCoachScope(row.getCoachId(),row.getStoreId());return sessionResponse(row); }
    @Transactional(rollbackFor=Exception.class)
    public GymCourseSessionRespVO createSession(GymCourseSessionSaveReqVO req) {
        if(req.getStatus()==null) req.setStatus("scheduled"); if(req.getCapacity()==null) req.setCapacity(1);
        if(!"scheduled".equals(req.getStatus())) throw exception(COURSE_SESSION_INVALID);
        validateSession(req,null); var row=BeanUtils.toBean(req,GymCourseSessionDO.class); row.setReservedCount(0); gymCourseSessionMapper.insert(row); return sessionResponse(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymCourseSessionRespVO patchSession(Long id,GymCourseSessionSaveReqVO patch) {
        var initial=requireSession(id,false);
        // Keep the coach -> session lock order, but cancellation must also work after departure/transfer.
        gymEmployeeMapper.selectForUpdate(initial.getCoachId()); var row=requireSession(id,true);
        if(!"scheduled".equals(row.getStatus())) throw exception(COURSE_SESSION_INVALID);
        if((patch.getStoreId()!=null&&!patch.getStoreId().equals(row.getStoreId()))||(patch.getCoachId()!=null&&!patch.getCoachId().equals(row.getCoachId()))
                ||(patch.getCourseProductId()!=null&&!patch.getCourseProductId().equals(row.getCourseProductId()))) throw exception(COURSE_SESSION_INVALID);
        var req=BeanUtils.toBean(row,GymCourseSessionSaveReqVO.class); GymPatchUtils.merge(patch,req); req.setStartsAt(normalized(req.getStartsAt())); req.setEndsAt(normalized(req.getEndsAt()));
        if(row.getReservedCount()>0&&(!row.getStartsAt().equals(req.getStartsAt())||!row.getEndsAt().equals(req.getEndsAt()))) throw exception(COURSE_SESSION_INVALID);
        if("cancelled".equals(req.getStatus())) {
            if(!gymBookingMapper.selectList(new LambdaQueryWrapper<GymBookingDO>().eq(GymBookingDO::getSessionId,id).eq(GymBookingDO::getStatus,"attended").last("FOR UPDATE")).isEmpty()) throw exception(COURSE_SESSION_INVALID);
            var bookings=gymBookingMapper.selectList(new LambdaQueryWrapper<GymBookingDO>().eq(GymBookingDO::getSessionId,id).eq(GymBookingDO::getStatus,"confirmed").last("FOR UPDATE"));
            for(var booking:bookings) cancelBookingLocked(booking,row);
            row.setStatus("cancelled"); gymCourseSessionMapper.updateById(row); return sessionResponse(row);
        }
        validateSession(req,id); var update=BeanUtils.toBean(req,GymCourseSessionDO.class); update.setId(id); update.setReservedCount(row.getReservedCount()); gymCourseSessionMapper.updateById(update); return session(id);
    }
    private void validateSession(GymCourseSessionSaveReqVO req,Long excluding) {
        req.setStartsAt(normalized(req.getStartsAt())); req.setEndsAt(normalized(req.getEndsAt())); validate(req);
        requireCoach(req.getCoachId(),req.getStoreId());
        var product=requireProduct(req.getCourseProductId(),true);
        if(!"active".equals(product.getStatus())||!req.getStoreId().equals(product.getStoreId())||!req.getCoachId().equals(product.getCoachId())
                ||!req.getEndsAt().isAfter(req.getStartsAt())||!Duration.between(req.getStartsAt(),req.getEndsAt()).equals(Duration.ofMinutes(product.getDurationMinutes()))) throw exception(COURSE_SESSION_INVALID);
        stores.requireStore(req.getStoreId(),false); requireCoach(req.getCoachId(),req.getStoreId());
        var overlapping=gymCourseSessionMapper.selectList(new LambdaQueryWrapper<GymCourseSessionDO>().eq(GymCourseSessionDO::getCoachId,req.getCoachId())
                .ne(GymCourseSessionDO::getStatus,"cancelled").ne(excluding!=null,GymCourseSessionDO::getId,excluding)
                .lt(GymCourseSessionDO::getStartsAt,req.getEndsAt()).gt(GymCourseSessionDO::getEndsAt,req.getStartsAt()).last("FOR UPDATE"));
        if(!overlapping.isEmpty()) throw exception(COURSE_SESSION_CONFLICT);
    }
    private GymCourseSessionRespVO sessionResponse(GymCourseSessionDO row) {
        var result=BeanUtils.toBean(row,GymCourseSessionRespVO.class); result.setStoreName(memberCoaches.coachStoreName(row.getCoachId(),row.getStoreId()));
        var product=gymCourseProductMapper.selectById(row.getCourseProductId()); result.setCourseName(product==null?null:product.getName()); result.setCoachName(coachName(row.getCoachId())); return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public GymCoursePackDO issuePack(Long memberId,Long productId,Instant startsAt,Instant expiresAt,String sourceType,String sourceId) {
        var member=members.requireMember(memberId,true); records.checkPurchase(memberId);
        var product=requireProduct(productId,true); startsAt=normalized(startsAt); expiresAt=normalized(expiresAt);
        if(!"active".equals(member.getStatus())||!"active".equals(product.getStatus())||startsAt==null||sourceType==null||sourceType.isBlank()
                ||sourceId==null||sourceId.isBlank()||(expiresAt!=null&&!expiresAt.isAfter(startsAt))) throw exception(COURSE_PACK_INVALID);
        var old=gymCoursePackMapper.selectList(new LambdaQueryWrapper<GymCoursePackDO>().eq(GymCoursePackDO::getSourceType,sourceType).eq(GymCoursePackDO::getSourceId,sourceId).last("FOR UPDATE"));
        if(!old.isEmpty()) { var row=old.getFirst(); if(!memberId.equals(row.getMemberId())||!productId.equals(row.getCourseProductId())
                ||!startsAt.equals(row.getStartsAt())||!Objects.equals(expiresAt,row.getExpiresAt())) throw exception(COURSE_PACK_INVALID); return row; }
        var row=new GymCoursePackDO(); row.setMemberId(memberId); row.setStoreId(product.getStoreId()); row.setCourseProductId(productId); row.setAssignedCoachId(product.getCoachId());
        row.setName(product.getName()); row.setStartsAt(startsAt); row.setExpiresAt(expiresAt); row.setTotalSessions(product.getSessionCount()); row.setRemainingSessions(product.getSessionCount());
        row.setReservedSessions(0); row.setStatus("active"); row.setPriceSnapshot(product.getPrice()); row.setSourceType(sourceType); row.setSourceId(sourceId); gymCoursePackMapper.insert(row);
        var membership=gymMemberStoreMapper.selectList(new LambdaQueryWrapper<GymMemberStoreDO>().eq(GymMemberStoreDO::getMemberId,memberId).eq(GymMemberStoreDO::getStoreId,row.getStoreId()).last("FOR UPDATE"));
        if(membership.isEmpty()) { var ms=new GymMemberStoreDO(); ms.setMemberId(memberId); ms.setStoreId(row.getStoreId()); ms.setJoinedAt(gymBusinessClock.now()); gymMemberStoreMapper.insert(ms); }
        var update=new cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO(); update.setId(memberId); update.setCustomerStage("member"); gymMemberMapper.updateById(update);
        contracts.issueForEntitlement("course-packs",row.getId()); return row;
    }
    public List<GymCoursePackRespVO> allPacks(Long memberId,GymBusinessPageReqVO req) {
        members.requireMember(memberId,false); if(req.getStoreId()!=null)members.checkMemberScope(memberId,req.getStoreId());var ids=members.readableMemberStores(memberId); if(empty(ids)) return List.of();
        var rows=gymCoursePackMapper.selectList(new LambdaQueryWrapper<GymCoursePackDO>().eq(GymCoursePackDO::getMemberId,memberId)
                .in(ids!=null,GymCoursePackDO::getStoreId,ids).eq(req.getStoreId()!=null,GymCoursePackDO::getStoreId,req.getStoreId()).orderByDesc(GymCoursePackDO::getId));
        return rows.stream().map(this::packResponse).filter(p->req.getStatus()==null||req.getStatus().equals(p.getStatus())).toList();
    }
    public PageResult<GymCoursePackRespVO> packs(Long memberId,GymBusinessPageReqVO req) {
        var all=allPacks(memberId,req); int start=(int)Math.min(all.size(),((long)req.getPageNo()-1)*req.getPageSize()),end=Math.min(all.size(),start+req.getPageSize()); return new PageResult<>(all.subList(start,end),(long)all.size());
    }
    private GymCoursePackRespVO packResponse(GymCoursePackDO row) {
        var result=BeanUtils.toBean(row,GymCoursePackRespVO.class); result.setStoreName(members.memberStoreName(row.getMemberId(),row.getStoreId()));
        result.setMemberName(members.requireMember(row.getMemberId(),false).getName()); result.setAssignedCoachName(coachName(row.getAssignedCoachId()));
        if("active".equals(row.getStatus())&&row.getExpiresAt()!=null&&!row.getExpiresAt().isAfter(gymBusinessClock.now())) result.setStatus("expired"); return result;
    }
    private GymCoursePackDO requirePack(Long id,Long memberId,GymCourseSessionDO session,boolean lock) {
        var row=lock?gymCoursePackMapper.selectForUpdate(id):gymCoursePackMapper.selectById(id); if(row==null) throw exception(COURSE_PACK_NOT_EXISTS);
        members.checkMemberScope(memberId,row.getStoreId());
        if(!memberId.equals(row.getMemberId())||!session.getStoreId().equals(row.getStoreId())||!session.getCourseProductId().equals(row.getCourseProductId())
                ||!session.getCoachId().equals(row.getAssignedCoachId())||!"active".equals(row.getStatus())||row.getStartsAt().isAfter(session.getStartsAt())
                ||(row.getExpiresAt()!=null&&!row.getExpiresAt().isAfter(session.getEndsAt()))) throw exception(COURSE_PACK_INVALID);
        return row;
    }

    @Transactional(rollbackFor=Exception.class)
    public GymBookingRespVO createBooking(GymBookingSaveReqVO req) {
        validate(req); if((req.getMemberCardId()==null)==(req.getMemberCoursePackId()==null)||req.getStatus()!=null) throw exception(BOOKING_INVALID);
        // Authorize before locking. Serialize against employee changes and session cancellation in coach-first order.
        var initialMember=members.requireMember(req.getMemberId(),false);
        var initialSession=memberSession(req.getSessionId(),initialMember.getId(),false);
        memberCoaches.checkCoachScope(initialSession.getCoachId(),initialSession.getStoreId());
        requireCoach(initialSession.getCoachId(),initialSession.getStoreId());
        var member=members.requireMember(req.getMemberId(),true); var session=memberSession(req.getSessionId(),member.getId(),true);
        memberCoaches.checkCoachScope(session.getCoachId(),session.getStoreId());
        members.checkMemberStore(member.getId(),session.getStoreId()); records.checkEntry(member.getId());
        if(!"active".equals(member.getStatus())||!activeMemberStore(session.getStoreId())||!"scheduled".equals(session.getStatus())) throw exception(BOOKING_INVALID);
        var existing=gymBookingMapper.selectList(new LambdaQueryWrapper<GymBookingDO>().eq(GymBookingDO::getSessionId,session.getId())
                .eq(GymBookingDO::getMemberId,member.getId()).last("FOR UPDATE"));
        GymBookingDO booking=existing.isEmpty()?new GymBookingDO():existing.getFirst();
        if("confirmed".equals(booking.getStatus())) {
            if(!Objects.equals(booking.getMemberCardId(),req.getMemberCardId())||!Objects.equals(booking.getMemberCoursePackId(),req.getMemberCoursePackId())) throw exception(BOOKING_INVALID);
            return bookingResponse(booking);
        }
        if("attended".equals(booking.getStatus())||!session.getStartsAt().isAfter(gymBusinessClock.now())) throw exception(BOOKING_INVALID);
        if(session.getReservedCount()>=session.getCapacity()) throw exception(BOOKING_CAPACITY_FULL);
        if(req.getMemberCoursePackId()!=null) {
            var pack=requirePack(req.getMemberCoursePackId(),member.getId(),session,true);
            if(pack.getRemainingSessions()-pack.getReservedSessions()<1) throw exception(COURSE_PACK_INSUFFICIENT);
            pack.setReservedSessions(pack.getReservedSessions()+1); gymCoursePackMapper.updateById(pack);
        } else {
            var card=cards.requireCard(req.getMemberCardId(),member.getId(),session.getStoreId(),session.getStartsAt(),session.getEndsAt(),true);
            if(card.getRemainingUses()!=null&&card.getRemainingUses()-card.getReservedUses()<1) throw exception(CARD_RIGHT_INSUFFICIENT);
            card.setReservedUses(card.getReservedUses()+1); gymMemberCardMapper.updateById(card);
        }
        booking.setStoreId(session.getStoreId()); booking.setSessionId(session.getId()); booking.setMemberId(member.getId()); booking.setMemberCardId(req.getMemberCardId());
        booking.setMemberCoursePackId(req.getMemberCoursePackId()); booking.setNote(req.getNote()); booking.setStatus("confirmed"); booking.setBookedAt(normalized(gymBusinessClock.now()));
        booking.setCancelledAt(null); booking.setCheckinId(null);
        if(booking.getId()==null) gymBookingMapper.insert(booking); else gymBookingMapper.updateById(booking);
        session.setReservedCount(session.getReservedCount()+1); gymCourseSessionMapper.updateById(session); return bookingResponse(booking);
    }
    private GymBookingDO requireBooking(Long id,boolean lock) {
        var row=lock?gymBookingMapper.selectForUpdate(id):gymBookingMapper.selectById(id); if(row==null) throw exception(BOOKING_NOT_EXISTS);
        members.checkMemberScope(row.getMemberId(),row.getStoreId()); members.requireMember(row.getMemberId(),false); return row;
    }
    public GymBookingRespVO booking(Long id) { return bookingResponse(requireBooking(id,false)); }
    @Transactional(rollbackFor=Exception.class)
    public GymBookingRespVO patchBooking(Long id,GymBookingSaveReqVO patch) {
        var first=requireBooking(id,false); var session=memberSession(first.getSessionId(),first.getMemberId(),true); var booking=requireBooking(id,true);
        if((patch.getSessionId()!=null&&!patch.getSessionId().equals(booking.getSessionId()))||(patch.getMemberId()!=null&&!patch.getMemberId().equals(booking.getMemberId()))
                ||(patch.getMemberCardId()!=null&&!patch.getMemberCardId().equals(booking.getMemberCardId()))||(patch.getMemberCoursePackId()!=null&&!patch.getMemberCoursePackId().equals(booking.getMemberCoursePackId()))
                ||(patch.getStatus()!=null&&!"cancelled".equals(patch.getStatus()))) throw exception(BOOKING_INVALID);
        if("attended".equals(booking.getStatus())) throw exception(BOOKING_INVALID);
        if(patch.getNote()!=null) { if(patch.getNote().length()>2000) throw exception(BOOKING_INVALID); booking.setNote(patch.getNote()); }
        if("cancelled".equals(patch.getStatus())&&"confirmed".equals(booking.getStatus())) cancelBookingLocked(booking,session);
        else gymBookingMapper.updateById(booking);
        return bookingResponse(booking);
    }
    private void cancelBookingLocked(GymBookingDO booking,GymCourseSessionDO session) {
        if(booking.getMemberCoursePackId()!=null) {
            var pack=gymCoursePackMapper.selectForUpdate(booking.getMemberCoursePackId()); if(pack==null||pack.getReservedSessions()<1) throw exception(COURSE_PACK_INVALID);
            pack.setReservedSessions(pack.getReservedSessions()-1); gymCoursePackMapper.updateById(pack);
        } else {
            var card=gymMemberCardMapper.selectForUpdate(booking.getMemberCardId()); if(card==null||card.getReservedUses()<1) throw exception(CARD_RIGHT_INVALID);
            card.setReservedUses(card.getReservedUses()-1); gymMemberCardMapper.updateById(card);
        }
        if(session.getReservedCount()<1) throw exception(COURSE_SESSION_INVALID);
        booking.setStatus("cancelled"); booking.setCancelledAt(normalized(gymBusinessClock.now())); gymBookingMapper.updateById(booking);
        session.setReservedCount(session.getReservedCount()-1); gymCourseSessionMapper.updateById(session);
    }
    public PageResult<GymBookingRespVO> bookings(GymBusinessPageReqVO req) {
        memberPageScope(req); var range=GymDateRange.of(req);
        var q=new LambdaQueryWrapper<GymBookingDO>().apply(members.memberPredicate("gym_booking.member_id","gym_booking.store_id")).eq(req.getStoreId()!=null,GymBookingDO::getStoreId,req.getStoreId())
                .eq(req.getMemberId()!=null,GymBookingDO::getMemberId,req.getMemberId()).eq(req.getSessionId()!=null,GymBookingDO::getSessionId,req.getSessionId())
                .eq(req.getStatus()!=null,GymBookingDO::getStatus,req.getStatus()).like(keyword(req),GymBookingDO::getNote,req.getKeyword()).orderByDesc(GymBookingDO::getId);
        String relation="SELECT 1 FROM gym_course_session s WHERE s.id=gym_booking.session_id AND s.deleted=0 AND s.tenant_id={0}";
        if(range.from()!=null) q.exists(relation+" AND s.starts_at>={1}",TenantContextHolder.getRequiredTenantId(),range.from());
        if(range.to()!=null) q.exists(relation+" AND s.starts_at<{1}",TenantContextHolder.getRequiredTenantId(),range.to());
        if(req.getCoachId()!=null) q.exists(relation+" AND s.coach_id={1}",TenantContextHolder.getRequiredTenantId(),req.getCoachId());
        var page=gymBookingMapper.selectPage(req,q); return new PageResult<>(page.getList().stream().map(this::bookingResponse).toList(),page.getTotal());
    }
    private GymBookingRespVO bookingResponse(GymBookingDO row) {
        var result=BeanUtils.toBean(row,GymBookingRespVO.class); result.setStoreName(members.memberStoreName(row.getMemberId(),row.getStoreId())); result.setMemberName(members.requireMember(row.getMemberId(),false).getName());
        var session=gymCourseSessionMapper.selectById(row.getSessionId()); if(session!=null) { result.setSessionStartsAt(session.getStartsAt()); result.setCoachName(coachName(session.getCoachId()));
            var product=gymCourseProductMapper.selectById(session.getCourseProductId()); result.setCourseName(product==null?null:product.getName()); } return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public GymCheckinRespVO checkIn(Long bookingId) {
        var first=requireBooking(bookingId,false); var session=memberSession(first.getSessionId(),first.getMemberId(),true); var booking=requireBooking(bookingId,true);
        if(booking.getCheckinId()!=null) return checkinResponse(gymCheckinMapper.selectById(booking.getCheckinId()));
        if(!"confirmed".equals(booking.getStatus())||!"active".equals(members.requireMember(booking.getMemberId(),false).getStatus())
                ||!activeMemberStore(booking.getStoreId())) throw exception(BOOKING_INVALID);
        records.checkEntry(booking.getMemberId());
        if(booking.getMemberCoursePackId()!=null) {
            var pack=requirePack(booking.getMemberCoursePackId(),booking.getMemberId(),session,false);
            if(pack.getStartsAt().isAfter(gymBusinessClock.now())||(pack.getExpiresAt()!=null&&!pack.getExpiresAt().isAfter(gymBusinessClock.now()))) throw exception(COURSE_PACK_INVALID);
        } else cards.requireCard(booking.getMemberCardId(),booking.getMemberId(),booking.getStoreId(),gymBusinessClock.now(),session.getEndsAt(),false);
        if(!gymBusinessClock.now().atZone(GymDateRange.ZONE).toLocalDate().equals(session.getStartsAt().atZone(GymDateRange.ZONE).toLocalDate())) throw exception(LESSON_TIME_INVALID);
        var row=new GymCheckinDO(); row.setStoreId(booking.getStoreId()); row.setMemberId(booking.getMemberId()); row.setBookingId(bookingId);
        row.setEnteredAt(normalized(gymBusinessClock.now())); row.setMethod("manual"); row.setVisitorType("member"); row.setStatus("succeeded"); gymCheckinMapper.insert(row);
        booking.setCheckinId(row.getId()); gymBookingMapper.updateById(booking); return checkinResponse(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public GymLessonRecordRespVO confirmLesson(Long bookingId) {
        var first=requireBooking(bookingId,false); var session=memberSession(first.getSessionId(),first.getMemberId(),true); var booking=requireBooking(bookingId,true);
        var previous=gymLessonRecordMapper.selectList(new LambdaQueryWrapper<GymLessonRecordDO>().eq(GymLessonRecordDO::getBookingId,bookingId).last("FOR UPDATE"));
        if(!previous.isEmpty()) return lessonResponse(previous.getFirst());
        if(!"confirmed".equals(booking.getStatus())) throw exception(BOOKING_INVALID);
        if(booking.getCheckinId()==null) throw exception(LESSON_CHECKIN_REQUIRED);
        var checkin=gymCheckinMapper.selectById(booking.getCheckinId());
        if(checkin==null||!booking.getMemberId().equals(checkin.getMemberId())||!booking.getStoreId().equals(checkin.getStoreId())
                ||!"succeeded".equals(checkin.getStatus())) throw exception(LESSON_CHECKIN_REQUIRED);
        Instant now=normalized(gymBusinessClock.now());
        if(now.isBefore(session.getStartsAt())||checkin.getEnteredAt().isAfter(now)
                ||!checkin.getEnteredAt().atZone(GymDateRange.ZONE).toLocalDate().equals(session.getStartsAt().atZone(GymDateRange.ZONE).toLocalDate())) throw exception(LESSON_TIME_INVALID);
        records.checkEntry(booking.getMemberId());
        if(booking.getMemberCoursePackId()!=null) {
            var pack=requirePack(booking.getMemberCoursePackId(),booking.getMemberId(),session,true);
            if(pack.getRemainingSessions()<1||pack.getReservedSessions()<1) throw exception(COURSE_PACK_INSUFFICIENT);
            pack.setRemainingSessions(pack.getRemainingSessions()-1); pack.setReservedSessions(pack.getReservedSessions()-1); gymCoursePackMapper.updateById(pack);
        } else {
            var card=cards.requireCard(booking.getMemberCardId(),booking.getMemberId(),booking.getStoreId(),session.getStartsAt(),session.getEndsAt(),true);
            if(card.getReservedUses()<1||(card.getRemainingUses()!=null&&card.getRemainingUses()<1)) throw exception(CARD_RIGHT_INSUFFICIENT);
            card.setReservedUses(card.getReservedUses()-1); if(card.getRemainingUses()!=null) card.setRemainingUses(card.getRemainingUses()-1); gymMemberCardMapper.updateById(card);
        }
        var lesson=new GymLessonRecordDO(); lesson.setStoreId(booking.getStoreId()); lesson.setMemberId(booking.getMemberId()); lesson.setCoachId(session.getCoachId());
        lesson.setBookingId(bookingId); lesson.setCheckinId(checkin.getId()); lesson.setMemberCardId(booking.getMemberCardId()); lesson.setMemberCoursePackId(booking.getMemberCoursePackId());
        lesson.setSessionUnits(1); lesson.setConfirmedAt(now); lesson.setMethod("manual"); lesson.setStatus("confirmed"); gymLessonRecordMapper.insert(lesson);
        booking.setStatus("attended"); gymBookingMapper.updateById(booking);
        session.setStatus("completed"); gymCourseSessionMapper.updateById(session); return lessonResponse(lesson);
    }
    public PageResult<GymCheckinRespVO> checkins(GymBusinessPageReqVO req) {
        memberPageScope(req); var range=GymDateRange.of(req);
        var q=new LambdaQueryWrapper<GymCheckinDO>().apply(members.memberPredicate("gym_checkin.member_id","gym_checkin.store_id")).eq(req.getStoreId()!=null,GymCheckinDO::getStoreId,req.getStoreId())
                .eq(req.getMemberId()!=null,GymCheckinDO::getMemberId,req.getMemberId()).eq(req.getStatus()!=null,GymCheckinDO::getStatus,req.getStatus())
                .ge(range.from()!=null,GymCheckinDO::getEnteredAt,range.from()).lt(range.to()!=null,GymCheckinDO::getEnteredAt,range.to()).orderByDesc(GymCheckinDO::getId);
        var page=gymCheckinMapper.selectPage(req,q); return new PageResult<>(page.getList().stream().map(this::checkinResponse).toList(),page.getTotal());
    }
    private GymCheckinRespVO checkinResponse(GymCheckinDO row) {
        var result=BeanUtils.toBean(row,GymCheckinRespVO.class); result.setStoreName(members.memberStoreName(row.getMemberId(),row.getStoreId())); result.setMemberName(members.requireMember(row.getMemberId(),false).getName()); return result;
    }
    public Map<String,Long> checkinSummary(GymBusinessPageReqVO req) {
        memberPageScope(req);
        LocalDate day=req.getDate()==null?gymBusinessClock.now().atZone(GymDateRange.ZONE).toLocalDate():req.getDate();
        Instant from=day.atStartOfDay(GymDateRange.ZONE).toInstant(),to=day.plusDays(1).atStartOfDay(GymDateRange.ZONE).toInstant();
        var today=gymCheckinMapper.selectList(new LambdaQueryWrapper<GymCheckinDO>().apply(members.memberPredicate("gym_checkin.member_id","gym_checkin.store_id"))
                .eq(req.getStoreId()!=null,GymCheckinDO::getStoreId,req.getStoreId()).eq(req.getMemberId()!=null,GymCheckinDO::getMemberId,req.getMemberId()).ge(GymCheckinDO::getEnteredAt,from).lt(GymCheckinDO::getEnteredAt,to));
        long yesterday=gymCheckinMapper.selectCount(new LambdaQueryWrapper<GymCheckinDO>().apply(members.memberPredicate("gym_checkin.member_id","gym_checkin.store_id"))
                .eq(req.getStoreId()!=null,GymCheckinDO::getStoreId,req.getStoreId()).eq(req.getMemberId()!=null,GymCheckinDO::getMemberId,req.getMemberId()).ge(GymCheckinDO::getEnteredAt,day.minusDays(1).atStartOfDay(GymDateRange.ZONE).toInstant()).lt(GymCheckinDO::getEnteredAt,from));
        return Map.of("entries",(long)today.size(),"members",today.stream().map(GymCheckinDO::getMemberId).filter(Objects::nonNull).distinct().count(),
                "visitors",today.stream().filter(r->"visitor".equals(r.getVisitorType())).count(),"yesterdayEntries",yesterday);
    }
    public PageResult<GymLessonRecordRespVO> lessons(GymBusinessPageReqVO req) {
        memberPageScope(req); var range=GymDateRange.of(req);
        var q=new LambdaQueryWrapper<GymLessonRecordDO>().apply(members.memberPredicate("gym_lesson_record.member_id","gym_lesson_record.store_id")).eq(req.getStoreId()!=null,GymLessonRecordDO::getStoreId,req.getStoreId())
                .eq(req.getMemberId()!=null,GymLessonRecordDO::getMemberId,req.getMemberId()).eq(req.getCoachId()!=null,GymLessonRecordDO::getCoachId,req.getCoachId())
                .ge(range.from()!=null,GymLessonRecordDO::getConfirmedAt,range.from()).lt(range.to()!=null,GymLessonRecordDO::getConfirmedAt,range.to()).orderByDesc(GymLessonRecordDO::getId);
        var page=gymLessonRecordMapper.selectPage(req,q); return new PageResult<>(page.getList().stream().map(this::lessonResponse).toList(),page.getTotal());
    }
    private GymLessonRecordRespVO lessonResponse(GymLessonRecordDO row) {
        var result=BeanUtils.toBean(row,GymLessonRecordRespVO.class); result.setStoreName(members.memberStoreName(row.getMemberId(),row.getStoreId())); result.setMemberName(members.requireMember(row.getMemberId(),false).getName()); result.setCoachName(coachName(row.getCoachId())); return result;
    }
}
