package cn.iocoder.yudao.module.system.api.permission;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.api.permission.dto.UserPermissionInfoRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 权限 API 实现类
 *
 * @author 芋道源码
 */
@Service
public class PermissionApiImpl implements PermissionApi {

    @Resource private PermissionService permissionService;
    @Resource private RoleService roleService;
    @Resource private MenuService menuService;

    @Override
    public Set<String> getEnabledMenuPermissions(Long userId) {
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(userId);
        if (roleIds.isEmpty()) return Set.of();
        var roles = roleService.getRoleList(roleIds);
        roles.removeIf(role -> !CommonStatusEnum.isEnable(role.getStatus()));
        var menuIds = permissionService.getRoleMenuListByRoleId(convertSet(roles, RoleDO::getId));
        var enabled = menuService.filterDisableMenus(menuService.getMenuList(menuIds));
        return enabled.stream()
                .map(MenuDO::getPermission)
                .filter(StrUtil::isNotBlank)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @DataPermission(enable = false)
    public UserPermissionInfoRespDTO getUserPermissionInfo(Long userId) {
        var roleIds = permissionService.getUserRoleIdListByUserId(userId);
        if (roleIds.isEmpty()) return new UserPermissionInfoRespDTO(List.of(), List.of());
        var roles =
                roleService.getRoleList(roleIds).stream()
                        .filter(role -> Integer.valueOf(0).equals(role.getStatus()))
                        .toList();
        var enabledIds = roles.stream().map(RoleDO::getId).collect(Collectors.toSet());
        if (enabledIds.isEmpty()) return new UserPermissionInfoRespDTO(List.of(), List.of());
        var menuIds = permissionService.getRoleMenuListByRoleId(enabledIds);
        var menus = menuService.filterDisableMenus(menuService.getMenuList(menuIds));
        return new UserPermissionInfoRespDTO(
                codes(roles.stream().map(RoleDO::getCode)),
                codes(menus.stream().map(MenuDO::getPermission)));
    }

    private static List<String> codes(Stream<String> values) {
        return values.filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public Set<Long> getUserRoleIdListByRoleIds(Collection<Long> roleIds) {
        return permissionService.getUserRoleIdListByRoleId(roleIds);
    }

    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        return permissionService.hasAnyPermissions(userId, permissions);
    }

    @Override
    public boolean hasAnyRoles(Long userId, String... roles) {
        return permissionService.hasAnyRoles(userId, roles);
    }

    @Override
    public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
        return permissionService.getDeptDataPermission(userId);
    }
}
