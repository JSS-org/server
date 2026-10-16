package cn.iocoder.yudao.module.gym.service.device;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.device.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymDeviceService {
    @Resource private GymDeviceMapper gymDeviceMapper;
    @Resource private GymDeviceCompartmentMapper gymDeviceCompartmentMapper;
    @Resource private GymDeviceCommandMapper gymDeviceCommandMapper;
    @Resource private GymEquipmentMapper gymEquipmentMapper;
    @Resource private GymEquipmentTutorialMapper gymEquipmentTutorialMapper;
    @Resource private GymFileMapper gymFileMapper;
    @Resource private GymDataScope scope;
    @Resource private GymStoreService stores;
    @Resource private GymBusinessClock clock;
    @Resource private AdminUserService users;
    @Resource private Validator validator;

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private GymDeviceDO requireDevice(Long id, boolean lock) {
        var q = new LambdaQueryWrapper<GymDeviceDO>().eq(GymDeviceDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = gymDeviceMapper.selectOne(q);
        if (row == null) throw exception(DEVICE_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }

    private GymEquipmentDO requireEquipment(Long id, boolean lock) {
        var q = new LambdaQueryWrapper<GymEquipmentDO>().eq(GymEquipmentDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = gymEquipmentMapper.selectOne(q);
        if (row == null) throw exception(EQUIPMENT_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }

    public PageResult<GymDeviceRespVO> devices(GymBusinessPageReqVO req) {
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return new PageResult<>(List.of(), 0L);
        var q =
                new LambdaQueryWrapper<GymDeviceDO>()
                        .eq(req.getStoreId() != null, GymDeviceDO::getStoreId, req.getStoreId())
                        .in(allowed != null, GymDeviceDO::getStoreId, allowed)
                        .eq(req.getStatus() != null, GymDeviceDO::getStatus, req.getStatus())
                        .eq(req.getKind() != null, GymDeviceDO::getType, req.getKind())
                        .orderByDesc(GymDeviceDO::getId);
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            q.and(
                    w ->
                            w.like(GymDeviceDO::getName, req.getKeyword())
                                    .or()
                                    .like(GymDeviceDO::getDeviceUid, req.getKeyword()));
        var page = gymDeviceMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(r -> deviceResponse(r, false)).toList(),
                page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymDeviceRespVO device(Long id) {
        return deviceResponse(requireDevice(id, false), true);
    }

    private void rejectTelemetry(GymDeviceSaveReqVO req) {
        if (req.getOnline() != null || req.getLastSeenAt() != null)
            throw exception(DEVICE_IDENTITY_IMMUTABLE);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymDeviceRespVO createDevice(GymDeviceSaveReqVO req) {
        rejectTelemetry(req);
        if (req.getStatus() == null) req.setStatus("active");
        validate(req);
        stores.requireStore(req.getStoreId(), false);
        req.setProvider(req.getProvider().trim().toLowerCase(Locale.ROOT));
        req.setDeviceUid(req.getDeviceUid().trim());
        validate(req);
        var row = BeanUtils.toBean(req, GymDeviceDO.class);
        row.setOnline(false);
        if (row.getLocation() == null) row.setLocation("");
        try {
            gymDeviceMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw exception(DEVICE_UID_DUPLICATE);
        }
        return deviceResponse(row, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymDeviceRespVO patchDevice(Long id, GymDeviceSaveReqVO patch) {
        var row = requireDevice(id, true);
        rejectTelemetry(patch);
        if (patch.getStoreId() != null && !row.getStoreId().equals(patch.getStoreId())
                || patch.getType() != null && !row.getType().equals(patch.getType())
                || patch.getProvider() != null
                        && !row.getProvider()
                                .equals(patch.getProvider().trim().toLowerCase(Locale.ROOT))
                || patch.getDeviceUid() != null
                        && !row.getDeviceUid().equals(patch.getDeviceUid().trim()))
            throw exception(DEVICE_IDENTITY_IMMUTABLE);
        var req = BeanUtils.toBean(row, GymDeviceSaveReqVO.class);
        GymPatchUtils.merge(patch, req);
        validate(req);
        row.setName(req.getName());
        row.setLocation(req.getLocation());
        row.setStatus(req.getStatus());
        gymDeviceMapper.updateById(row);
        return deviceResponse(row, true);
    }

    private GymDeviceRespVO deviceResponse(GymDeviceDO row, boolean detail) {
        var result = BeanUtils.toBean(row, GymDeviceRespVO.class);
        result.setStoreName(stores.get(row.getStoreId()).getName());
        result.setCompartments(
                detail
                        ? gymDeviceCompartmentMapper
                                .selectList(
                                        new LambdaQueryWrapper<GymDeviceCompartmentDO>()
                                                .eq(
                                                        GymDeviceCompartmentDO::getDeviceId,
                                                        row.getId())
                                                .eq(GymDeviceCompartmentDO::getConfigured, true)
                                                .orderByAsc(GymDeviceCompartmentDO::getId))
                                .stream()
                                .map(r -> BeanUtils.toBean(r, GymDeviceRespVO.Compartment.class))
                                .toList()
                        : List.of());
        return result;
    }

    private boolean busy(GymDeviceCompartmentDO row) {
        return Set.of("reserved", "occupied").contains(row.getOccupancyState());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymDeviceRespVO saveCompartments(Long id, GymCompartmentsReqVO req) {
        validate(req);
        var device = requireDevice(id, true);
        if (!"locker".equals(device.getType())) throw exception(DEVICE_COMPARTMENT_INVALID);
        var desired = new LinkedHashMap<String, String>();
        for (var item : req.getCompartments()) {
            String number = item.getNumber().trim().toUpperCase(Locale.ROOT);
            if (number.isEmpty()
                    || number.length() > 32
                    || number.chars().anyMatch(Character::isISOControl)
                    || desired.putIfAbsent(number, item.getStatus()) != null)
                throw exception(DEVICE_COMPARTMENT_INVALID);
        }
        var rows =
                gymDeviceCompartmentMapper.selectList(
                        new LambdaQueryWrapper<GymDeviceCompartmentDO>()
                                .eq(GymDeviceCompartmentDO::getDeviceId, id)
                                .orderByAsc(GymDeviceCompartmentDO::getId)
                                .last("FOR UPDATE"));
        var existing = new HashMap<String, GymDeviceCompartmentDO>();
        rows.forEach(r -> existing.put(r.getNumber(), r));
        for (var row : rows)
            if (busy(row)
                    && Boolean.TRUE.equals(row.getConfigured())
                    && (!desired.containsKey(row.getNumber())
                            || !row.getStatus().equals(desired.get(row.getNumber()))))
                throw exception(DEVICE_COMPARTMENT_BUSY);
        for (var entry : desired.entrySet()) {
            var row = existing.get(entry.getKey());
            boolean create = row == null;
            if (create) {
                row = new GymDeviceCompartmentDO();
                row.setDeviceId(id);
                row.setNumber(entry.getKey());
                row.setOccupancyState("unknown");
            } else if (busy(row) && !Boolean.TRUE.equals(row.getConfigured()))
                throw exception(DEVICE_COMPARTMENT_BUSY);
            row.setStatus(entry.getValue());
            row.setConfigured(true);
            if (create) gymDeviceCompartmentMapper.insert(row);
            else gymDeviceCompartmentMapper.updateById(row);
        }
        for (var row : rows)
            if (!desired.containsKey(row.getNumber()) && Boolean.TRUE.equals(row.getConfigured())) {
                row.setConfigured(false);
                row.setStatus("inactive");
                gymDeviceCompartmentMapper.updateById(row);
            }
        return deviceResponse(device, true);
    }

    public PageResult<GymDeviceCommandRespVO> commands(Long id, GymBusinessPageReqVO req) {
        requireDevice(id, false);
        var range = GymDateRange.of(req);
        var q =
                new LambdaQueryWrapper<GymDeviceCommandDO>()
                        .eq(GymDeviceCommandDO::getDeviceId, id)
                        .eq(req.getStatus() != null, GymDeviceCommandDO::getStatus, req.getStatus())
                        .ge(range.from() != null, GymDeviceCommandDO::getRequestedAt, range.from())
                        .lt(range.to() != null, GymDeviceCommandDO::getRequestedAt, range.to())
                        .orderByDesc(GymDeviceCommandDO::getId);
        var page = gymDeviceCommandMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream()
                        .map(r -> BeanUtils.toBean(r, GymDeviceCommandRespVO.class))
                        .toList(),
                page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymDeviceCommandRespVO command(Long id, GymDeviceCommandReqVO req, String key) {
        validate(req);
        var device = requireDevice(id, true);
        Long user = SecurityFrameworkUtils.getLoginUserId();
        if (user == null || key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}"))
            throw exception(DEVICE_COMMAND_INVALID);
        if (!("door".equals(device.getType())
                        && "open_door".equals(req.getCommand())
                        && req.getCompartmentId() == null
                || "locker".equals(device.getType())
                        && "open_locker".equals(req.getCommand())
                        && req.getCompartmentId() != null)) throw exception(DEVICE_COMMAND_INVALID);
        String hash = SecureUtil.sha256(JsonUtils.toJsonString(req));
        var old =
                gymDeviceCommandMapper.selectOne(
                        new LambdaQueryWrapper<GymDeviceCommandDO>()
                                .eq(GymDeviceCommandDO::getDeviceId, id)
                                .eq(GymDeviceCommandDO::getRequesterId, user)
                                .eq(GymDeviceCommandDO::getRequestKey, key));
        if (old != null) {
            if (!hash.equals(old.getRequestHash())) throw exception(DEVICE_COMMAND_CONFLICT);
            return BeanUtils.toBean(old, GymDeviceCommandRespVO.class);
        }
        GymDeviceCompartmentDO compartment = null;
        if (req.getCompartmentId() != null) {
            compartment = gymDeviceCompartmentMapper.selectById(req.getCompartmentId());
            if (compartment == null || !id.equals(compartment.getDeviceId()))
                throw exception(DEVICE_COMPARTMENT_INVALID);
        }
        // TODO-INTEGRATION-DEVICE: authenticated vendor request/receipt and heartbeat ingestion
        // must be wired before any success or online state can be written.
        String failure = "设备厂商接口未配置，未发送远程指令";
        if (!"active".equals(device.getStatus())) failure = "设备已停用，未发送指令";
        else if (!"active".equals(stores.get(device.getStoreId()).getStatus()))
            failure = "门店已停用，未发送指令";
        else if (compartment != null
                && (!Boolean.TRUE.equals(compartment.getConfigured())
                        || !"available".equals(compartment.getStatus()))) failure = "柜格已停用，未发送指令";
        else if ("mock".equals(device.getProvider())) failure = "模拟设备未接入真实控制，未发送指令";
        var row = BeanUtils.toBean(req, GymDeviceCommandDO.class);
        row.setDeviceId(id);
        row.setRequesterId(user);
        var account = users.getUser(user);
        row.setRequesterName(
                account == null || account.getNickname() == null
                        ? "用户#" + user
                        : account.getNickname());
        row.setRequestedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
        row.setStatus("failed");
        row.setFailureReason(failure);
        row.setSimulated("mock".equals(device.getProvider()));
        row.setRequestKey(key);
        row.setRequestHash(hash);
        gymDeviceCommandMapper.insert(row);
        return BeanUtils.toBean(row, GymDeviceCommandRespVO.class);
    }

    public PageResult<GymEquipmentRespVO> equipment(GymBusinessPageReqVO req) {
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return new PageResult<>(List.of(), 0L);
        var q =
                new LambdaQueryWrapper<GymEquipmentDO>()
                        .eq(req.getStoreId() != null, GymEquipmentDO::getStoreId, req.getStoreId())
                        .in(allowed != null, GymEquipmentDO::getStoreId, allowed)
                        .eq(req.getStatus() != null, GymEquipmentDO::getStatus, req.getStatus())
                        .orderByDesc(GymEquipmentDO::getId);
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            q.and(
                    w ->
                            w.like(GymEquipmentDO::getName, req.getKeyword())
                                    .or()
                                    .like(GymEquipmentDO::getCode, req.getKeyword()));
        var page = gymEquipmentMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::equipmentResponse).toList(), page.getTotal());
    }

    public GymEquipmentRespVO equipment(Long id) {
        return equipmentResponse(requireEquipment(id, false));
    }

    private GymEquipmentRespVO equipmentResponse(GymEquipmentDO row) {
        var result = BeanUtils.toBean(row, GymEquipmentRespVO.class);
        result.setStoreName(stores.get(row.getStoreId()).getName());
        return result;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymEquipmentRespVO createEquipment(GymEquipmentSaveReqVO req) {
        if (req.getStatus() == null) req.setStatus("active");
        validate(req);
        stores.requireStore(req.getStoreId(), false);
        req.setCode(req.getCode().trim());
        var row = BeanUtils.toBean(req, GymEquipmentDO.class);
        if (row.getModel() == null) row.setModel("");
        try {
            gymEquipmentMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw exception(EQUIPMENT_CODE_DUPLICATE);
        }
        return equipmentResponse(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymEquipmentRespVO patchEquipment(Long id, GymEquipmentSaveReqVO patch) {
        var row = requireEquipment(id, true);
        if (patch.getStoreId() != null && !row.getStoreId().equals(patch.getStoreId())
                || patch.getCode() != null && !row.getCode().equals(patch.getCode().trim()))
            throw exception(EQUIPMENT_IDENTITY_IMMUTABLE);
        var req = BeanUtils.toBean(row, GymEquipmentSaveReqVO.class);
        GymPatchUtils.merge(patch, req);
        validate(req);
        row.setName(req.getName());
        row.setModel(req.getModel());
        row.setStatus(req.getStatus());
        gymEquipmentMapper.updateById(row);
        return equipmentResponse(row);
    }

    public PageResult<GymTutorialRespVO> tutorials(Long equipment, GymBusinessPageReqVO req) {
        requireEquipment(equipment, false);
        var q =
                new LambdaQueryWrapper<GymEquipmentTutorialDO>()
                        .eq(GymEquipmentTutorialDO::getEquipmentId, equipment)
                        .eq(
                                req.getStatus() != null,
                                GymEquipmentTutorialDO::getStatus,
                                req.getStatus())
                        .orderByAsc(GymEquipmentTutorialDO::getSortOrder)
                        .orderByDesc(GymEquipmentTutorialDO::getId);
        var page = gymEquipmentTutorialMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream()
                        .map(r -> BeanUtils.toBean(r, GymTutorialRespVO.class))
                        .toList(),
                page.getTotal());
    }

    private void tutorialFile(Long store, Long fileId) {
        var file = gymFileMapper.selectById(fileId);
        if (file == null
                || !store.equals(file.getStoreId())
                || file.getMimeType() == null
                || !("image".equals(file.getCategory()) && file.getMimeType().startsWith("image/")
                        || "video".equals(file.getCategory())
                                && "video/mp4".equals(file.getMimeType())))
            throw exception(EQUIPMENT_TUTORIAL_FILE_INVALID);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymTutorialRespVO createTutorial(Long id, GymTutorialSaveReqVO req) {
        validate(req);
        var equipment = requireEquipment(id, true);
        tutorialFile(equipment.getStoreId(), req.getFileId());
        var row = BeanUtils.toBean(req, GymEquipmentTutorialDO.class);
        row.setEquipmentId(id);
        row.setStatus("active");
        if (row.getInstructions() == null) row.setInstructions("");
        gymEquipmentTutorialMapper.insert(row);
        return BeanUtils.toBean(row, GymTutorialRespVO.class);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymTutorialRespVO patchTutorial(Long equipment, Long id, GymTutorialStatusReqVO req) {
        validate(req);
        var parent = requireEquipment(equipment, true);
        var row = gymEquipmentTutorialMapper.selectById(id);
        if (row == null || !equipment.equals(row.getEquipmentId()))
            throw exception(EQUIPMENT_TUTORIAL_NOT_EXISTS);
        if ("active".equals(req.getStatus())) tutorialFile(parent.getStoreId(), row.getFileId());
        row.setStatus(req.getStatus());
        gymEquipmentTutorialMapper.updateById(row);
        return BeanUtils.toBean(row, GymTutorialRespVO.class);
    }
}
