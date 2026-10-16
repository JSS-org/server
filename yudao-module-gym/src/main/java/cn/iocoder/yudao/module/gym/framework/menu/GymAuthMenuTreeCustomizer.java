package cn.iocoder.yudao.module.gym.framework.menu;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO.MenuVO;
import cn.iocoder.yudao.module.system.service.auth.AuthMenuTreeCustomizer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按 gym.ts 分类已授权菜单。只适配认证响应，不修改菜单实体、角色授权或接口权限码。
 * 当前客户端会直接拼接父子路径，因此另保留隐藏的原地址，供收藏和业务页内链接使用。
 */
@Component
public class GymAuthMenuTreeCustomizer implements AuthMenuTreeCustomizer {

    private record Page(String path, String title, boolean hidden) {
        Page(String path, String title) {
            this(path, title, false);
        }
    }

    private record Group(
            String path, String title, String icon, int order, boolean hidden, List<Page> pages) {}

    private static final List<Group> GROUPS =
            List.of(
                    new Group(
                            "store-management",
                            "门店管理",
                            "lucide:building-2",
                            0,
                            false,
                            List.of(
                                    new Page("stores", "门店资料"), new Page("checkins", "签到记录"),
                                    new Page("qr-codes", "小程序 / App 码", true),
                                            new Page("reviews", "门店评价", true))),
                    new Group(
                            "staff",
                            "员工管理",
                            "lucide:contact-round",
                            1,
                            false,
                            List.of(
                                    new Page("employees", "员工资料"),
                                    new Page("roles", "角色权限"),
                                    new Page("salary-templates", "薪资模板"),
                                    new Page("employee-salary-assignments", "员工薪资关联", true),
                                    new Page("payrolls", "门店工资表", true))),
                    new Group(
                            "membership",
                            "会员管理",
                            "lucide:users",
                            2,
                            false,
                            List.of(
                                    new Page("members", "会员资料"),
                                    new Page("body-measurements", "体测记录"),
                                    new Page("blacklists", "黑名单"))),
                    new Group(
                            "membership-cards",
                            "会员卡管理",
                            "lucide:credit-card",
                            3,
                            false,
                            List.of(
                                    new Page("cards", "会员卡模板"),
                                    new Page("settings", "售卡与核销设置"),
                                    new Page("self-service-card-orders", "自主办卡记录"),
                                    new Page("subscriptions", "连续包月记录"),
                                    new Page("verifications", "自助核销记录"))),
                    new Group(
                            "training",
                            "课程管理",
                            "lucide:calendar-days",
                            4,
                            false,
                            List.of(
                                    new Page("course-products", "课程产品"),
                                            new Page("course-sessions", "课程安排"),
                                    new Page("bookings", "预约管理"),
                                            new Page("lesson-records", "上课记录"))),
                    new Group(
                            "contract-management",
                            "合同管理",
                            "lucide:file-text",
                            5,
                            false,
                            List.of(
                                    new Page("contract-templates", "合同模板"),
                                    new Page("contracts", "合同记录"))),
                    new Group(
                            "approval-management",
                            "审批管理",
                            "lucide:file-check",
                            6,
                            false,
                            List.of(
                                    new Page("approvals", "审批记录"),
                                    new Page("approval-templates", "审批模板"))),
                    new Group(
                            "hardware",
                            "设备管理",
                            "lucide:door-open",
                            7,
                            false,
                            List.of(new Page("devices", "智能设备"), new Page("equipment", "器械教学"))),
                    new Group(
                            "coupons",
                            "优惠券管理",
                            "lucide:ticket",
                            8,
                            false,
                            List.of(
                                    new Page("coupon-templates", "优惠券模板"),
                                    new Page("coupon-store-quotas", "优惠券额度管理"),
                                    new Page("coupon-quota-batches", "一键维护：门店额度", true),
                                    new Page("coupon-batches", "批量发券"),
                                    new Page("coupon-quota-ledger", "券额度流水", true),
                                    new Page("member-coupons", "会员优惠券"),
                                    new Page("gift-code-batches", "礼品码生成"))),
                    new Group(
                            "points",
                            "积分管理",
                            "lucide:coins",
                            9,
                            false,
                            List.of(
                                    new Page("point-rules", "积分规则"),
                                            new Page("point-products", "积分兑换"),
                                    new Page("point-ledger", "积分流水"),
                                            new Page("point-redemptions", "兑换记录"))),
                    new Group(
                            "communications",
                            "消息管理",
                            "lucide:messages-square",
                            10,
                            true,
                            List.of(
                                    new Page("message-batches", "私信与批量通知"),
                                    new Page("feedback", "匿名反馈"))),
                    new Group(
                            "content",
                            "内容管理",
                            "lucide:megaphone",
                            11,
                            false,
                            List.of(
                                    new Page("client-settings", "展示配置"),
                                    new Page("activities", "门店活动"))),
                    new Group(
                            "franchise",
                            "加盟管理",
                            "lucide:handshake",
                            12,
                            true,
                            List.of(
                                    new Page("franchise-brand", "加盟品牌与模型"),
                                    new Page("franchise-inquiries", "加盟咨询"))),
                    new Group(
                            "finance",
                            "财务管理",
                            "lucide:wallet",
                            13,
                            false,
                            List.of(
                                    new Page("payment-accounts", "收款账户"),
                                    new Page("settlement-rules", "分账规则"),
                                    new Page("settlements", "分账结算记录"))),
                    new Group(
                            "reports",
                            "统计报表",
                            "lucide:chart-no-axes-combined",
                            14,
                            false,
                            List.of(
                                    new Page("reports/performance", "门店与员工业绩"),
                                    new Page("reports/members", "会员汇总"),
                                    new Page("reports/member-cards", "会员卡报表"))));

