package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileRequestMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.function.Supplier;

/** Durable result and business mutations commit together; failures leave no committed pending key. */
@Service
public class GymMobileIdempotency {
    @Resource private GymMobileRequestMapper requests;

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(
            String operation, String key, Object payload, Class<T> type, Supplier<T> action) {
        return executePayloads(operation, key, payload, null, type, action);
    }

    /**
     * A caller may supply an explicitly equivalent legacy encoding built from the current validated
     * request. Never accept a legacy hash or payload from the client. Existing hashes stay unchanged.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T executeWithLegacyPayload(
            String operation,
            String key,
            Object payload,
            Object legacyPayload,
            Class<T> type,
            Supplier<T> action) {
        return executePayloads(operation, key, payload, legacyPayload, type, action);
    }

    private <T> T executePayloads(
            String operation,
            String key,
            Object payload,
            Object legacyPayload,
            Class<T> type,
            Supplier<T> action) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,128}"))
            throw exception(MOBILE_REQUEST_INVALID);
        var login = SecurityFrameworkUtils.getLoginUser();
        if (login == null || login.getId() == null || login.getUserType() == null)
            throw exception(MOBILE_IDENTITY_INVALID);
        Long tenant = TenantContextHolder.getRequiredTenantId();
        String hash = DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
        requests.ensure(tenant, login.getUserType(), login.getId(), operation, key, hash);
        var request = requests.lock(tenant, login.getUserType(), login.getId(), operation, key);
        if (request == null
                || (!hash.equals(request.getRequestHash())
                        && (legacyPayload == null
                                || !DigestUtil.sha256Hex(JsonUtils.toJsonString(legacyPayload))
                                        .equals(request.getRequestHash()))))
            throw exception(MOBILE_IDEMPOTENCY_CONFLICT);
        if (request.getResponseJson() != null)
            return JsonUtils.parseObject(request.getResponseJson(), type);
        T result = action.get();
        request.setResponseJson(JsonUtils.toJsonString(result));
        requests.updateById(request);
        return result;
    }
}
