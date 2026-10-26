package cn.iocoder.yudao.module.gym.service.points;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.points.vo.GymPointProductVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointProductsVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.points.GymPointProductDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.points.GymPointProductMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class GymPointProductsService {
    @Resource private GymPointProductMapper products;
    @Resource private GymCardTemplateMapper templates;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessMutex mutex;
    @Resource private Validator validator;

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private boolean applicable(GymCardTemplateDO card, Long store) {
        if (card == null) return false;
        if ("all".equals(card.getAccessScope())) return true;
        try {
            return "selected".equals(card.getAccessScope())
                    && JsonUtils.parseArray(card.getStoreIds(), Long.class).contains(store);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void adminStore(Long store, boolean lock) {
        if (SecurityFrameworkUtils.getLoginUser() == null
                || !Integer.valueOf(2).equals(SecurityFrameworkUtils.getLoginUser().getUserType())
                || !cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder
                        .getRequiredTenantId()
                        .equals(SecurityFrameworkUtils.getLoginUser().getTenantId()))
            throw exception(STORE_NO_PERMISSION);
        scope.check(store);
        var row = lock ? stores.selectForUpdate(store) : stores.selectById(store);
        if (row == null) throw exception(STORE_NOT_EXISTS);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<GymPointProductVO.Product> manage(GymMobilePointProductsVO.Query req) {
        validate(req);
        adminStore(req.getStoreId(), false);
        var page =
                products.selectPage(
                        req,
                        new LambdaQueryWrapper<GymPointProductDO>()
                                .eq(GymPointProductDO::getStoreId, req.getStoreId())
                                .orderByDesc(GymPointProductDO::getId));
        return new PageResult<>(
                page.getList().stream().map(this::adminResponse).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymPointProductVO.Product save(Long id, GymPointProductVO.Save req) {
        validate(req);
        if (id != null && id <= 0
                || id == null && req.getRevision() != null
                || id != null && req.getRevision() == null) throw exception(POINT_PRODUCT_INVALID);
        mutex.lockStoreEntitlements();
        adminStore(req.getStoreId(), true);
        GymPointProductDO row;
        if (id == null) {
            row = new GymPointProductDO();
            row.setStoreId(req.getStoreId());
            row.setCardProductId(req.getCardProductId());
            row.setRevision(1L);
            row.setReservedStock(0);
        } else {
            row =
                    products.selectOne(
                            new LambdaQueryWrapper<GymPointProductDO>()
                                    .eq(GymPointProductDO::getId, id)
                                    .eq(GymPointProductDO::getStoreId, req.getStoreId())
                                    .last("FOR UPDATE"));
            if (row == null) throw exception(POINT_PRODUCT_NOT_EXISTS);
            if (!row.getCardProductId().equals(req.getCardProductId()))
                throw exception(POINT_PRODUCT_INVALID);
            if (!row.getRevision().equals(req.getRevision())
                    || row.getRevision() >= GymPointLedgerService.MAX_POINTS)
                throw exception(POINT_PRODUCT_CONFLICT);
        }
        // Disabling an existing product must remain possible after its template was
        // archived/deleted.
        if (id == null || req.getEnabled()) {
            var card = templates.selectForUpdate(req.getCardProductId());
            if (!applicable(card, req.getStoreId())
                    || req.getEnabled() && !"published".equals(card.getStatus()))
                throw exception(POINT_PRODUCT_INVALID);
        }
        if (req.getStock() != null
                        && (long) req.getStock() + row.getReservedStock() > Integer.MAX_VALUE
                || row.getRevision() + 1 + row.getReservedStock()
                        > GymPointLedgerService.MAX_POINTS) throw exception(POINT_PRODUCT_CONFLICT);
        String name = req.getName().trim();
        if (name.isBlank()) throw exception(POINT_PRODUCT_INVALID);
        boolean changed =
                !name.equals(row.getName())
                        || !req.getRequiredPoints().equals(row.getRequiredPoints())
                        || !Objects.equals(req.getStock(), row.getStock())
                        || !req.getEnabled().equals(row.getEnabled());
        row.setName(name);
        row.setRequiredPoints(req.getRequiredPoints());
        row.setStock(req.getStock());
        row.setEnabled(req.getEnabled());
        if (id == null) products.insert(row);
        else if (changed) {
            row.setRevision(row.getRevision() + 1);
            products.updateById(row);
        }
        return adminResponse(row);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<GymMobilePointProductsVO.Product> catalog(
            GymMobilePointProductsVO.Query req) {
        validate(req);
        Long member = identities.requireMemberId();
        var store = stores.selectById(req.getStoreId());
        if (store == null
                || !"active".equals(store.getStatus())
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member)
                                        .eq(GymMemberStoreDO::getStoreId, req.getStoreId()))
                        == 0) throw exception(POINT_PRODUCT_UNAVAILABLE);
        var ids =
                templates
                        .selectList(
                                new LambdaQueryWrapper<GymCardTemplateDO>()
                                        .eq(GymCardTemplateDO::getStatus, "published"))
                        .stream()
                        .filter(t -> applicable(t, req.getStoreId()))
                        .map(GymCardTemplateDO::getId)
                        .toList();
        if (ids.isEmpty()) return new PageResult<>(List.of(), 0L);
        var page =
                products.selectPage(
                        req,
                        new LambdaQueryWrapper<GymPointProductDO>()
                                .eq(GymPointProductDO::getStoreId, req.getStoreId())
                                .eq(GymPointProductDO::getEnabled, true)
                                .in(GymPointProductDO::getCardProductId, ids)
                                .and(
                                        q ->
                                                q.isNull(GymPointProductDO::getStock)
                                                        .or()
                                                        .gt(GymPointProductDO::getStock, 0))
                                .orderByAsc(GymPointProductDO::getId));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                p ->
                                        new GymMobilePointProductsVO.Product(
                                                p.getId().toString(),
                                                p.getName(),
                                                p.getRequiredPoints(),
                                                p.getStock(),
                                                p.getCardProductId().toString()))
                        .toList(),
                page.getTotal());
    }

    private GymPointProductVO.Product adminResponse(GymPointProductDO p) {
        return new GymPointProductVO.Product(
                p.getId().toString(),
                p.getStoreId().toString(),
                p.getCardProductId().toString(),
                p.getName(),
                p.getRequiredPoints(),
                p.getStock(),
                p.getEnabled(),
                p.getRevision());
    }
}
