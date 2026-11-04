package cn.iocoder.yudao.module.iot.dal.storage;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.iot.enums.ErrorCodeConstants.TIME_SERIES_STORAGE_DISABLED;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.iot.controller.admin.device.vo.message.IotDeviceMessagePageReqVO;
import cn.iocoder.yudao.module.iot.controller.admin.device.vo.property.IotDevicePropertyHistoryListReqVO;
import cn.iocoder.yudao.module.iot.controller.admin.device.vo.property.IotDevicePropertyRespVO;
import cn.iocoder.yudao.module.iot.dal.dataobject.device.IotDeviceDO;
import cn.iocoder.yudao.module.iot.dal.dataobject.device.IotDeviceMessageDO;
import cn.iocoder.yudao.module.iot.dal.dataobject.thingmodel.IotThingModelDO;
import cn.iocoder.yudao.module.iot.framework.storage.config.IotStorageProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Explicit provider absence. Never returns fabricated data or falls back to the primary database. */
@Repository
@ConditionalOnProperty(
        prefix = IotStorageProperties.PREFIX,
        name = "type",
        havingValue = "disabled")
public class IotDisabledTimeSeriesDAO implements IotDeviceMessageDAO, IotDevicePropertyDAO {

    @Override
    public void initialize() {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public void insert(IotDeviceMessageDO message) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public PageResult<IotDeviceMessageDO> selectPage(IotDeviceMessagePageReqVO reqVO) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public List<IotDeviceMessageDO> selectListByRequestIdsAndReply(
            Long deviceId, Collection<String> requestIds, Boolean reply) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public Long selectCountByCreateTime(Long createTime) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public List<Map<String, Object>> selectDeviceMessageCountGroupByDate(
            Long startTime, Long endTime) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public void defineProductPropertyTable(
            Long productId, List<IotThingModelDO> propertyThingModels) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public void deleteProductPropertyTable(Long productId) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public void insert(
            IotDeviceDO device, Map<String, Object> properties, Long ts, Long reportTime) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }

    @Override
    public List<IotDevicePropertyRespVO> selectHistoryList(
            Long productId, IotDevicePropertyHistoryListReqVO reqVO) {
        throw exception(TIME_SERIES_STORAGE_DISABLED);
    }
}
