package cn.iocoder.yudao.module.pay.controller.admin.wallet;

import static cn.iocoder.yudao.framework.common.enums.UserTypeEnum.MEMBER;
import static cn.iocoder.yudao.module.pay.enums.wallet.PayWalletBizTypeEnum.UPDATE_BALANCE;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.controller.admin.wallet.vo.wallet.PayWalletUpdateBalanceReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.wallet.PayWalletDO;
import cn.iocoder.yudao.module.pay.service.wallet.PayWalletService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

class PayWalletControllerTest extends BaseMockitoUnitTest {
    @InjectMocks private PayWalletController controller;
    @Mock private PayWalletService walletService;

    @Test
    void eachManualBalanceAdjustmentHasItsOwnBusinessId() {
        when(walletService.getOrCreateWallet(23006L, MEMBER.getValue()))
                .thenReturn(new PayWalletDO().setId(123L));
        assertTrue(
                controller
                        .updateWalletBalance(
                                new PayWalletUpdateBalanceReqVO().setUserId(23006L).setBalance(100))
                        .getData());
        assertTrue(
                controller
                        .updateWalletBalance(
                                new PayWalletUpdateBalanceReqVO().setUserId(23006L).setBalance(-50))
                        .getData());
        var ids = ArgumentCaptor.forClass(String.class);
        var amounts = ArgumentCaptor.forClass(Integer.class);
        verify(walletService, times(2))
                .addWalletBalance(eq(123L), ids.capture(), eq(UPDATE_BALANCE), amounts.capture());
        assertNotEquals(ids.getAllValues().get(0), ids.getAllValues().get(1));
        assertEquals(List.of(100, -50), amounts.getAllValues());
        for (String id : ids.getAllValues()) {
            assertNotNull(id);
            assertFalse(id.isBlank());
            assertNotEquals("23006", id);
        }
    }
}
