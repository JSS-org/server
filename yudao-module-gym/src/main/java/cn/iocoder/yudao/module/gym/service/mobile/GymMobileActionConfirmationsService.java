package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymActionConfirmationVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.mobile.GymActionConfirmationProperties;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.device.GymActionTotp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymMobileActionConfirmationsService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private SecurityFrameworkService security;
    @Resource private GymDeviceMapper devices;
    @Resource private GymDeviceCompartmentMapper compartments;
    @Resource private GymStoreMapper stores;
    @Resource private GymDeviceCommandMapper commands;
    @Resource private GymActionVerificationStateMapper states;
    @Resource private GymActionConfirmationMapper confirmations;
    @Resource private GymActionConfirmationProperties properties;
    @Resource private GymActionTotp totp;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;
    @Resource private PlatformTransactionManager transactionManager;
    private final SecureRandom random = new SecureRandom();

    private record Outcome(Result result, ErrorCode error) {}

    private Long authorize() {
        Long user = access.requireAdmin();
        if (!security.hasPermission("gym:devices:command")) throw exception(FORBIDDEN);
        return user;
    }

    private GymActionTotp.Credential credential(Long user) {
        return totp.credential(properties.secret(TenantContextHolder.getRequiredTenantId(), user));
    }

    private GymDeviceDO resource(Long id, String action, Long slot) {
        if (id == null
                || id <= 0
                || !("open_door".equals(action) && slot == null
                        || "open_locker".equals(action) && slot != null && slot > 0))
            throw exception(MOBILE_REQUEST_INVALID);
        var device =
                devices.selectOne(
                        new LambdaQueryWrapper<GymDeviceDO>()
                                .eq(GymDeviceDO::getId, id)
                                .last("FOR UPDATE"));
        if (device == null) throw exception(DEVICE_NOT_EXISTS);
        scope.check(device.getStoreId());
        var store =
                stores.selectOne(
                        new LambdaQueryWrapper<
                                        cn.iocoder.yudao.module.gym.dal.dataobject.store
                                                .GymStoreDO>()
                                .eq(
                                        cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO
                                                ::getId,
                                        device.getStoreId())
                                .last("FOR UPDATE"));
        if (store == null
                || !"active".equals(store.getStatus())
                || !"active".equals(device.getStatus())
                || !("open_door".equals(action) && "door".equals(device.getType())
                        || "open_locker".equals(action) && "locker".equals(device.getType())))
            throw exception(ACTION_RESOURCE_UNAVAILABLE);
        if (slot != null) {
            var row =
                    compartments.selectOne(
                            new LambdaQueryWrapper<GymDeviceCompartmentDO>()
                                    .eq(GymDeviceCompartmentDO::getId, slot)
                                    .last("FOR UPDATE"));
            if (row == null
                    || !id.equals(row.getDeviceId())
                    || !Boolean.TRUE.equals(row.getConfigured())
                    || !"available".equals(row.getStatus()))
                throw exception(ACTION_RESOURCE_UNAVAILABLE);
        }
        return device;
    }

    // Failed OTP attempts must commit before a business exception is returned to the caller.
    @Transactional(propagation = Propagation.NEVER)
    public Result create(Request req) {
        Long user = authorize();
        if (req == null) throw exception(MOBILE_REQUEST_INVALID);
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw exception(MOBILE_REQUEST_INVALID);
        var key = credential(user);
        var tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        Outcome outcome =
                tx.execute(
                        status -> {
                            authorize();
                            var device =
                                    resource(
                                            req.getResourceId(),
                                            req.getAction(),
                                            req.getCompartmentId());
                            Long tenant = TenantContextHolder.getRequiredTenantId();
                            states.ensure(tenant, user);
                            var state = states.lock(tenant, user);
                            var now = clock.now().truncatedTo(ChronoUnit.MICROS);
                            if (now.getEpochSecond() < 0
                                    || state.getLastAttemptAt() != null
                                            && now.isBefore(state.getLastAttemptAt()))
                                return new Outcome(null, ACTION_CONFIRMATION_UNAVAILABLE);
                            if (state.getLockedUntil() != null
                                    && now.isBefore(state.getLockedUntil()))
                                return new Outcome(null, ACTION_VERIFICATION_LOCKED);
                            int failures =
                                    state.getLockedUntil() == null ? state.getFailureCount() : 0;
                            Long counter =
                                    totp.match(
                                            key,
                                            req.getVerificationCode(),
                                            now,
                                            confirmations.lastCounter(
                                                    tenant, user, key.fingerprint()));
                            if (counter == null) {
                                failures++;
                                states.saveAttempt(
                                        tenant,
                                        user,
                                        failures,
                                        failures >= 5 ? now.plusSeconds(300) : null,
                                        now);
                                return new Outcome(
                                        null,
                                        failures >= 5
                                                ? ACTION_VERIFICATION_LOCKED
                                                : ACTION_VERIFICATION_INVALID);
                            }
                            byte[] bytes = new byte[32];
                            random.nextBytes(bytes);
                            String token =
                                    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                            var row = new GymActionConfirmationDO();
                            row.setUserId(user);
                            row.setStoreId(device.getStoreId());
                            row.setDeviceId(device.getId());
                            row.setAction(req.getAction());
                            row.setCompartmentId(req.getCompartmentId());
                            row.setTokenHash(
                                    GymActionTotp.hash(token.getBytes(StandardCharsets.US_ASCII)));
                            row.setCredentialFingerprint(key.fingerprint());
                            row.setVerificationCounter(counter);
                            row.setIssuedAt(now);
                            row.setExpiresAt(now.plusSeconds(120));
                            confirmations.insert(row);
                            states.saveAttempt(tenant, user, 0, null, now);
                            return new Outcome(
                                    new Result(
                                            token,
                                            row.getExpiresAt()
                                                    .atZone(GymDateRange.ZONE)
                                                    .toOffsetDateTime()
                                                    .toString()),
                                    null);
                        });
        if (outcome.error() != null) throw exception(outcome.error());
        return outcome.result();
    }

    /** Future command writer must call this in the same transaction as its real queued command. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void consume(String token, Long commandId) {
        Long user = authorize();
        var key = credential(user);
        if (token == null
                || !token.matches("[A-Za-z0-9_-]{43}")
                || commandId == null
                || commandId <= 0) throw exception(ACTION_CONFIRMATION_INVALID);
        var command = commands.selectById(commandId);
        if (command == null
                || !user.equals(command.getRequesterId())
                || !"queued".equals(command.getStatus())
                || !Boolean.FALSE.equals(command.getSimulated())
                || command.getRequestKey() == null
                || command.getRequestKey().isBlank()
                || command.getCompletedAt() != null) throw exception(ACTION_CONFIRMATION_INVALID);
        var device =
                resource(command.getDeviceId(), command.getCommand(), command.getCompartmentId());
        // Locking reread defeats a stale queued snapshot in the caller's transaction.
        var lockedCommand =
                commands.selectOne(
                        new LambdaQueryWrapper<GymDeviceCommandDO>()
                                .eq(GymDeviceCommandDO::getId, commandId)
                                .last("FOR UPDATE"));
        if (lockedCommand == null
                || !"queued".equals(lockedCommand.getStatus())
                || !user.equals(lockedCommand.getRequesterId())
                || !Boolean.FALSE.equals(lockedCommand.getSimulated())
                || lockedCommand.getCompletedAt() != null
                || lockedCommand.getRequestKey() == null
                || lockedCommand.getRequestKey().isBlank()
                || !Objects.equals(command.getDeviceId(), lockedCommand.getDeviceId())
                || !Objects.equals(command.getCommand(), lockedCommand.getCommand())
                || !Objects.equals(command.getCompartmentId(), lockedCommand.getCompartmentId()))
            throw exception(ACTION_CONFIRMATION_INVALID);
        command = lockedCommand;
        var row =
                confirmations.selectOne(
                        new LambdaQueryWrapper<GymActionConfirmationDO>()
                                .eq(
                                        GymActionConfirmationDO::getTokenHash,
                                        GymActionTotp.hash(
                                                token.getBytes(StandardCharsets.US_ASCII)))
                                .last("FOR UPDATE"));
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        if (row == null
                || !user.equals(row.getUserId())
                || !device.getId().equals(row.getDeviceId())
                || !device.getStoreId().equals(row.getStoreId())
                || !command.getCommand().equals(row.getAction())
                || !Objects.equals(command.getCompartmentId(), row.getCompartmentId())
                || !key.fingerprint().equals(row.getCredentialFingerprint())
                || row.getConsumedAt() != null
                || now.isBefore(row.getIssuedAt())
                || !now.isBefore(row.getExpiresAt())
                || command.getRequestedAt() == null
                || command.getRequestedAt().isBefore(row.getIssuedAt())
                || command.getRequestedAt().isAfter(now))
            throw exception(ACTION_CONFIRMATION_INVALID);
        row.setConsumedAt(now);
        row.setConsumedCommandId(commandId);
        confirmations.updateById(row);
    }
}
