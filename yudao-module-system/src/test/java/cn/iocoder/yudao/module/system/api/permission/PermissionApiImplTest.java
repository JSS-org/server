package cn.iocoder.yudao.module.system.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.service.permission.*;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

class PermissionApiImplTest {
    PermissionApiImpl api;
    PermissionService permissions;
    RoleService roles;
    MenuService menus;

    @BeforeEach
    void setup() {
        api = new PermissionApiImpl();
        permissions = mock(PermissionService.class);
        roles = mock(RoleService.class);
        menus = mock(MenuServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(api, "permissionService", permissions);
        ReflectionTestUtils.setField(api, "roleService", roles);
        ReflectionTestUtils.setField(api, "menuService", menus);
    }

    RoleDO role(long id, String code, int status) {
        var row = new RoleDO();
        row.setId(id);
        row.setCode(code);
        row.setStatus(status);
        return row;
    }

    MenuDO menu(long id, long parent, String permission, int status) {
        var row = new MenuDO();
        row.setId(id);
        row.setParentId(parent);
        row.setPermission(permission);
        row.setStatus(status);
        return row;
    }

    @Test
    void noRolesOrOnlyDisabledRolesNeverRequestMenus() {
        when(permissions.getUserRoleIdListByUserId(7L)).thenReturn(Set.of());
        assertTrue(api.getUserPermissionInfo(7L).roles().isEmpty());
        verifyNoInteractions(roles, menus);
        when(permissions.getUserRoleIdListByUserId(7L)).thenReturn(Set.of(1L));
        when(roles.getRoleList(Set.of(1L))).thenReturn(List.of(role(1, "disabled", 1)));
        var result = api.getUserPermissionInfo(7L);
        assertTrue(result.roles().isEmpty());
        assertTrue(result.permissions().isEmpty());
        verify(permissions, never())
                .getRoleMenuListByRoleId(org.mockito.ArgumentMatchers.<Long>anyCollection());
    }

    @Test
    void disabledRoleAndDisabledParentMenusCannotContributeContextPermissions() {
        when(permissions.getUserRoleIdListByUserId(7L)).thenReturn(Set.of(1L, 2L, 3L));
        when(roles.getRoleList(Set.of(1L, 2L, 3L)))
                .thenReturn(
                        List.of(role(1, "staff", 0), role(2, "disabled", 1), role(3, "staff", 0)));
        var ids = Set.of(10L, 11L, 12L, 13L, 14L, 15L);
        when(permissions.getRoleMenuListByRoleId(Set.of(1L, 3L))).thenReturn(ids);
        doReturn(
                        List.of(
                                menu(10, 0, "gym:members:write", 1),
                                menu(11, 10, "gym:members:read", 0),
                                menu(12, 0, "gym:stores:read", 0),
                                menu(13, 0, "gym:stores:read", 0),
                                menu(14, 0, "", 0),
                                menu(15, 0, null, 0)))
                .when(menus)
                .getMenuList(ids);
        var result = api.getUserPermissionInfo(7L);
        assertEquals(List.of("staff"), result.roles());
        assertEquals(List.of("gym:stores:read"), result.permissions());
        verify(permissions).getRoleMenuListByRoleId(Set.of(1L, 3L));
    }

    @Test
    void superAdminUsesExistingMenuResolutionWithoutInventingWildcardOrGymRole() {
        when(permissions.getUserRoleIdListByUserId(7L)).thenReturn(Set.of(1L));
        when(roles.getRoleList(Set.of(1L))).thenReturn(List.of(role(1, "super_admin", 0)));
        when(permissions.getRoleMenuListByRoleId(Set.of(1L))).thenReturn(Set.of(10L, 11L));
        doReturn(List.of(menu(10, 0, "system:user:query", 0), menu(11, 0, "gym:members:read", 0)))
                .when(menus)
                .getMenuList(Set.of(10L, 11L));
        var result = api.getUserPermissionInfo(7L);
        assertEquals(List.of("super_admin"), result.roles());
        assertEquals(List.of("gym:members:read", "system:user:query"), result.permissions());
    }
}
