package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.points.GymPointProductsController;
import cn.iocoder.yudao.module.gym.controller.admin.points.vo.GymPointProductVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointProductsVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.points.GymPointProductsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobilePointProductsControllerTest {
    GymPointProductsService service;
    MockMvc mvc;
    MockMvc admin;

    @BeforeEach
    void setup() {
        service = mock(GymPointProductsService.class);
        var app = new AppGymPointProductsController();
        ReflectionTestUtils.setField(app, "service", service);
        var staff = new GymPointProductsController();
        ReflectionTestUtils.setField(staff, "service", service);
        mvc = build(app);
        admin = build(staff);
    }

    MockMvc build(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(
                        new GymMobileExceptionAdvice(),
                        new GlobalExceptionHandler(
                                "point-products", mock(ApiErrorLogCommonApi.class)))
                .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void catalogBindsStorePaginationAndOnlyReturnsFivePublicFields() throws Exception {
        when(service.catalog(any()))
                .thenReturn(
                        new PageResult<>(
                                List.of(new Product("10", "次卡", 9007199254740991L, null, "20")),
                                1L));
        var response =
                mvc.perform(
                        get("/gym/point-products")
                                .param("storeId", "101")
                                .param("pageNo", "2")
                                .param("pageSize", "5")
                                .param("memberId", "999"));
        assertEquals(0, code(response));
        verify(service)
                .catalog(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && q.getPageNo() == 2
                                                && q.getPageSize() == 5));
        var data =
                JsonUtils.parseObject(
                        response.andReturn().getResponse().getContentAsString(), "data", Map.class);
        var row = (Map<?, ?>) ((List<?>) data.get("list")).getFirst();
        assertEquals(
                Set.of("id", "name", "requiredPoints", "stock", "cardProductId"), row.keySet());
        assertEquals("20", row.get("cardProductId"));
        assertNull(row.get("stock"));
        assertEquals(9007199254740991L, ((Number) row.get("requiredPoints")).longValue());
    }

    @Test
    void catalogRejectsMissingStoreInvalidIdsAndPagination() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/point-products"))));
        for (String[] pair :
                List.of(
                        new String[] {"storeId", "0"},
                        new String[] {"pageSize", "201"},
                        new String[] {"pageNo", "0"})) {
            var req = get("/gym/point-products");
            if (!pair[0].equals("storeId")) req.param("storeId", "101");
            assertEquals(400, code(mvc.perform(req.param(pair[0], pair[1]))));
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminUpdateBindsExplicitNullStockAndRevisionButRejectsInvalidCost() throws Exception {
        when(service.save(any(), any()))
                .thenReturn(
                        new GymPointProductVO.Product("10", "101", "20", "次卡", 100, null, true, 2));
        String body =
                "{\"storeId\":101,\"cardProductId\":20,\"name\":\"次卡\",\"requiredPoints\":100,\"stock\":null,\"enabled\":true,\"revision\":1}";
        assertEquals(
                0,
                code(
                        admin.perform(
                                put("/gym/point-products/10")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))));
        verify(service)
                .save(
                        eq(10L),
                        argThat(
                                q ->
                                        q.getStock() == null
                                                && q.getRevision() == 1L
                                                && q.getStoreId() == 101L));
        clearInvocations(service);
        assertEquals(
                400,
                code(
                        admin.perform(
                                post("/gym/point-products")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                body.replace(
                                                        "\"requiredPoints\":100",
                                                        "\"requiredPoints\":0")))));
        verifyNoInteractions(service);
    }
}
