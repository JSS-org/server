package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCatalogService;

import jakarta.annotation.security.PermitAll;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileCatalogControllerTest {
    GymMobileCatalogService catalog;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        catalog = mock(GymMobileCatalogService.class);
        var controller = new AppGymCatalogController();
        ReflectionTestUtils.setField(controller, "catalog", catalog);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "catalog-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void publicRoutesBindStoreCoachAndDetailType() throws Exception {
        assertTrue(AppGymCatalogController.class.isAnnotationPresent(PermitAll.class));
        for (String path : List.of("card-products", "course-products", "subscription-products"))
            assertEquals(
                    0,
                    code(
                            mvc.perform(
                                    get("/gym/" + path)
                                            .param("storeId", "101")
                                            .param("coachId", "91")
                                            .param("pageNo", "2")
                                            .param("pageSize", "5"))));
        verify(catalog)
                .cards(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getPageNo() == 2
                                                && r.getPageSize() == 5));
        verify(catalog).courses(argThat(r -> r.getStoreId() == 101L && r.getCoachId() == 91L));
        verify(catalog).subscriptions(argThat(r -> r.getStoreId() == 101L));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/products/7")
                                        .param("productType", "card")
                                        .param("storeId", "101"))));
        verify(catalog).product(7L, "card", 101L);
        assertEquals(
                0, code(mvc.perform(get("/gym/products/7").param("productType", "subscription"))));
        verify(catalog).product(7L, "subscription", null);
    }

    @Test
    void allListsRequireValidStoreAndBoundedPageAndDetailRequiresType() throws Exception {
        for (String path : List.of("card-products", "course-products", "subscription-products")) {
            assertEquals(400, code(mvc.perform(get("/gym/" + path))));
            assertEquals(400, code(mvc.perform(get("/gym/" + path).param("storeId", "0"))));
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/" + path)
                                            .param("storeId", "101")
                                            .param("pageSize", "-1"))));
        }
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/course-products")
                                        .param("storeId", "101")
                                        .param("coachId", "0"))));
        assertEquals(400, code(mvc.perform(get("/gym/products/7"))));
        verifyNoInteractions(catalog);
    }
}
