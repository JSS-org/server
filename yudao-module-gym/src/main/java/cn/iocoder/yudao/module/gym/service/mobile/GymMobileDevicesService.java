package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDevicesVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.List;

@Service
public class GymMobileDevicesService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymStoreMapper stores;
    @Resource private GymDeviceMapper devices;
    @Resource private GymDeviceCompartmentMapper compartments;
    @Resource private Validator validator;

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private void store(Long id) {
        scope.check(id);
        if (stores.selectById(id) == null) throw exception(STORE_NOT_EXISTS);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Device> devices(Query req) {
        access.requireAdmin();
        validate(req);
        store(req.getStoreId());
        var q =
                new LambdaQueryWrapper<GymDeviceDO>()
                        .eq(GymDeviceDO::getStoreId, req.getStoreId())
                        .in(GymDeviceDO::getType, List.of("door", "locker"))
                        .eq(req.getType() != null, GymDeviceDO::getType, req.getType())
                        .orderByAsc(GymDeviceDO::getId);
        var result = devices.selectPage(req, q);
        return new PageResult<>(
                result.getList().stream()
                        .map(
                                row ->
                                        new Device(
                                                row.getId().toString(),
                                                row.getName(),
                                                row.getType(),
                                                row.getStatus()))
                        .toList(),
                result.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Compartment> compartments(Long id, PageParam req) {
        access.requireAdmin();
        validate(req);
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var parent = devices.selectById(id);
        if (parent == null) throw exception(DEVICE_NOT_EXISTS);
        store(parent.getStoreId());
        if (!"locker".equals(parent.getType())) throw exception(DEVICE_COMPARTMENT_INVALID);
        var result =
                compartments.selectPage(
                        req,
                        new LambdaQueryWrapper<GymDeviceCompartmentDO>()
                                .eq(GymDeviceCompartmentDO::getDeviceId, id)
                                .eq(GymDeviceCompartmentDO::getConfigured, true)
                                .orderByAsc(GymDeviceCompartmentDO::getId));
        return new PageResult<>(
                result.getList().stream()
                        .map(
                                row ->
                                        new Compartment(
                                                row.getId().toString(),
                                                row.getNumber(),
                                                row.getStatus()))
                        .toList(),
                result.getTotal());
    }
}
