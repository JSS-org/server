package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCatalogVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymCardTemplateDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseProductDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSalesSettingDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymCardTemplateMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCourseProductMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSalesSettingMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobileCatalogService {
    @Resource private GymMobilePublicService publicStores;
    @Resource private GymCardTemplateMapper templates;
    @Resource private GymCourseProductMapper courses;
    @Resource private GymSalesSettingMapper settings;
    @Resource private GymStoreMapper stores;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Product> cards(StorePage req) {
        validate(req);
        publicStores.requirePublicStore(req.getStoreId());
        return page(
                cardProducts(req.getStoreId(), false).stream()
                        .filter(p -> "on_sale".equals(p.saleStatus()))
                        .toList(),
                req);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Product> subscriptions(StorePage req) {
        validate(req);
        publicStores.requirePublicStore(req.getStoreId());
        return page(cardProducts(req.getStoreId(), true), req);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Product> courses(CoursePage req) {
        validate(req);
        publicStores.requirePublicStore(req.getStoreId());
        var q =
                courseQuery()
                        .eq(GymCourseProductDO::getStoreId, req.getStoreId())
                        .eq(
                                req.getCoachId() != null,
                                GymCourseProductDO::getCoachId,
                                req.getCoachId())
                        .orderByAsc(GymCourseProductDO::getId);
        var page = courses.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::course).toList(), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Product product(Long id, String type, Long store) {
        TenantContextHolder.getRequiredTenantId();
        if (id == null
                || id <= 0
                || store != null && store <= 0
                || !Set.of("card", "course", "subscription").contains(Objects.toString(type, "")))
            throw exception(MOBILE_REQUEST_INVALID);
        if (store != null) publicStores.requirePublicStore(store);
        if ("course".equals(type)) {
            var row =
                    courses.selectOne(
                            courseQuery()
                                    .eq(GymCourseProductDO::getId, id)
                                    .eq(store != null, GymCourseProductDO::getStoreId, store));
            if (row == null) throw exception(MOBILE_PRODUCT_NOT_AVAILABLE);
            return course(row);
        }
        // A card template can have distinct prices at multiple stores. Pick a stable public sale
        // scope
        // only when the client omitted storeId; never combine prices or revisions across stores.
        var locations =
                store == null
                        ? stores
                                .selectList(
                                        new LambdaQueryWrapper<GymStoreDO>()
                                                .eq(GymStoreDO::getStatus, "active")
                                                .orderByAsc(GymStoreDO::getId))
                                .stream()
                                .map(GymStoreDO::getId)
                                .toList()
                        : List.of(store);
        for (var location : locations) {
            var match =
                    cardProducts(location, "subscription".equals(type)).stream()
                            .filter(p -> id.toString().equals(p.id()))
                            .findFirst();
            if (match.isPresent()) return match.get();
        }
        throw exception(MOBILE_PRODUCT_NOT_AVAILABLE);
    }

    private LambdaQueryWrapper<GymCourseProductDO> courseQuery() {
        return new LambdaQueryWrapper<GymCourseProductDO>()
                .eq(GymCourseProductDO::getStatus, "active")
                .eq(GymCourseProductDO::getKind, "private")
                .exists(
                        "SELECT 1 FROM gym_store s WHERE s.id=gym_course_product.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active'",
                        TenantContextHolder.getRequiredTenantId())
                .exists(
                        "SELECT 1 FROM gym_employee e JOIN gym_employee_store es ON es.employee_id=e.id AND es.tenant_id=e.tenant_id AND es.deleted=0 "
                                + "WHERE e.id=gym_course_product.coach_id AND e.tenant_id={0} AND e.deleted=0 AND e.status='active' AND e.hired_on<={1} "
                                + "AND es.store_id=gym_course_product.store_id AND es.joined_on<={1}",
                        TenantContextHolder.getRequiredTenantId(),
                        clock.now().atZone(GymDateRange.ZONE).toLocalDate());
    }

    private Product course(GymCourseProductDO row) {
        return new Product(
                row.getId().toString(),
                "course",
                row.getStoreId().toString(),
                row.getName(),
                row.getDescription() == null ? "" : row.getDescription(),
                row.getPrice().setScale(2).toPlainString(),
                row.getVersion(),
                null,
                row.getSessionCount(),
                row.getCoachId().toString(),
                "on_sale",
                "checkout_unavailable",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private List<Product> cardProducts(Long store, boolean subscription) {
        var setting =
                settings.selectOne(
                        new LambdaQueryWrapper<GymSalesSettingDO>()
                                .eq(GymSalesSettingDO::getStoreId, store)
                                .eq(
                                        GymSalesSettingDO::getKind,
                                        subscription
                                                ? "subscription-settings"
                                                : "online-sales-settings"));
        if (setting == null) return List.of();
        var online = subscription ? null : parse(setting, GymOnlineSalesSettingsReqVO.class);
        var recurring = subscription ? parse(setting, GymSubscriptionSettingsReqVO.class) : null;
        if (subscription && recurring == null || !subscription && online == null) return List.of();
        var ids =
                subscription
                        ? recurring.getItems().stream()
                                .map(GymSubscriptionSettingsReqVO.Item::getCardTemplateId)
                                .toList()
                        : online.getOffers().stream()
                                .map(GymOnlineSalesSettingsReqVO.Offer::getCardTemplateId)
                                .toList();
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) return List.of();
        var byId =
                templates
                        .selectList(
                                new LambdaQueryWrapper<GymCardTemplateDO>()
                                        .in(GymCardTemplateDO::getId, ids)
                                        .eq(GymCardTemplateDO::getStatus, "published"))
                        .stream()
                        .filter(t -> applicable(t, store))
                        .collect(Collectors.toMap(GymCardTemplateDO::getId, t -> t));
        var result = new ArrayList<Product>();
        Instant now = clock.now();
        if (subscription) {
            for (var offer : recurring.getItems()) {
                var t = byId.get(offer.getCardTemplateId());
                if (t == null
                        || !Boolean.TRUE.equals(offer.getEnabled())
                        || !"period".equals(t.getKind())
                        || t.getDurationDays() == null
                        || t.getDurationDays() < 28
                        || t.getDurationDays() > 31
                        || offer.getRenewalTerms() == null
                        || offer.getRenewalTerms().isBlank()
                        || offer.getAgreementVersion() == null
                        || offer.getAgreementVersion().isBlank()) continue;
                result.add(
                        new Product(
                                t.getId().toString(),
                                "subscription",
                                store.toString(),
                                t.getName(),
                                "",
                                offer.getPrice().setScale(2).toPlainString(),
                                t.getVersion(),
                                t.getDurationDays(),
                                null,
                                null,
                                "on_sale",
                                "auto_renewal_integration_deferred",
                                offer.getRenewalTerms(),
                                offer.getAgreementVersion(),
                                setting.getRevision(),
                                "duration",
                                null,
                                offer.getBillingDay(),
                                offer.getRenewalNoticeDays(),
                                offer.getGraceDays()));
            }
        } else {
            for (var offer : online.getOffers()) {
                var t = byId.get(offer.getCardTemplateId());
                if (t == null
                        || !Boolean.TRUE.equals(offer.getEnabled())
                        || (offer.getStartsAt() == null) != (offer.getEndsAt() == null)
                        || offer.getStartsAt() != null
                                && !offer.getEndsAt().isAfter(offer.getStartsAt())) continue;
                String status =
                        offer.getStartsAt() != null && now.isBefore(offer.getStartsAt())
                                ? "not_started"
                                : offer.getEndsAt() != null && !now.isBefore(offer.getEndsAt())
                                        ? "ended"
                                        : "on_sale";
                result.add(
                        new Product(
                                t.getId().toString(),
                                "card",
                                store.toString(),
                                t.getName(),
                                "",
                                offer.getSalePrice().setScale(2).toPlainString(),
                                t.getVersion(),
                                t.getDurationDays(),
                                null,
                                null,
                                status,
                                "on_sale".equals(status) ? "checkout_unavailable" : status,
                                null,
                                null,
                                setting.getRevision(),
                                "period".equals(t.getKind()) ? "duration" : "times",
                                t.getIncludedUses(),
                                null,
                                null,
                                null));
            }
        }
        result.sort(Comparator.comparingLong(p -> Long.parseLong(p.id())));
        return result;
    }

    private boolean applicable(GymCardTemplateDO template, Long store) {
        if ("all".equals(template.getAccessScope())) return true;
        try {
            return "selected".equals(template.getAccessScope())
                    && JsonUtils.parseArray(template.getStoreIds(), Long.class).contains(store);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private <T> T parse(GymSalesSettingDO row, Class<T> type) {
        try {
            T value = JsonUtils.parseObject(row.getContent(), type);
            return value != null && validator.validate(value).isEmpty() ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private PageResult<Product> page(List<Product> rows, PageParam req) {
        // Each store's published JSON configuration is capped at 100 offers. Filter before slicing.
        int start = (int) Math.min(rows.size(), ((long) req.getPageNo() - 1) * req.getPageSize());
        return new PageResult<>(
                rows.subList(start, Math.min(rows.size(), start + req.getPageSize())),
                (long) rows.size());
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
}
