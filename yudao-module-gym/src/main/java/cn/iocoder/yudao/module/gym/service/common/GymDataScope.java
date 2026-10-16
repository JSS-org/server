package cn.iocoder.yudao.module.gym.service.common;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/** null 表示全部；空集表示无门店范围。仅本人数据权限不扩展为门店权限。 */
@Component
public class GymDataScope {
    @Resource private PermissionApi permissionApi;

    public Set<Long> readableStoreIds() {
        DeptDataPermissionRespDTO scope =
                permissionApi.getDeptDataPermission(SecurityFrameworkUtils.getLoginUserId());
        if (scope != null && Boolean.TRUE.equals(scope.getAll())) return null;
        return scope != null && scope.getDeptIds() != null
                ? scope.getDeptIds()
                : Collections.emptySet();
    }

    public void check(Long storeId) {
        Set<Long> ids = readableStoreIds();
        if (storeId == null || (ids != null && !ids.contains(storeId)))
            throw exception(STORE_NO_PERMISSION);
    }

    public void checkAll(Collection<Long> storeIds) {
        storeIds.forEach(this::check);
    }

    public void requireHeadquarters() {
        if (readableStoreIds() != null) throw exception(STORE_NO_PERMISSION);
    }

    public void checkAccountLinkPermission() {
        if (!permissionApi.hasAnyPermissions(
                SecurityFrameworkUtils.getLoginUserId(), "system:user:update")) {
            throw exception(EMPLOYEE_USER_NO_PERMISSION);
        }
    }
}
