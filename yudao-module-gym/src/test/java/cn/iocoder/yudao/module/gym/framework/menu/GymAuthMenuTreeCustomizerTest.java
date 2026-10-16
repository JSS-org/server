package cn.iocoder.yudao.module.gym.framework.menu;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO.MenuVO;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class GymAuthMenuTreeCustomizerTest {

    private final GymAuthMenuTreeCustomizer customizer = new GymAuthMenuTreeCustomizer();

    @Test
    void categorizesAllDeliveredPagesAndKeepsEveryOriginalUrl() {
        String[] paths = {
            "home",
            "stores",
            "checkins",
            "qr-codes",
            "employees",
            "salary-templates",
            "employee-salary-assignments",
            "payrolls",
            "members",
            "body-measurements",
            "blacklists",
            "cards",
            "settings",
            "self-service-card-orders",
            "subscriptions",
            "verifications",
            "course-products",
            "course-sessions",
            "bookings",
            "lesson-records",
            "contract-templates",
            "contracts",
            "approval-templates",
            "approvals",
            "devices",
            "equipment",
            "client-settings",
            "activities",
            "franchise-inquiries",
            "payment-accounts",
            "settlement-rules",
            "settlements",
            "reports/performance",
            "reports/members",
            "reports/member-cards"
        };
        List<MenuVO> pages = new ArrayList<>();
        for (int i = 0; i < paths.length; i++) {
            pages.add(page(100L + i, paths[i]));
        }
        List<MenuVO> result = customizer.customize(List.of(root(pages)));
        assertEquals(12, result.stream().filter(node -> node.getChildren() != null).count());
        assertEquals(
                List.of(
                        "首页", "门店管理", "员工管理", "会员管理", "会员卡管理", "课程管理", "合同管理", "审批管理", "设备管理",
                        "内容管理", "财务管理", "统计报表"),
                result.stream()
                        .filter(node -> Boolean.TRUE.equals(node.getVisible()))
                        .map(MenuVO::getName)
                        .toList());
        Set<String> routePaths = new HashSet<>();
        collectPaths(result, "", routePaths);
        for (String path : paths) {
            assertTrue(routePaths.contains("/gym/" + path), "原地址丢失：" + path);
        }
        assertTrue(routePaths.contains("/gym/store-management/stores"));
        assertFalse(routePaths.contains("/gym/reports/reports/performance"));
        assertEquals(
                66, flatten(result).stream().filter(node -> node.getComponent() != null).count());
        assertEquals(flatten(result).size(), routePaths.size(), "不应重复注册同地址路由");
        assertEquals(
                flatten(result).size(),
                flatten(result).stream().map(MenuVO::getId).distinct().count());
        assertEquals(
                flatten(result).size(),
                flatten(result).stream().map(MenuVO::getComponentName).distinct().count());
    }

    @Test
    void limitedRoleOnlyGetsItsExistingPagesAndNoEmptyCategories() {
        List<MenuVO> result =
                customizer.customize(
                        List.of(
                                root(
                                        List.of(
                                                page(101L, "members"),
                                                page(102L, "course-products")))));
        assertEquals(
                List.of("会员管理", "课程管理"),
                result.stream()
                        .filter(node -> Boolean.TRUE.equals(node.getVisible()))
                        .map(MenuVO::getName)
                        .toList());
        assertEquals(
                Set.of("gym/members/index", "gym/course-products/index"),
                new HashSet<>(
                        flatten(result).stream()
                                .map(MenuVO::getComponent)
                                .filter(component -> component != null)
                                .toList()));
        assertTrue(customizer.customize(List.of()).isEmpty());
        assertTrue(customizer.customize(List.of(root(List.of()))).isEmpty());
    }

    @Test
    void respectsAttachmentHiddenFlagsAndExistingVisibility() {
        MenuVO disabledDisplay = page(101L, "stores");
        disabledDisplay.setVisible(false);
        List<MenuVO> result =
                customizer.customize(
                        List.of(
                                root(
                                        List.of(
                                                disabledDisplay,
                                                page(102L, "qr-codes"),
                                                page(103L, "payrolls"),
                                                page(104L, "employee-salary-assignments"),
                                                page(105L, "franchise-inquiries")))));
        assertTrue(result.stream().noneMatch(node -> Boolean.TRUE.equals(node.getVisible())));
        assertFalse(find(result, "GymGroup-franchise").getVisible());
        assertTrue(
                flatten(result).stream()
                        .filter(node -> node.getComponent() != null)
                        .filter(
                                node ->
                                        !"gym/franchise-inquiries/index"
                                                .equals(node.getComponent()))
                        .noneMatch(node -> Boolean.TRUE.equals(node.getVisible())));
    }

    @Test
    void hiddenGymRootNeverPromotesVisibleEntries() {
        MenuVO root = root(List.of(page(101L, "home"), page(102L, "members")));
        root.setVisible(false);
        assertTrue(
                customizer.customize(List.of(root)).stream()
                        .noneMatch(node -> Boolean.TRUE.equals(node.getVisible())));
    }

    @Test
    void keepsApprovalRecordsFirstAndReportsAtTheirExistingUrls() {
        List<MenuVO> result =
                customizer.customize(
                        List.of(
                                root(
                                        List.of(
                                                page(101L, "approval-templates"),
                                                page(102L, "approvals"),
                                                page(103L, "reports/member-cards")))));
        assertEquals(
                List.of("审批记录", "审批模板"),
                find(result, "GymGroup-approval-management").getChildren().stream()
                        .map(MenuVO::getName)
                        .toList());
        assertEquals(
                "member-cards",
                find(result, "GymGroup-reports").getChildren().getFirst().getPath());
    }

    @Test
    void doesNotMutateInputOrUnrelatedAndCustomMenusAndIsRepeatable() {
        MenuVO member = page(101L, "members");
        MenuVO custom = page(102L, "custom");
        custom.setComponent("custom/index");
        MenuVO root = root(List.of(member, custom));
        MenuVO system =
                MenuVO.builder().id(1L).parentId(0L).path("system").name("系统管理").sort(99).build();
        List<MenuVO> result = customizer.customize(List.of(root, system));
        assertEquals("members", member.getPath());
        assertEquals(1000L, member.getParentId());
        assertEquals(2, root.getChildren().size());
        assertSame(
                system,
                result.stream().filter(node -> node.getId().equals(1L)).findFirst().orElseThrow());
        assertSame(
                custom,
                result.stream()
                        .filter(node -> node.getId().equals(1000L))
                        .findFirst()
                        .orElseThrow()
                        .getChildren()
                        .getFirst());
        assertEquals(result, customizer.customize(result));
    }

    @Test
    void syntheticDirectoryAndAliasIdsDoNotCollideWithExistingIds() {
        MenuVO existing =
                MenuVO.builder().id(-1_000_002L).parentId(0L).path("other").sort(100).build();
        MenuVO existingAlias =
                MenuVO.builder().id(-2_000_101L).parentId(0L).path("another").sort(101).build();
        List<MenuVO> result =
                flatten(
                        customizer.customize(
                                List.of(
                                        existing,
                                        existingAlias,
                                        root(List.of(page(101L, "members"))))));
        assertEquals(result.size(), result.stream().map(MenuVO::getId).distinct().count());
    }

    private static MenuVO root(List<MenuVO> pages) {
        return MenuVO.builder()
                .id(1000L)
                .parentId(0L)
                .path("gym")
                .name("健身管理")
                .visible(true)
                .sort(90)
                .children(pages)
                .build();
    }

    private static MenuVO page(Long id, String path) {
        return MenuVO.builder()
                .id(id)
                .parentId(1000L)
                .path(path)
                .name(path)
                .componentName("Gym-" + path)
                .component("gym/" + path + "/index")
                .visible(true)
                .sort(10)
                .keepAlive(true)
                .alwaysShow(true)
                .build();
    }

    private static MenuVO find(List<MenuVO> menus, String name) {
        return menus.stream()
                .filter(menu -> name.equals(menu.getComponentName()))
                .findFirst()
                .orElseThrow();
    }

    private static List<MenuVO> flatten(List<MenuVO> menus) {
        List<MenuVO> result = new ArrayList<>();
        for (MenuVO menu : menus) {
            result.add(menu);
            if (menu.getChildren() != null) {
                result.addAll(flatten(menu.getChildren()));
            }
        }
        return result;
    }

    private static void collectPaths(List<MenuVO> menus, String parent, Set<String> paths) {
        for (MenuVO menu : menus) {
            String path = parent.isEmpty() ? menu.getPath() : parent + "/" + menu.getPath();
            paths.add(path);
            if (menu.getChildren() != null) {
                collectPaths(menu.getChildren(), path, paths);
            }
        }
    }
}
