package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.contract.vo.GymContractCreateReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkRecordsVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCoursePackMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.contract.GymContractService;
import cn.iocoder.yudao.module.gym.service.member.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
public class GymMobileWorkRecordsService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymMemberRecordsService measurements;
    @Resource private GymTrainingPlanService plans;
    @Resource private GymContractService contracts;
    @Resource private GymContractMapper contractMapper;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymDataScope scope;
    @Resource private Validator validator;

    public PageResult<Measurement> measurements(Page req) {
        prepare(req, Set.of());
        var page = measurements.measurements(legacy(req));
        return new PageResult<>(
                page.getList().stream().map(this::measurement).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Measurement createMeasurement(MeasurementCreate req, String key) {
        validate(req);
        access.member(req.getMemberId(), req.getStoreId());
        return idempotency.execute(
                "work-measurement-create",
                key,
                req,
                Measurement.class,
                () -> measurement(measurements.createMeasurement(req)));
    }

    public PageResult<TrainingPlan> plans(Page req) {
        prepare(req, Set.of("active", "archived"));
        var page = plans.page(legacy(req));
        return new PageResult<>(page.getList().stream().map(this::plan).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TrainingPlan createPlan(PlanCreate req, String key) {
        validate(req);
        access.member(req.getMemberId(), req.getStoreId());
        return idempotency.execute(
                "work-plan-create", key, req, TrainingPlan.class, () -> plan(plans.create(req)));
    }

    public PageResult<Contract> contracts(Page req) {
        prepare(req, Set.of("unsigned", "signing", "signed", "voided"));
        var q =
                new LambdaQueryWrapper<GymContractDO>()
                        .eq(GymContractDO::getMemberId, req.getMemberId())
                        .eq(GymContractDO::getStoreId, req.getStoreId())
                        .orderByDesc(GymContractDO::getIssuedAt, GymContractDO::getId);
        if (req.getStatus() != null) {
            switch (req.getStatus()) {
                case "voided" -> q.eq(GymContractDO::getStatus, "cancelled");
                case "signing" ->
                        q.eq(GymContractDO::getStatus, "unsigned")
                                .eq(GymContractDO::getSigningStatus, "signing");
                case "unsigned" ->
                        q.eq(GymContractDO::getStatus, "unsigned")
                                .and(
                                        w ->
                                                w.isNull(GymContractDO::getSigningStatus)
                                                        .or()
                                                        .ne(
                                                                GymContractDO::getSigningStatus,
                                                                "signing"));
                default -> q.eq(GymContractDO::getStatus, req.getStatus());
            }
        }
        var page = contractMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::contract).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Contract createContract(ContractCreate req, String key) {
        validate(req);
        access.member(req.getMemberId(), req.getStoreId());
        scope.check(req.getStoreId());
        // Ownership must match the explicit target even when replaying an old request.
        String source;
        if ("member_card".equals(req.getSourceType())) {
            var card = cards.selectById(req.getSourceId());
            if (card == null
                    || !req.getMemberId().equals(card.getMemberId())
                    || !req.getStoreId().equals(card.getIssuedStoreId()))
                throw exception(CONTRACT_SOURCE_INVALID);
            source = "member-cards";
        } else {
            var pack = packs.selectById(req.getSourceId());
            if (pack == null
                    || !req.getMemberId().equals(pack.getMemberId())
                    || !req.getStoreId().equals(pack.getStoreId()))
                throw exception(CONTRACT_SOURCE_INVALID);
            source = "course-packs";
        }
        return idempotency.execute(
                "work-contract-create",
                key,
                req,
                Contract.class,
                () -> {
                    var legacy = new GymContractCreateReqVO();
                    legacy.setSourceType(source);
                    legacy.setSourceId(req.getSourceId());
                    legacy.setTemplateId(req.getTemplateId());
                    var created = contracts.create(legacy);
                    return contract(contracts.require(Long.valueOf(created.getId()), false));
                });
    }

    private void prepare(Page req, Set<String> statuses) {
        validate(req);
        if (req.getStatus() != null && !statuses.contains(req.getStatus()))
            throw exception(MOBILE_REQUEST_INVALID);
        access.member(req.getMemberId(), req.getStoreId());
    }

    private GymBusinessPageReqVO legacy(Page req) {
        var q = new GymBusinessPageReqVO();
        q.setPageNo(req.getPageNo());
        q.setPageSize(req.getPageSize());
        q.setMemberId(req.getMemberId());
        q.setStoreId(req.getStoreId());
        q.setStatus(req.getStatus());
        return q;
    }

    private Measurement measurement(GymMeasurementRespVO r) {
        return new Measurement(
                r.getId(),
                r.getStoreId(),
                time(r.getMeasuredAt()),
                r.getHeightCm(),
                r.getWeightKg(),
                r.getBodyFatPercent(),
                r.getMuscleMassKg(),
                r.getNote(),
                r.getReportFileId(),
                null);
    }

    private TrainingPlan plan(GymTrainingPlanRespVO r) {
        return new TrainingPlan(
                r.getId(),
                r.getStoreId(),
                r.getTitle(),
                r.getContent(),
                r.getFileId(),
                r.getStatus(),
                null,
                time(r.getCreateTime()),
                time(r.getUpdateTime()));
    }

    private Contract contract(GymContractDO r) {
        var snapshot = JsonUtils.parseObject(r.getTemplateSnapshot(), Map.class);
        Object version = snapshot == null ? null : snapshot.get("version");
        String status =
                "cancelled".equals(r.getStatus())
                        ? "voided"
                        : "unsigned".equals(r.getStatus()) && "signing".equals(r.getSigningStatus())
                                ? "signing"
                                : r.getStatus();
        return new Contract(
                r.getId().toString(),
                r.getName(),
                r.getStoreId().toString(),
                status,
                time(r.getIssuedAt()),
                time(r.getSignedAt()),
                id(r.getUnsignedFileId()),
                id(r.getSignedFileId()),
                version == null ? null : version.toString());
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private static String time(LocalDateTime value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
