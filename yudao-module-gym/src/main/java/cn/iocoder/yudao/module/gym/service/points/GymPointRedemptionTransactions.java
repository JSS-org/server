package cn.iocoder.yudao.module.gym.service.points;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointRedemptionVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.points.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.points.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;
import cn.iocoder.yudao.module.gym.service.mobile.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GymPointRedemptionTransactions {
    @Resource private GymPointRedemptionMapper rows;
    @Resource private GymPointProductMapper products;
    @Resource private GymCardTemplateMapper templates;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMemberRecordsService records;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymPointLedgerService ledger;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private GymPointRedemptionAccess access;
    @Resource private GymCardService cards;
    @Resource private Validator validator;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long prepare(Create req, String key) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        identities.requireIdentity();
        return idempotency.execute(
                "point-redemption",
                key,
                req,
                Long.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var identity = identities.requireIdentity();
                    var prior =
                            rows.selectOne(
                                    new LambdaQueryWrapper<GymPointRedemptionDO>()
                                            .eq(
                                                    GymPointRedemptionDO::getUserId,
                                                    identity.getUserId())
                                            .eq(GymPointRedemptionDO::getRequestKey, key)
                                            .last("FOR UPDATE"));
                    if (prior != null) {
                        if (!identity.getMemberId().equals(prior.getMemberId())
                                || !req.getStoreId().equals(prior.getStoreId())
                                || !req.getProductId().equals(prior.getProductId()))
                            throw exception(MOBILE_IDEMPOTENCY_CONFLICT);
                        return prior.getId();
                    }
                    var store = stores.selectForUpdate(req.getStoreId());
                    if (store == null
                            || !"active".equals(store.getStatus())
                            || memberships.selectCount(
                                            new LambdaQueryWrapper<GymMemberStoreDO>()
                                                    .eq(
                                                            GymMemberStoreDO::getMemberId,
                                                            identity.getMemberId())
                                                    .eq(
                                                            GymMemberStoreDO::getStoreId,
                                                            req.getStoreId()))
                                    == 0) throw exception(POINT_PRODUCT_UNAVAILABLE);
                    records.checkPurchase(identity.getMemberId());
                    var product =
                            products.selectOne(
                                    new LambdaQueryWrapper<GymPointProductDO>()
                                            .eq(GymPointProductDO::getId, req.getProductId())
                                            .eq(GymPointProductDO::getStoreId, req.getStoreId())
                                            .last("FOR UPDATE"));
                    if (product == null
                            || !Boolean.TRUE.equals(product.getEnabled())
                            || product.getStock() != null && product.getStock() <= 0
                            || product.getReservedStock() == Integer.MAX_VALUE
                            || product.getRevision() + product.getReservedStock() + 2
                                    > GymPointLedgerService.MAX_POINTS)
                        throw exception(POINT_PRODUCT_INVALID);
                    var card = templates.selectForUpdate(product.getCardProductId());
                    if (!GymPointRedemptionAccess.applicable(card, req.getStoreId()))
                        throw exception(POINT_PRODUCT_INVALID);
                    var row = new GymPointRedemptionDO();
                    row.setUserId(identity.getUserId());
                    row.setRequestKey(key);
                    row.setMemberId(identity.getMemberId());
                    row.setStoreId(req.getStoreId());
                    row.setProductId(product.getId());
                    row.setProductRevision(product.getRevision());
                    row.setRequiredPoints(product.getRequiredPoints());
                    row.setStockReserved(product.getStock() != null);
                    row.setTemplateSnapshot(JsonUtils.toJsonString(card));
                    row.setStatus("processing");
                    row.setRequestedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
                    rows.insert(row);
                    var debit =
                            ledger.append(
                                    new GymPointLedgerService.Posting(
                                            row.getMemberId(),
                                            "redemption:" + row.getId() + ":spend",
                                            "spend",
                                            -row.getRequiredPoints(),
                                            "积分兑换：" + product.getName(),
                                            null));
                    ledger.reserveRefund(row.getMemberId(), row.getRequiredPoints());
                    row.setDebitRecordId(debit.id());
                    rows.updateById(row);
                    if (product.getStock() != null) product.setStock(product.getStock() - 1);
                    product.setReservedStock(product.getReservedStock() + 1);
                    product.setRevision(product.getRevision() + 1);
                    products.updateById(product);
                    return row.getId();
                });
    }

    private GymPointRedemptionDO locked(Long id) {
        return rows.selectOne(
                new LambdaQueryWrapper<GymPointRedemptionDO>()
                        .eq(GymPointRedemptionDO::getId, id)
                        .last("FOR UPDATE"));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void fulfill(Long id) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null || !"processing".equals(row.getStatus())) return;
        access.require(id);
        var card = cards.issuePointRedemption(id, clock.now());
        release(row, false);
        ledger.releaseRefund(row.getMemberId(), row.getRequiredPoints());
        row.setMemberCardId(card.getId());
        row.setStatus("succeeded");
        row.setCompletedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
        rows.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void fail(Long id, Integer code) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null || !"processing".equals(row.getStatus())) return;
        ledger.releaseRefund(row.getMemberId(), row.getRequiredPoints());
        var refund =
                ledger.append(
                        new GymPointLedgerService.Posting(
                                row.getMemberId(),
                                "redemption:" + id + ":refund",
                                "refund",
                                row.getRequiredPoints(),
                                "积分兑换失败返还",
                                row.getDebitRecordId()));
        release(row, true);
        row.setRefundRecordId(refund.id());
        row.setStatus("failed");
        row.setFailureCode(code);
        row.setCompletedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
        rows.updateById(row);
    }

    private void release(GymPointRedemptionDO row, boolean failed) {
        var p =
                products.selectOne(
                        new LambdaQueryWrapper<GymPointProductDO>()
                                .eq(GymPointProductDO::getId, row.getProductId())
                                .last("FOR UPDATE"));
        if (p == null || p.getReservedStock() < 1) throw exception(POINT_REDEMPTION_INVALID);
        p.setReservedStock(p.getReservedStock() - 1);
        if (failed && row.getStockReserved() && p.getStock() != null) p.setStock(p.getStock() + 1);
        p.setRevision(p.getRevision() + 1);
        products.updateById(p);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result get(Long id) {
        var identity = identities.requireIdentity();
        var row =
                rows.selectOne(
                        new LambdaQueryWrapper<GymPointRedemptionDO>()
                                .eq(GymPointRedemptionDO::getId, id)
                                .eq(GymPointRedemptionDO::getUserId, identity.getUserId())
                                .eq(GymPointRedemptionDO::getMemberId, identity.getMemberId()));
        if (row == null) throw exception(POINT_REDEMPTION_NOT_EXISTS);
        return new Result(
                row.getId().toString(),
                row.getStatus(),
                "failed".equals(row.getStatus()) ? 0 : row.getRequiredPoints(),
                row.getMemberCardId() == null ? null : row.getMemberCardId().toString());
    }

    public List<Long> due() {
        return rows
                .selectList(
                        new LambdaQueryWrapper<GymPointRedemptionDO>()
                                .eq(GymPointRedemptionDO::getStatus, "processing")
                                .le(
                                        GymPointRedemptionDO::getRequestedAt,
                                        clock.now().minusSeconds(30))
                                .orderByAsc(GymPointRedemptionDO::getId)
                                .last("LIMIT 100"))
                .stream()
                .map(GymPointRedemptionDO::getId)
                .toList();
    }
}
