package cn.iocoder.yudao.module.statistics.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.pojo.SortablePageParam;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.statistics.controller.admin.product.vo.ProductStatisticsReqVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.product.ProductStatisticsDO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.trade.TradeStatisticsDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.product.ProductStatisticsMapper;
import cn.iocoder.yudao.module.statistics.dal.mysql.trade.TradeStatisticsMapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.injector.MPJSqlInjector;
import com.github.yulichang.interceptor.MPJInterceptor;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Runs only through the owned-MySQL wrapper; no Spring, H2 or mapper doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/jss_statistics_mapper_verify_[0-9a-f]+.*")
class JssStatisticsMySqlMapperIT {
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void configureActualMappers() throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(
                new Environment(
                        "jss-isolated-mysql",
                        new JdbcTransactionFactory(),
                        new UnpooledDataSource(
                                "com.mysql.cj.jdbc.Driver",
                                System.getenv("JSS_TEST_MYSQL_URL"),
                                System.getenv("JSS_TEST_MYSQL_USER"),
                                System.getenv("JSS_TEST_MYSQL_PASSWORD"))));
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfigUtils.setGlobalConfig(
                configuration,
                new GlobalConfig()
                        .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                        .setMetaObjectHandler(new DefaultDBFieldHandler())
                        .setSqlInjector(new MPJSqlInjector()));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(
                new TenantLineInnerInterceptor(
                        new TenantDatabaseInterceptor(new TenantProperties())));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor());
        configuration.addInterceptor(plugins);
        configuration.addInterceptor(new MPJInterceptor());
        configuration.addMapper(ProductStatisticsMapper.class);
        configuration.addMapper(TradeStatisticsMapper.class);
        for (String resource :
                List.of(
                        "mapper/product/ProductStatisticsMapper.xml",
                        "mapper/trade/TradeStatisticsMapper.xml")) {
            try (var reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(
                                reader, configuration, resource, configuration.getSqlFragments())
                        .parse();
            }
        }
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void tradeXmlAggregatesEnforceTenantDeletionAndSignedArithmetic() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(TradeStatisticsMapper.class);
            mapper.insert(
                    new TradeStatisticsDO()
                            .setTime(LocalDateTime.of(2026, 10, 20, 12, 0))
                            .setOrderCreateCount(1)
                            .setOrderPayPrice(100)
                            .setRechargePayPrice(30)
                            .setWalletPayPrice(20)
                            .setBrokerageSettlementPrice(-5)
                            .setAfterSaleRefundPrice(10));
            mapper.insert(
                    new TradeStatisticsDO()
                            .setTime(LocalDateTime.of(2026, 10, 21, 12, 0))
                            .setOrderCreateCount(2)
                            .setOrderPayPrice(200)
                            .setWalletPayPrice(10));
            var deleted =
                    new TradeStatisticsDO()
                            .setTime(LocalDateTime.of(2026, 10, 20, 18, 0))
                            .setOrderPayPrice(888);
            deleted.setDeleted(true);
            mapper.insert(deleted);
            TenantContextHolder.setTenantId(2L);
            mapper.insert(
                    new TradeStatisticsDO()
                            .setTime(LocalDateTime.of(2026, 10, 20, 12, 0))
                            .setOrderPayPrice(999));
            TenantContextHolder.setTenantId(1L);
            var begin = LocalDateTime.of(2026, 10, 20, 0, 0);
            var end = LocalDateTime.of(2026, 10, 21, 23, 59, 59);
            var summary =
                    mapper.selectOrderCreateCountSumAndOrderPayPriceSumByTimeBetween(begin, end);
            assertEquals(3, summary.getCount());
            assertEquals(300, summary.getSummary());
            var trend = mapper.selectVoByTimeBetween(begin, end);
            assertEquals(330, trend.getTurnoverPrice());
            assertEquals(35, trend.getExpensePrice());
            assertEquals(35, mapper.selectExpensePriceByTimeBetween(begin, end));
            assertEquals(2, mapper.selectListByTimeBetween(begin, end).size());
            TenantContextHolder.setTenantId(2L);
            assertEquals(
                    999,
                    mapper.selectOrderCreateCountSumAndOrderPayPriceSumByTimeBetween(begin, end)
                            .getSummary());
        }
    }

    @Test
    void productDailyMpJoinAggregatesAndPaginationRespectTenantAndDeletedSnapshots() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(ProductStatisticsMapper.class);
            mapper.insert(
                    new ProductStatisticsDO()
                            .setTime(LocalDate.of(2026, 10, 20))
                            .setSpuId(10L)
                            .setBrowseCount(5)
                            .setFavoriteCount(2)
                            .setOrderPayPrice(100)
                            .setBrowseConvertPercent(20));
            mapper.insert(
                    new ProductStatisticsDO()
                            .setTime(LocalDate.of(2026, 10, 21))
                            .setSpuId(10L)
                            .setBrowseCount(6)
                            .setFavoriteCount(3)
                            .setOrderPayPrice(200)
                            .setBrowseConvertPercent(40));
            var deleted =
                    new ProductStatisticsDO()
                            .setTime(LocalDate.of(2026, 10, 20))
                            .setSpuId(10L)
                            .setBrowseCount(777);
            deleted.setDeleted(true);
            mapper.insert(deleted);
            TenantContextHolder.setTenantId(2L);
            mapper.insert(
                    new ProductStatisticsDO()
                            .setTime(LocalDate.of(2026, 10, 20))
                            .setSpuId(10L)
                            .setBrowseCount(999));
            TenantContextHolder.setTenantId(1L);
            var request =
                    new ProductStatisticsReqVO(
                            new LocalDateTime[] {
                                LocalDateTime.of(2026, 10, 20, 0, 0),
                                LocalDateTime.of(2026, 10, 21, 23, 59, 59)
                            });
            var summary = mapper.selectVoByTimeBetween(request);
            assertEquals(11, summary.getBrowseCount());
            assertEquals(5, summary.getFavoriteCount());
            assertEquals(300, summary.getOrderPayPrice());
            assertEquals(30, summary.getBrowseConvertPercent());
            var daily = mapper.selectListByTimeBetween(request);
            assertEquals(2, daily.size());
            assertEquals(11, daily.stream().mapToInt(ProductStatisticsDO::getBrowseCount).sum());
            var pagination = new SortablePageParam();
            pagination.setPageNo(1);
            pagination.setPageSize(10);
            var result = mapper.selectPageGroupBySpuId(request, pagination);
            assertEquals(1L, result.getTotal());
            assertEquals(10L, result.getList().getFirst().getSpuId());
            assertEquals(11, result.getList().getFirst().getBrowseCount());
            TenantContextHolder.setTenantId(2L);
            assertEquals(999, mapper.selectVoByTimeBetween(request).getBrowseCount());
        }
    }

    @Test
    void sourceProductXmlKeepsSubqueriesAndOrderJoinsInTheCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            // Deliberate cross-tenant references stress every subquery/join in the production XML.
            String fixtures =
                    """
                    INSERT INTO product_spu (id,tenant_id,name,description,category_id) VALUES (1,1,'isolated-one','detail',1),(2,2,'isolated-two','detail',1);
                    INSERT INTO product_browse_history (tenant_id,user_id,spu_id,create_time) VALUES (1,10,1,'2026-10-20 12:00:00'),(1,10,1,'2026-10-20 12:00:00'),(1,11,1,'2026-10-20 12:00:00'),(2,99,1,'2026-10-20 12:00:00');
                    INSERT INTO product_favorite (tenant_id,user_id,spu_id,create_time) VALUES (1,10,1,'2026-10-20 12:00:00'),(2,99,1,'2026-10-20 12:00:00');
                    INSERT INTO trade_cart (tenant_id,user_id,spu_id,sku_id,count,create_time) VALUES (1,10,1,1,2,'2026-10-20 12:00:00'),(2,99,1,1,99,'2026-10-20 12:00:00');
                    INSERT INTO trade_order (id,tenant_id,no,user_id,status,pay_status,create_time) VALUES (1,1,'O-stat-source-1',10,10,b'1','2026-10-20 12:00:00'),(2,2,'O-stat-source-2',99,10,b'1','2026-10-20 12:00:00');
                    INSERT INTO trade_order_item (id,tenant_id,user_id,order_id,spu_id,sku_id,count,pay_price,create_time) VALUES (1,1,10,1,1,1,2,80,'2026-10-20 12:00:00'),(2,2,99,2,1,1,99,99999,'2026-10-20 12:00:00');
                    INSERT INTO trade_after_sale (tenant_id,no,status,user_id,order_id,order_item_id,spu_id,sku_id,count,refund_price,refund_time,create_time) VALUES (1,'AS-stat-source-1',50,10,1,1,1,1,1,40,'2026-10-20 13:00:00','2026-10-20 12:00:00'),(2,'AS-stat-source-2',50,99,2,2,1,1,99,99999,'2026-10-20 13:00:00','2026-10-20 12:00:00');
                    """;
            try (var statement = session.getConnection().createStatement()) {
                for (String sql : fixtures.split(";"))
                    if (!sql.isBlank()) statement.executeUpdate(sql);
            }
            var mapper = session.getMapper(ProductStatisticsMapper.class);
            var result =
                    mapper.selectStatisticsResultPageByTimeBetween(
                            new Page<>(1, 10),
                            LocalDateTime.of(2026, 10, 20, 0, 0),
                            LocalDateTime.of(2026, 10, 20, 23, 59, 59));
            assertEquals(1L, result.getTotal());
            var metrics = result.getRecords().getFirst();
            assertEquals(1L, metrics.getSpuId());
            assertEquals(3, metrics.getBrowseCount());
            assertEquals(2, metrics.getBrowseUserCount());
            assertEquals(1, metrics.getFavoriteCount());
            assertEquals(1, metrics.getCartCount());
            assertEquals(2, metrics.getOrderCount());
            assertEquals(2, metrics.getOrderPayCount());
            assertEquals(80, metrics.getOrderPayPrice());
            assertEquals(1, metrics.getAfterSaleCount());
            assertEquals(40, metrics.getAfterSaleRefundPrice());
        }
    }
}
