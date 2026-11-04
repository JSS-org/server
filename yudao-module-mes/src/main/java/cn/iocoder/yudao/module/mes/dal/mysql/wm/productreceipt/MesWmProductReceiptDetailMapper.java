package cn.iocoder.yudao.module.mes.dal.mysql.wm.productreceipt;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mes.controller.admin.wm.productreceipt.vo.detail.MesWmProductReceiptDetailListReqVO;
import cn.iocoder.yudao.module.mes.dal.dataobject.wm.productreceipt.MesWmProductReceiptDetailDO;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 产品收货单明细 Mapper
 */
@Mapper
public interface MesWmProductReceiptDetailMapper extends BaseMapperX<MesWmProductReceiptDetailDO> {

    default List<MesWmProductReceiptDetailDO> selectList(MesWmProductReceiptDetailListReqVO reqVO) {
        return selectList(
                new LambdaQueryWrapperX<MesWmProductReceiptDetailDO>()
                        .eqIfPresent(
                                MesWmProductReceiptDetailDO::getReceiptId, reqVO.getReceiptId())
                        .eqIfPresent(MesWmProductReceiptDetailDO::getLineId, reqVO.getLineId())
                        .orderByAsc(MesWmProductReceiptDetailDO::getId));
    }

    default List<MesWmProductReceiptDetailDO> selectListByRecptId(Long receiptId) {
        return selectList(MesWmProductReceiptDetailDO::getReceiptId, receiptId);
    }

    default List<MesWmProductReceiptDetailDO> selectListByLineId(Long lineId) {
        return selectList(MesWmProductReceiptDetailDO::getLineId, lineId);
    }

    default void deleteByRecptId(Long receiptId) {
        delete(MesWmProductReceiptDetailDO::getReceiptId, receiptId);
    }

    default void deleteByLineId(Long lineId) {
        delete(MesWmProductReceiptDetailDO::getLineId, lineId);
    }
}
