package cn.iocoder.yudao.module.system.controller.admin.permission;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.permission.PermissionAssignRoleDataScopeReqVO;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.permission.PermissionAssignRoleMenuReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantMenuHandler;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

/** Controller preconditions only: service collaborators are doubles; this is not HTTP/RBAC proof. */
@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {
    @InjectMocks private PermissionController controller;
    @Mock private PermissionService permissions;
    @Mock private RoleService roleService;
    @Mock private DeptService deptService;
    @Mock private TenantService tenantService;
    @Mock private AdminUserService userService;

    private void error(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void invisibleRoleCannotReadMenuAssignments() {
        error(ROLE_NOT_EXISTS.getCode(), () -> controller.getRoleMenuList(900L));
        verifyNoInteractions(permissions);
    }

    @Test
    void invisibleRoleCannotReceiveMenuOrDataGrants() {
        var menus =
                new PermissionAssignRoleMenuReqVO()
                        .setRoleId(900L)
                        .setMenuIds(new HashSet<>(Set.of(10L)));
        error(ROLE_NOT_EXISTS.getCode(), () -> controller.assignRoleMenu(menus));
        var scope =
                new PermissionAssignRoleDataScopeReqVO()
                        .setRoleId(900L)
                        .setDataScope(2)
                        .setDataScopeDeptIds(Set.of(20L));
        error(ROLE_NOT_EXISTS.getCode(), () -> controller.assignRoleDataScope(scope));
        verifyNoInteractions(permissions, tenantService);
    }

    @Test
    void customScopeRejectsTheWholeSetWhenAnyDepartmentIsInvisible() {
        lenient().when(roleService.getRole(1L)).thenReturn(new RoleDO().setId(1L));
        lenient()
                .when(deptService.getDeptList(Set.of(20L, 900L)))
                .thenReturn(List.of(new DeptDO().setId(20L)));
        var scope =
                new PermissionAssignRoleDataScopeReqVO()
                        .setRoleId(1L)
                        .setDataScope(2)
                        .setDataScopeDeptIds(Set.of(20L, 900L));
        error(DEPT_NOT_FOUND.getCode(), () -> controller.assignRoleDataScope(scope));
        verifyNoInteractions(permissions);
    }

    @Test
    void validMenuGrantRetainsTheExistingTenantPackageFilter() {
        lenient().when(roleService.getRole(1L)).thenReturn(new RoleDO().setId(1L));
        doAnswer(
                        call -> {
                            ((TenantMenuHandler) call.getArgument(0)).handle(Set.of(10L));
                            return null;
                        })
                .when(tenantService)
                .handleTenantMenu(any());
        var menus =
                new PermissionAssignRoleMenuReqVO()
                        .setRoleId(1L)
                        .setMenuIds(new HashSet<>(Set.of(10L, 900L)));
        assertTrue(controller.assignRoleMenu(menus).getData());
        verify(permissions).assignRoleMenu(1L, Set.of(10L));
    }

    @Test
    void visibleCustomDepartmentsArePreservedAndNonCustomScopeClearsHiddenIds() {
        lenient().when(roleService.getRole(1L)).thenReturn(new RoleDO().setId(1L));
        lenient()
                .when(deptService.getDeptList(Set.of(20L)))
                .thenReturn(List.of(new DeptDO().setId(20L)));
        var custom =
                new PermissionAssignRoleDataScopeReqVO()
                        .setRoleId(1L)
                        .setDataScope(2)
                        .setDataScopeDeptIds(Set.of(20L));
        assertTrue(controller.assignRoleDataScope(custom).getData());
        verify(permissions).assignRoleDataScope(1L, 2, Set.of(20L));
        var self =
                new PermissionAssignRoleDataScopeReqVO()
                        .setRoleId(1L)
                        .setDataScope(5)
                        .setDataScopeDeptIds(Set.of(900L));
        assertTrue(controller.assignRoleDataScope(self).getData());
        verify(permissions).assignRoleDataScope(1L, 5, Set.of());
    }
}
