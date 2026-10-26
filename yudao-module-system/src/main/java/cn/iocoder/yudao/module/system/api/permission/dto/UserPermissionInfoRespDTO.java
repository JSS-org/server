package cn.iocoder.yudao.module.system.api.permission.dto;

import java.util.List;

/** 启用角色和已过滤停用菜单的权限码，不包含用户个人资料或隐含的通配权限。 */
public record UserPermissionInfoRespDTO(List<String> roles, List<String> permissions) {}
