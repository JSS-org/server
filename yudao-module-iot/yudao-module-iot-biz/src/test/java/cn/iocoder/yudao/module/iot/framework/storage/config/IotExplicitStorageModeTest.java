package cn.iocoder.yudao.module.iot.framework.storage.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.iot.dal.storage.IotDeviceMessageDAO;
import cn.iocoder.yudao.module.iot.dal.storage.IotDevicePropertyDAO;
import cn.iocoder.yudao.module.iot.dal.storage.IotDisabledTimeSeriesDAO;
import cn.iocoder.yudao.module.iot.framework.storage.core.enums.IotStorageTypeEnum;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class IotExplicitStorageModeTest {
    @Test
    void missingDefaultProviderFailsStartupWithoutInitializingEitherDao() {
        var properties = new IotStorageProperties();
        assertEquals(IotStorageTypeEnum.TDENGINE, properties.getType());
        var messages = mock(IotDeviceMessageDAO.class);
        var attributes = mock(IotDevicePropertyDAO.class);
        var runner = runner(properties, messages, attributes);
        ReflectionTestUtils.setField(runner, "dataSource", new DynamicRoutingDataSource(List.of()));
        assertTrue(assertThrows(IllegalStateException.class, () -> runner.run(null))
                .getMessage().contains("spring.datasource.dynamic.datasource.tdengine"));
        verifyNoInteractions(messages, attributes);
    }

    @Test
    void explicitDisabledModeDoesNotTouchDatabaseOrProvider() {
        var properties = new IotStorageProperties();
        properties.setType(IotStorageTypeEnum.DISABLED);
        var messages = mock(IotDeviceMessageDAO.class);
        var attributes = mock(IotDevicePropertyDAO.class);
        runner(properties, messages, attributes).run(null);
        verifyNoInteractions(messages, attributes);
    }

    @Test
    void allDisabledReadsAndWritesReportUnavailableInsteadOfEmptyOrSuccess() {
        var dao = new IotDisabledTimeSeriesDAO();
        List<Runnable> operations = List.of(dao::initialize, () -> dao.insert((cn.iocoder.yudao.module.iot.dal.dataobject.device.IotDeviceMessageDO) null),
                () -> dao.selectPage(null), () -> dao.selectListByRequestIdsAndReply(null, null, null),
                () -> dao.selectCountByCreateTime(null), () -> dao.selectDeviceMessageCountGroupByDate(null, null),
                () -> dao.defineProductPropertyTable(null, null), () -> dao.deleteProductPropertyTable(1L), () -> dao.insert(null, null, null, null),
                () -> dao.selectHistoryList(null, null));
        for (var operation : operations) {
            assertEquals(1_050_000_000, assertThrows(ServiceException.class, operation::run).getCode());
        }
    }

    private IotStorageInitRunner runner(IotStorageProperties properties,
            IotDeviceMessageDAO messages, IotDevicePropertyDAO attributes) {
        var runner = new IotStorageInitRunner();
        ReflectionTestUtils.setField(runner, "storageProperties", properties);
        ReflectionTestUtils.setField(runner, "deviceMessageDAO", messages);
        ReflectionTestUtils.setField(runner, "devicePropertyDAO", attributes);
        return runner;
    }
}
