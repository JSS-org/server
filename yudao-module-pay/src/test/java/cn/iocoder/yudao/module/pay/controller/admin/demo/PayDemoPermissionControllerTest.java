package cn.iocoder.yudao.module.pay.controller.admin.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayRefundNotifyReqDTO;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayTransferNotifyReqDTO;
import cn.iocoder.yudao.module.pay.controller.admin.demo.vo.order.PayDemoOrderCreateReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.demo.vo.withdraw.PayDemoWithdrawCreateReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.order.PayOrderController;
import cn.iocoder.yudao.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.demo.PayDemoOrderService;
import cn.iocoder.yudao.module.pay.service.demo.PayDemoWithdrawService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.wallet.PayWalletService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Production controllers and method-security/permission ceiling; no real money or provider calls. */
class PayDemoPermissionControllerTest {
    private AnnotationConfigApplicationContext context;
    private PayDemoOrderController orders;
    private PayDemoWithdrawController withdrawals;
    private PayDemoOrderService orderService;
    private PayDemoWithdrawService withdrawService;
    private PermissionCommonApi permissions;
    private PayOrderController cashier;
    private PayOrderService payOrders;
    private PayWalletService wallets;
    private final Set<String> grants = new HashSet<>();

    private record Action(String permission, Supplier<?> invoke) {}

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        context = new AnnotationConfigApplicationContext(Config.class);
        orders = context.getBean(PayDemoOrderController.class);
        withdrawals = context.getBean(PayDemoWithdrawController.class);
        orderService = context.getBean(PayDemoOrderService.class);
        withdrawService = context.getBean(PayDemoWithdrawService.class);
        permissions = context.getBean(PermissionCommonApi.class);
        cashier = context.getBean(PayOrderController.class);
        payOrders = context.getBean(PayOrderService.class);
        wallets = context.getBean(PayWalletService.class);
        assertTrue(AopUtils.isAopProxy(orders));
        assertTrue(AopUtils.isAopProxy(withdrawals));
        when(permissions.hasAnyPermissions(eq(20L), any(String[].class)))
                .thenAnswer(
                        invocation ->
                                grants.contains("*:*:*")
                                        || Arrays.stream((String[]) invocation.getRawArguments()[1])
                                                .anyMatch(grants::contains));
        when(orderService.getDemoOrderPage(any(PageParam.class))).thenReturn(PageResult.empty());
        when(withdrawService.getDemoWithdrawPage(any(PageParam.class)))
                .thenReturn(PageResult.empty());
        when(orderService.createDemoOrder(anyLong(), any(PayDemoOrderCreateReqVO.class)))
                .thenReturn(99L);
        when(withdrawService.createDemoWithdraw(any(PayDemoWithdrawCreateReqVO.class)))
                .thenReturn(100L);
        when(withdrawService.transferDemoWithdraw(anyLong(), anyLong())).thenReturn(101L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private void login(Set<String> ceiling) {
        var user =
                new LoginUser()
                        .setId(20L)
                        .setUserType(UserTypeEnum.ADMIN.getValue())
                        .setTenantId(1L)
                        .setPermissionLimits(ceiling);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private List<Action> actions() {
        return List.of(
                new Action("pay:demo-order:query", () -> orders.getDemoOrderPage(new PageParam())),
                new Action(
                        "pay:demo-order:create",
                        () -> orders.createDemoOrder(new PayDemoOrderCreateReqVO().setSpuId(1L))),
                new Action("pay:demo-order:refund", () -> orders.refundDemoOrder(42L)),
                new Action(
                        "pay:demo-withdraw:query",
                        () -> withdrawals.getDemoWithdrawPage(new PageParam())),
                new Action(
                        "pay:demo-withdraw:create",
                        () ->
                                withdrawals.createDemoWithdraw(
                                        new PayDemoWithdrawCreateReqVO()
                                                .setSubject("test")
                                                .setPrice(100)
                                                .setType(3)
                                                .setUserAccount("20"))),
                new Action(
                        "pay:demo-withdraw:transfer", () -> withdrawals.transferDemoWithdraw(43L)));
    }

    private void denied(Supplier<?> action) {
        var error = assertThrows(RuntimeException.class, action::get);
        assertTrue(
                error instanceof AccessDeniedException
                        || error instanceof AuthenticationCredentialsNotFoundException,
                error.toString());
    }

    @Test
    void cashierSubmissionRequiresItsOwnNativeAndWorkspacePermissionBeforeWalletOrPaymentWork() {
        var request = new PayOrderSubmitReqVO().setId(9007199254740993L).setChannelCode("mock");
        denied(() -> cashier.submitPayOrder(request));
        grants.add("pay:order:query");
        login(Set.copyOf(grants));
        denied(() -> cashier.submitPayOrder(request));
        grants.add("pay:order:submit");
        denied(() -> cashier.submitPayOrder(request));
        login(Set.of("pay:order:submit"));
        grants.remove("pay:order:submit");
        denied(() -> cashier.submitPayOrder(request));
        grants.add("pay:order:submit");
        login(Set.of());
        denied(() -> cashier.submitPayOrder(request));
        verifyNoInteractions(payOrders, wallets);
        login(Set.of("pay:order:submit"));
        assertNotNull(cashier.submitPayOrder(request));
        verify(payOrders).submitOrder(eq(request), any());
        verifyNoInteractions(wallets);
    }

    @Test
    void everyOperationRequiresBothNativeGrantAndTheSameWorkspaceCapability() {
        for (Action action : actions()) {
            grants.clear();
            grants.add(action.permission());
            login(Set.of(action.permission()));
            assertNotNull(action.invoke().get());
            clearInvocations(orderService, withdrawService);
            grants.clear();
            denied(action.invoke());
            grants.add(action.permission());
            login(Set.of());
            denied(action.invoke());
            verifyNoInteractions(orderService, withdrawService);
        }
    }

    @Test
    void queryOnlyDoesNotAuthorizeWrites() {
        grants.addAll(Set.of("pay:demo-order:query", "pay:demo-withdraw:query"));
        login(Set.copyOf(grants));
        orders.getDemoOrderPage(new PageParam());
        withdrawals.getDemoWithdrawPage(new PageParam());
        clearInvocations(orderService, withdrawService);
        for (Action action : actions())
            if (!action.permission().endsWith(":query")) denied(action.invoke());
        verifyNoInteractions(orderService, withdrawService);
    }

    @Test
    void oneWriteCapabilityDoesNotGrantReadsOrAnotherWrite() {
        grants.add("pay:demo-order:refund");
        login(Set.copyOf(grants));
        assertTrue(orders.refundDemoOrder(42L).getData());
        verify(orderService).refundDemoOrder(eq(42L), isNull());
        clearInvocations(orderService, withdrawService);
        for (Action action : actions())
            if (!action.permission().equals("pay:demo-order:refund")) denied(action.invoke());
        verifyNoInteractions(orderService, withdrawService);
    }

    @Test
    void emptyWorkspaceCeilingCannotBeOverriddenByNativeWildcardGrants() {
        grants.add("*:*:*");
        login(Set.of());
        for (Action action : actions()) denied(action.invoke());
        verifyNoInteractions(orderService, withdrawService, permissions);
    }

    @Test
    void nativeAdminWithoutCeilingStillRequiresAnExplicitBusinessGrantAndKeepsTheAdminActor() {
        login(null);
        for (Action action : actions()) denied(action.invoke());
        grants.addAll(Set.of("pay:demo-order:create", "pay:demo-withdraw:transfer"));
        assertEquals(
                99L, orders.createDemoOrder(new PayDemoOrderCreateReqVO().setSpuId(1L)).getData());
        assertEquals(101L, withdrawals.transferDemoWithdraw(43L).getData());
        verify(orderService).createDemoOrder(eq(20L), any(PayDemoOrderCreateReqVO.class));
        verify(withdrawService).transferDemoWithdraw(43L, 20L);
    }

    @Test
    void anonymousInteractiveCallsNeverReachBusinessServices() {
        for (Action action : actions()) denied(action.invoke());
        verifyNoInteractions(orderService, withdrawService);
    }

    @Test
    void providerNotificationsKeepTheirExistingPermitAllDelegationPath() {
        assertTrue(
                orders.updateDemoOrderPaid(
                                new PayOrderNotifyReqDTO()
                                        .setMerchantOrderId("42")
                                        .setPayOrderId(99L))
                        .getData());
        assertTrue(
                orders.updateDemoOrderRefunded(
                                new PayRefundNotifyReqDTO()
                                        .setMerchantOrderId("42")
                                        .setMerchantRefundId("refund-42")
                                        .setPayRefundId(100L))
                        .getData());
        assertTrue(
                withdrawals
                        .updateDemoWithdrawTransferred(
                                new PayTransferNotifyReqDTO()
                                        .setMerchantTransferId("43")
                                        .setPayTransferId(101L))
                        .getData());
        verify(orderService).updateDemoOrderPaid(42L, 99L);
        verify(orderService).updateDemoOrderRefunded(42L, "refund-42", 100L);
        verify(withdrawService).updateDemoWithdrawTransferred(43L, 101L);
        verifyNoInteractions(permissions);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true, jsr250Enabled = true)
    static class Config {
        @Bean
        PayOrderService payOrders() {
            return mock(PayOrderService.class);
        }

        @Bean
        PayWalletService wallets() {
            return mock(PayWalletService.class);
        }

        @Bean
        PayAppService appService() {
            return mock(PayAppService.class);
        }

        @Bean
        PayOrderController cashier(PayOrderService service, PayWalletService wallets) {
            var controller = new PayOrderController();
            ReflectionTestUtils.setField(controller, "orderService", service);
            ReflectionTestUtils.setField(controller, "payWalletService", wallets);
            return controller;
        }

        @Bean
        PayDemoOrderService demoOrders() {
            return mock(PayDemoOrderService.class);
        }

        @Bean
        PayDemoWithdrawService withdrawService() {
            return mock(PayDemoWithdrawService.class);
        }

        @Bean
        PermissionCommonApi permissions() {
            return mock(PermissionCommonApi.class);
        }

        @Bean("ss")
        SecurityFrameworkService security(PermissionCommonApi permissions) {
            return new SecurityFrameworkServiceImpl(permissions);
        }

        @Bean
        PayDemoOrderController orders(PayDemoOrderService service) {
            var controller = new PayDemoOrderController();
            ReflectionTestUtils.setField(controller, "payDemoOrderService", service);
            return controller;
        }

        @Bean
        PayDemoWithdrawController withdrawals(PayDemoWithdrawService service) {
            var controller = new PayDemoWithdrawController();
            ReflectionTestUtils.setField(controller, "demoWithdrawService", service);
            return controller;
        }
    }
}