    @Override
    public List<MenuVO> customize(List<MenuVO> menus) {
        List<MenuVO> result = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();
        collectIds(menus, usedIds);
        for (MenuVO menu : menus) {
            // 只接管现有 gym 根目录；其他系统目录及人工新增的未知子菜单原样保留。
            if (!("gym".equals(menu.getPath()) || "/gym".equals(menu.getPath()))
                    || (menu.getComponent() != null && !menu.getComponent().isEmpty())
                    || menu.getChildren() == null) {
                result.add(menu);
                continue;
            }
            Map<String, MenuVO> pages = new LinkedHashMap<>();
            for (MenuVO child : menu.getChildren()) {
                String path = child.getPath();
                String key = path != null && path.startsWith("/gym/") ? path.substring(5) : path;
                if (key != null
                        && ("gym/" + key + "/index").equals(child.getComponent())
                        && (child.getChildren() == null || child.getChildren().isEmpty())
                        && !pages.containsKey(key)) {
                    pages.put(key, child);
                }
            }
            Set<Long> classified = new HashSet<>();
            MenuVO home = pages.get("home");
            if (home != null) {
                MenuVO copy = copyPage(home, 0L, "/gym/home");
                copy.setName("首页");
                copy.setIcon("lucide:house");
                copy.setSort(-1);
                copy.setVisible(
                        Boolean.TRUE.equals(menu.getVisible())
                                && Boolean.TRUE.equals(home.getVisible()));
                result.add(copy);
                classified.add(home.getId());
            }
            for (Group group : GROUPS) {
                List<MenuVO> children = new ArrayList<>();
                long groupId = unusedId(-1_000_000L - group.order(), usedIds);
                for (int i = 0; i < group.pages().size(); i++) {
                    Page page = group.pages().get(i);
                    MenuVO original = pages.get(page.path());
                    if (original == null) {
                        continue;
                    }
                    String childPath =
                            page.path().startsWith(group.path() + "/")
                                    ? page.path().substring(group.path().length() + 1)
                                    : page.path();
                    MenuVO child = copyPage(original, groupId, childPath);
                    child.setName(page.title());
                    child.setSort(i);
                    child.setVisible(Boolean.TRUE.equals(original.getVisible()) && !page.hidden());
                    children.add(child);
                    classified.add(original.getId());
                    // 报表分类本就与原地址一致，避免注册同地址的重复路由。
                    if ((group.path() + "/" + childPath).equals(page.path())) {
                        continue;
                    }
                    MenuVO legacy = copyPage(original, 0L, "/gym/" + page.path());
                    legacy.setId(unusedId(-2_000_000L - original.getId(), usedIds));
                    legacy.setComponentName(
                            (original.getComponentName() == null
                                            ? "Gym" + original.getId()
                                            : original.getComponentName())
                                    + "Legacy");
                    legacy.setVisible(false);
                    result.add(legacy);
                }
                if (!children.isEmpty()) {
                    result.add(
                            MenuVO.builder()
                                    .id(groupId)
                                    .parentId(0L)
                                    .name(group.title())
                                    .path("/gym/" + group.path())
                                    .componentName("GymGroup-" + group.path())
                                    .icon(group.icon())
                                    .sort(group.order())
                                    .visible(
                                            Boolean.TRUE.equals(menu.getVisible())
                                                    && !group.hidden()
                                                    && children.stream()
                                                            .anyMatch(
                                                                    child ->
                                                                            Boolean.TRUE.equals(
                                                                                    child
                                                                                            .getVisible())))
                                    .keepAlive(false)
                                    .alwaysShow(true)
                                    .children(children)
                                    .build());
                }
            }
            List<MenuVO> remaining =
                    menu.getChildren().stream()
                            .filter(child -> !classified.contains(child.getId()))
                            .toList();
            if (!remaining.isEmpty()) {
                MenuVO remainingRoot = BeanUtils.toBean(menu, MenuVO.class);
                remainingRoot.setChildren(remaining);
                result.add(remainingRoot);
            }
        }
        result.sort(
                Comparator.comparing(
                        MenuVO::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private static MenuVO copyPage(MenuVO original, Long parentId, String path) {
        MenuVO copy = BeanUtils.toBean(original, MenuVO.class);
        copy.setParentId(parentId);
        copy.setPath(path);
        return copy;
    }

    private static long unusedId(long candidate, Set<Long> usedIds) {
        while (!usedIds.add(candidate)) {
            candidate--;
        }
        return candidate;
    }

    private static void collectIds(List<MenuVO> menus, Set<Long> ids) {
        for (MenuVO menu : menus) {
            ids.add(menu.getId());
            if (menu.getChildren() != null) {
                collectIds(menu.getChildren(), ids);
            }
        }
    }
}
