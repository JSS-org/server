package cn.iocoder.yudao.module.gym.service.risk;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.dal.dataobject.risk.*;
import cn.iocoder.yudao.module.gym.dal.mysql.device.GymDeviceMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.risk.*;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessMutex;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;
import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Internal sink only. Caller must authenticate the source and derive risk from real evidence. */
@Service
public class GymRiskAlertPublisher {
    public record Event(
            @NotNull @Positive Long deviceId,
            @Positive Long memberId,
            @NotNull @Pattern(regexp = "multiple_entry|tailgating|blacklist|overnight|debt")
                    String type,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:_.-]{0,127}") String sourceEventKey,
            @NotNull Instant occurredAt,
            @NotNull @Size(max = 10) List<@NotNull @Positive Long> evidenceFileIds) {}

    @Resource private GymRiskAlertMapper alerts;
    @Resource private GymDeviceMapper devices;
    @Resource private GymMemberMapper members;
    @Resource private GymFileMapper files;
    @Resource private GymBusinessMutex mutex;
    @Resource private Validator validator;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long publish(Event event) {
        var errors = validator.validate(event);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if (new HashSet<>(event.evidenceFileIds()).size() != event.evidenceFileIds().size())
            throw exception(RISK_SOURCE_INVALID);
        var canonical =
                new Event(
                        event.deviceId(),
                        event.memberId(),
                        event.type(),
                        event.sourceEventKey(),
                        event.occurredAt().truncatedTo(ChronoUnit.MICROS),
                        event.evidenceFileIds().stream().sorted().toList());
        mutex.lockStoreEntitlements();
        String snapshot = JsonUtils.toJsonString(canonical);
        var old =
                alerts.selectOne(
                        new LambdaQueryWrapper<GymRiskAlertDO>()
                                .eq(GymRiskAlertDO::getDeviceId, event.deviceId())
                                .eq(GymRiskAlertDO::getSourceEventKey, event.sourceEventKey())
                                .eq(GymRiskAlertDO::getType, event.type())
                                .last("FOR UPDATE"));
        if (old != null) {
            if (!snapshot.equals(old.getSourceSnapshot())) throw exception(RISK_SOURCE_CONFLICT);
            return old.getId();
        }
        var device =
                devices.selectOne(
                        new LambdaQueryWrapper<
                                        cn.iocoder.yudao.module.gym.dal.dataobject.device
                                                .GymDeviceDO>()
                                .eq(
                                        cn.iocoder.yudao.module.gym.dal.dataobject.device
                                                        .GymDeviceDO
                                                ::getId,
                                        event.deviceId())
                                .last("FOR UPDATE"));
        if (device == null
                || !Set.of("door", "face_reader").contains(device.getType())
                || event.memberId() != null && members.selectById(event.memberId()) == null)
            throw exception(RISK_SOURCE_INVALID);
        for (Long id : canonical.evidenceFileIds()) {
            var file = files.selectById(id);
            if (file == null
                    || !device.getStoreId().equals(file.getStoreId())
                    || !"risk_evidence".equals(file.getCategory())
                    || file.getMimeType() == null
                    || !Set.of("image/png", "image/jpeg", "video/mp4").contains(file.getMimeType())
                    || file.getSize() == null
                    || file.getSize() <= 0
                    || file.getSize() > 50L * 1024 * 1024) throw exception(RISK_SOURCE_INVALID);
        }
        var row = new GymRiskAlertDO();
        row.setStoreId(device.getStoreId());
        row.setDeviceId(device.getId());
        row.setMemberId(canonical.memberId());
        row.setType(canonical.type());
        row.setSourceEventKey(canonical.sourceEventKey());
        row.setSourceSnapshot(snapshot);
        row.setOccurredAt(canonical.occurredAt());
        row.setEvidenceFileIds(JsonUtils.toJsonString(canonical.evidenceFileIds()));
        row.setStatus("open");
        row.setVersion(1);
        alerts.insert(row);
        return row.getId();
    }
}
