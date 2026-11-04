package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDeviceCommandVO;
import cn.iocoder.yudao.module.gym.dal.mysql.device.GymDeviceCommandMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.device.GymDeviceMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.util.Set;

@Service
public class GymMobileDeviceCommandsService {
    private static final Set<String> SAFE_FAILURES =
            Set.of(
                    "设备厂商接口未配置，未发送远程指令",
                    "设备已停用，未发送指令",
                    "门店已停用，未发送指令",
                    "柜格已停用，未发送指令",
                    "模拟设备未接入真实控制，未发送指令");
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private SecurityFrameworkService security;
    @Resource private GymDeviceCommandMapper commands;
    @Resource private GymDeviceMapper devices;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymMobileDeviceCommandVO get(Long id) {
        Long user = access.requireAdmin();
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var row = commands.selectById(id);
        if (row == null) throw exception(DEVICE_COMMAND_NOT_EXISTS);
        if (!user.equals(row.getRequesterId())) {
            if (!security.hasPermission("gym:devices:read"))
                throw exception(DEVICE_COMMAND_NOT_EXISTS);
            var device = devices.selectById(row.getDeviceId());
            if (device == null) throw exception(DEVICE_COMMAND_NOT_EXISTS);
            var allowed = scope.readableStoreIds();
            if (allowed != null && !allowed.contains(device.getStoreId()))
                throw exception(DEVICE_COMMAND_NOT_EXISTS);
        }
        String status = row.getStatus();
        boolean completionValid =
                row.getCompletedAt() != null
                        && row.getRequestedAt() != null
                        && !row.getCompletedAt().isBefore(row.getRequestedAt());
        if (status == null
                || !Set.of("queued", "sent", "succeeded", "failed", "unknown").contains(status)
                || !"failed".equals(status) && !Boolean.FALSE.equals(row.getSimulated())
                || "succeeded".equals(status) && !completionValid) status = "unknown";
        boolean terminal = "succeeded".equals(status) || "failed".equals(status);
        String reason = null;
        if ("failed".equals(status)) {
            reason =
                    row.getFailureReason() != null && SAFE_FAILURES.contains(row.getFailureReason())
                            ? row.getFailureReason()
                            : "设备指令未完成，请联系门店核实";
        } else if ("unknown".equals(status)) reason = "暂无可信执行结果，请联系门店核实；不要自动重发指令";
        return new GymMobileDeviceCommandVO(
                row.getId().toString(),
                row.getDeviceId().toString(),
                row.getCommand(),
                status,
                time(row.getRequestedAt()),
                terminal && completionValid ? time(row.getCompletedAt()) : null,
                reason);
    }

    private String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
