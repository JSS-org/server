package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Record;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePointsService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobilePointsControllerTest {
    GymMobilePointsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobilePointsService.class);
        var controller = new AppGymPointsController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "points", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void balanceReturnsOnlyExactNumericTotalsAndCannotSelectAnotherMember() throws Exception {
        when(service.balance()).thenReturn(new Balance(-10, 9007199254740981L, 9007199254740991L));
        var result = mvc.perform(get("/gym/me/points").param("memberId", "999"));
        assertEquals(0, code(result));
        var data =
                JsonUtils.parseObject(
                        result.andReturn().getResponse().getContentAsString(), "data", Map.class);
        assertEquals(Set.of("balance", "totalEarned", "totalSpent"), data.keySet());
        assertEquals(-10L, ((Number) data.get("balance")).longValue());
        assertEquals(9007199254740991L, ((Number) data.get("totalSpent")).longValue());
        verify(service).balance();
    }

    @Test
    void historyBindsTypeAndPaginationAndExposesOnlyFiveFields() throws Exception {
        when(service.records(any()))
                .thenReturn(
                        new PageResult<>(
                                List.of(
                                        new Record(
                                                "17", 20, 90, "兑换失败返还", "2026-10-26T08:00+08:00")),
                                1L));
        var result =
                mvc.perform(
                        get("/gym/me/point-records")
                                .param("type", "refund")
                                .param("pageNo", "2")
                                .param("pageSize", "10")
                                .param("memberId", "999"));
        assertEquals(0, code(result));
        verify(service)
                .records(
                        argThat(
                                req ->
                                        "refund".equals(req.getType())
                                                && req.getPageNo() == 2
                                                && req.getPageSize() == 10));
        var page =
                JsonUtils.parseObject(
                        result.andReturn().getResponse().getContentAsString(), "data", Map.class);
        var row = (Map<?, ?>) ((List<?>) page.get("list")).getFirst();
        assertEquals(Set.of("id", "delta", "balanceAfter", "reason", "createdAt"), row.keySet());
        assertEquals("17", row.get("id"));
        assertEquals(20L, ((Number) row.get("delta")).longValue());
    }

    @Test
    void rejectsInvalidTypeAndPaginationBeforeQuery() throws Exception {
        for (String[] pair :
                List.of(
                        new String[] {"type", "unknown"},
                        new String[] {"type", ""},
                        new String[] {"pageSize", "201"},
                        new String[] {"pageSize", "0"},
                        new String[] {"pageNo", "0"}))
            assertEquals(
                    400, code(mvc.perform(get("/gym/me/point-records").param(pair[0], pair[1]))));
        verifyNoInteractions(service);
    }
}
