package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordPageReqVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class GymMobileRecordsService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMeasurementMapper measurements;
    @Resource private GymTrainingPlanMapper plans;
    @Resource private GymContractMapper contracts;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymLessonRecordMapper lessons;
    @Resource private Validator validator;

    private void validate(GymMobileRecordPageReqVO req, Set<String> statuses) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        req.validateFilters(statuses);
    }

    public PageResult<Measurement> measurements(GymMobileRecordPageReqVO req) {
        Long member = identities.requireMemberId();
        validate(req, Set.of());
        var q =
                new LambdaQueryWrapper<GymMeasurementDO>()
                        .eq(GymMeasurementDO::getMemberId, member)
                        .eq(
                                req.getStoreId() != null,
                                GymMeasurementDO::getStoreId,
                                req.getStoreId())
                        .ge(req.from() != null, GymMeasurementDO::getMeasuredAt, req.from())
                        .lt(req.to() != null, GymMeasurementDO::getMeasuredAt, req.to())
                        .orderByDesc(GymMeasurementDO::getMeasuredAt, GymMeasurementDO::getId);
        var page = measurements.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::measurementResponse).toList(), page.getTotal());
    }

    public Measurement measurement(Long id) {
        Long member = identities.requireMemberId();
        var row =
                measurements.selectOne(
                        new LambdaQueryWrapper<GymMeasurementDO>()
                                .eq(GymMeasurementDO::getId, id)
                                .eq(GymMeasurementDO::getMemberId, member));
        if (row == null) throw exception(MEASUREMENT_NOT_EXISTS);
        return measurementResponse(row);
    }

    public PageResult<TrainingPlan> plans(GymMobileRecordPageReqVO req) {
        Long member = identities.requireMemberId();
        validate(req, Set.of("active", "archived"));
        var q =
                new LambdaQueryWrapper<GymTrainingPlanDO>()
                        .eq(GymTrainingPlanDO::getMemberId, member)
                        .eq(
                                req.getStoreId() != null,
                                GymTrainingPlanDO::getStoreId,
                                req.getStoreId())
                        .eq(req.getStatus() != null, GymTrainingPlanDO::getStatus, req.getStatus())
                        .orderByDesc(GymTrainingPlanDO::getCreateTime, GymTrainingPlanDO::getId);
        var page = plans.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::planResponse).toList(), page.getTotal());
    }

    public TrainingPlan plan(Long id) {
        Long member = identities.requireMemberId();
        var row =
                plans.selectOne(
                        new LambdaQueryWrapper<GymTrainingPlanDO>()
                                .eq(GymTrainingPlanDO::getId, id)
                                .eq(GymTrainingPlanDO::getMemberId, member));
        if (row == null) throw exception(TRAINING_PLAN_NOT_EXISTS);
        return planResponse(row);
    }

    public PageResult<Contract> contracts(GymMobileRecordPageReqVO req) {
        Long member = identities.requireMemberId();
        validate(req, Set.of("unsigned", "signing", "signed", "voided"));
        var q =
                new LambdaQueryWrapper<GymContractDO>()
                        .eq(GymContractDO::getMemberId, member)
                        .eq(req.getStoreId() != null, GymContractDO::getStoreId, req.getStoreId());
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
        q.orderByDesc(GymContractDO::getIssuedAt, GymContractDO::getId);
        var page = contracts.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::contractResponse).toList(), page.getTotal());
    }

    public Contract contract(Long id) {
        Long member = identities.requireMemberId();
        var row =
                contracts.selectOne(
                        new LambdaQueryWrapper<GymContractDO>()
                                .eq(GymContractDO::getId, id)
                                .eq(GymContractDO::getMemberId, member));
        if (row == null) throw exception(CONTRACT_NOT_EXISTS);
        return contractResponse(row);
    }

    public PageResult<Checkin> checkins(GymMobileRecordPageReqVO req) {
        Long member = identities.requireMemberId();
        validate(req, Set.of());
        var q =
                new LambdaQueryWrapper<GymCheckinDO>()
                        .eq(GymCheckinDO::getMemberId, member)
                        .eq(GymCheckinDO::getStatus, "succeeded")
                        .eq(req.getStoreId() != null, GymCheckinDO::getStoreId, req.getStoreId())
                        .ge(req.from() != null, GymCheckinDO::getEnteredAt, req.from())
                        .lt(req.to() != null, GymCheckinDO::getEnteredAt, req.to())
                        .orderByDesc(GymCheckinDO::getEnteredAt, GymCheckinDO::getId);
        var page = checkins.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r ->
                                        new Checkin(
                                                id(r.getId()),
                                                id(r.getStoreId()),
                                                time(r.getEnteredAt()),
                                                time(r.getExitedAt()),
                                                r.getMethod(),
                                                id(r.getBookingId())))
                        .toList(),
                page.getTotal());
    }

    public FitnessSummary summary(Long storeId) {
        Long member = identities.requireMemberId();
        var latest =
                measurements.selectList(
                        new LambdaQueryWrapper<GymMeasurementDO>()
                                .eq(GymMeasurementDO::getMemberId, member)
                                .eq(storeId != null, GymMeasurementDO::getStoreId, storeId)
                                .orderByDesc(
                                        GymMeasurementDO::getMeasuredAt, GymMeasurementDO::getId)
                                .last("LIMIT 1"));
        long planCount =
                plans.selectCount(
                        new LambdaQueryWrapper<GymTrainingPlanDO>()
                                .eq(GymTrainingPlanDO::getMemberId, member)
                                .eq(GymTrainingPlanDO::getStatus, "active")
                                .eq(storeId != null, GymTrainingPlanDO::getStoreId, storeId));
        long checkinCount =
                checkins.selectCount(
                        new LambdaQueryWrapper<GymCheckinDO>()
                                .eq(GymCheckinDO::getMemberId, member)
                                .eq(GymCheckinDO::getStatus, "succeeded")
                                .eq(storeId != null, GymCheckinDO::getStoreId, storeId));
        List<Object> counts =
                lessons.selectObjs(
                        new QueryWrapper<GymLessonRecordDO>()
                                .select("COALESCE(SUM(session_units),0)")
                                .eq("member_id", member)
                                .eq("status", "confirmed")
                                .eq(storeId != null, "store_id", storeId));
        long lessonCount = counts.isEmpty() ? 0 : ((Number) counts.getFirst()).longValue();
        return new FitnessSummary(
                latest.isEmpty() ? null : measurementResponse(latest.getFirst()),
                planCount,
                checkinCount,
                lessonCount);
    }

    private Measurement measurementResponse(GymMeasurementDO r) {
        return new Measurement(
                id(r.getId()),
                id(r.getStoreId()),
                time(r.getMeasuredAt()),
                r.getHeightCm(),
                r.getWeightKg(),
                r.getBodyFatPercent(),
                r.getMuscleMassKg(),
                r.getNote(),
                id(r.getReportFileId()),
                null);
    }

    private TrainingPlan planResponse(GymTrainingPlanDO r) {
        return new TrainingPlan(
                id(r.getId()),
                id(r.getStoreId()),
                r.getTitle(),
                r.getContent(),
                id(r.getFileId()),
                r.getStatus(),
                null,
                time(r.getCreateTime()),
                time(r.getUpdateTime()));
    }

    private Contract contractResponse(GymContractDO r) {
        var snapshot = JsonUtils.parseObject(r.getTemplateSnapshot(), Map.class);
        Object version = snapshot == null ? null : snapshot.get("version");
        String status =
                "cancelled".equals(r.getStatus())
                        ? "voided"
                        : "unsigned".equals(r.getStatus()) && "signing".equals(r.getSigningStatus())
                                ? "signing"
                                : r.getStatus();
        return new Contract(
                id(r.getId()),
                r.getName(),
                id(r.getStoreId()),
                status,
                time(r.getIssuedAt()),
                time(r.getSignedAt()),
                id(r.getUnsignedFileId()),
                id(r.getSignedFileId()),
                version == null ? null : version.toString());
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
