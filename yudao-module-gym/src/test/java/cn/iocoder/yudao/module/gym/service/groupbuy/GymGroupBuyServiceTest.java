package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.STORE_NO_PERMISSION;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryReqVO;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanCouponClient;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GymGroupBuyServiceTest {
    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void checksStoreBeforeAccessingProviderAndUsesContextTenant() {
        var stores = mock(GymStoreService.class);
        var client = mock(GymMeituanCouponClient.class);
        var service = service(stores, client);
        var req = new GymMeituanCouponHistoryReqVO();
        req.setStoreId(12L);
        req.setDate("2026-10-19");
        TenantContextHolder.setTenantId(7L);
        service.queryMeituanHistory(req);
        var order = inOrder(stores, client);
        order.verify(stores).requireStore(12L, false);
        order.verify(client)
                .queryHistory(
                        eq(7L),
                        eq(12L),
                        argThat(
                                request ->
                                        request.getDate().equals(req.getDate())
                                                && request.getOffset().equals(req.getOffset())
                                                && request.getLimit().equals(req.getLimit())));
    }

    @Test
    void forbiddenStoreNeverReachesProvider() {
        var stores = mock(GymStoreService.class);
        var client = mock(GymMeituanCouponClient.class);
        when(stores.requireStore(12L, false)).thenThrow(exception(STORE_NO_PERMISSION));
        var req = new GymMeituanCouponHistoryReqVO();
        req.setStoreId(12L);
        req.setDate("2026-10-19");
        var service = service(stores, client);
        assertThrows(ServiceException.class, () -> service.queryMeituanHistory(req));
        verifyNoInteractions(client);
    }

    private GymGroupBuyService service(GymStoreService stores, GymMeituanCouponClient client) {
        var service = new GymGroupBuyService();
        ReflectionTestUtils.setField(service, "stores", stores);
        ReflectionTestUtils.setField(service, "meituan", client);
        return service;
    }
}
