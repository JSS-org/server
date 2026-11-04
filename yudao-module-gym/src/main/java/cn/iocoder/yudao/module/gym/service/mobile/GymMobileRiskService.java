package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileRiskVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.risk.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.risk.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymMobileRiskService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private SecurityFrameworkService security;
    @Resource private GymStoreMapper stores;
    @Resource private GymRiskAlertMapper alerts;
    @Resource private GymRiskResolutionMapper resolutions;
    @Resource private GymFileMapper files;
    @Resource private FileApi storage;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    private void validate(Object value) {
        var errors = validator.validate(value);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private Long authorize(boolean write) {
        Long user = access.requireAdmin();
        if (!security.hasPermission("gym:risk:read")
                || write && !security.hasPermission("gym:risk:resolve")) throw exception(FORBIDDEN);
        return user;
    }

    private GymRiskAlertDO require(Long id, boolean lock) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var row =
                alerts.selectOne(
                        new LambdaQueryWrapper<GymRiskAlertDO>()
                                .eq(GymRiskAlertDO::getId, id)
                                .last(lock ? "FOR UPDATE" : ""));
        if (row == null) throw exception(RISK_ALERT_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Alert> page(Query req) {
        authorize(false);
        validate(req);
        scope.check(req.getStoreId());
        if (stores.selectById(req.getStoreId()) == null) throw exception(STORE_NOT_EXISTS);
        var page =
                alerts.selectPage(
                        req,
                        new LambdaQueryWrapper<GymRiskAlertDO>()
                                .eq(GymRiskAlertDO::getStoreId, req.getStoreId())
                                .eq(
                                        req.getStatus() != null,
                                        GymRiskAlertDO::getStatus,
                                        req.getStatus())
                                .orderByDesc(GymRiskAlertDO::getOccurredAt)
                                .orderByDesc(GymRiskAlertDO::getId));
        return new PageResult<>(
                page.getList().stream().map(this::response).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Alert resolve(Long id, Resolve req, String key) {
        Long user = authorize(true);
        validate(req);
        require(id, false);
        // Preserve both historical Map.of orders without rewriting their stored hashes.
        var payload = new LinkedHashMap<String, Object>();
        payload.put("id", id);
        payload.put("request", req);
        var legacyPayload = new LinkedHashMap<String, Object>();
        legacyPayload.put("request", req);
        legacyPayload.put("id", id);
        return idempotency.executeWithLegacyPayload(
                "risk-resolution",
                key,
                payload,
                legacyPayload,
                Alert.class,
                () -> {
                    authorize(true);
                    var row = require(id, true);
                    if (!"open".equals(row.getStatus())
                            || !req.getExpectedVersion().equals(row.getVersion()))
                        throw exception(RISK_RESOLUTION_CONFLICT);
                    String note = req.getNote().trim();
                    if (note.isBlank()) throw exception(MOBILE_REQUEST_INVALID);
                    var resolution = new GymRiskResolutionDO();
                    resolution.setAlertId(id);
                    resolution.setOperatorUserId(user);
                    resolution.setResult(req.getResult());
                    resolution.setNote(note);
                    resolution.setPreviousVersion(row.getVersion());
                    resolution.setResolvedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
                    resolutions.insert(resolution);
                    row.setStatus("resolved");
                    row.setVersion(row.getVersion() + 1);
                    alerts.updateById(row);
                    return response(row);
                });
    }

    private Alert response(GymRiskAlertDO row) {
        var resolution = resolutions.selectOne(GymRiskResolutionDO::getAlertId, row.getId());
        return new Alert(
                row.getId().toString(),
                row.getType(),
                row.getDeviceId().toString(),
                row.getMemberId() == null ? null : row.getMemberId().toString(),
                row.getOccurredAt().atZone(GymDateRange.ZONE).toOffsetDateTime().toString(),
                row.getStatus(),
                JsonUtils.parseArray(row.getEvidenceFileIds(), Long.class).stream()
                        .map(String::valueOf)
                        .toList(),
                row.getVersion(),
                resolution == null
                        ? null
                        : new Resolution(
                                resolution.getResult(),
                                resolution.getNote(),
                                resolution.getOperatorUserId().toString(),
                                resolution
                                        .getResolvedAt()
                                        .atZone(GymDateRange.ZONE)
                                        .toOffsetDateTime()
                                        .toString()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymFileContentDTO attachment(Long id, Long fileId) {
        authorize(false);
        var alert = require(id, false);
        if (fileId == null
                || !JsonUtils.parseArray(alert.getEvidenceFileIds(), Long.class).contains(fileId))
            throw exception(FILE_NOT_EXISTS);
        var file = files.selectById(fileId);
        if (file == null
                || !alert.getStoreId().equals(file.getStoreId())
                || !"risk_evidence".equals(file.getCategory())
                || file.getMimeType() == null
                || !Set.of("image/png", "image/jpeg", "video/mp4").contains(file.getMimeType()))
            throw exception(FILE_NOT_EXISTS);
        byte[] bytes = storage.getFileContentByUrl(file.getUrl());
        if (bytes == null || bytes.length == 0 || bytes.length > 50 * 1024 * 1024)
            throw exception(FILE_CONTENT_NOT_EXISTS);
        var detected = GymFileTypeUtils.detect(bytes);
        if (detected == null || !file.getMimeType().equals(detected.getMimeType()))
            throw exception(RISK_SOURCE_INVALID);
        return new GymFileContentDTO(
                "risk-evidence-" + fileId + "." + detected.getExtension(),
                detected.getMimeType(),
                bytes);
    }
}
