package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymOnlineSalesSettingsReqVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileOrdersVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseQuoteDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCourseProductMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.course.GymBookingRules;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobileOrdersService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private GymMobileCourseQuoteService quotes;
    @Resource private GymBookingRules bookingRules;
    @Resource private GymMemberRecordsService records;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymCardTemplateMapper cards;
    @Resource private GymCourseProductMapper products;
    @Resource private GymSalesSettingMapper settings;
    @Resource private GymBusinessOrderMapper orders;
    @Resource private GymOrderItemMapper items;
    @Resource private GymOrderPolicyMapper policies;
    @Resource private GymFulfillmentTaskMapper tasks;
    @Resource private Validator validator;
    @Resource private cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentQueueService queue;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private record Payload(
            Long memberId,
            Long storeId,
            String productType,
            Long productId,
            int quantity,
            Long quoteId) {}

    private record Cancellation(Long orderId, String reason) {}

    private record Terms(
            String name,
            int version,
            BigDecimal amount,
            boolean approval,
            boolean contract,
            Map<String, Object> snapshot) {}

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Order create(Create req, String key) {
        validate(req);
        Long memberId = identities.requireMemberId();
        Long userId = identities.requireUser().getId();
        if (req.getQuoteId() != null && !"course".equals(req.getProductType()))
            throw exception(COMMERCE_REQUEST_INVALID);
        return idempotency.execute(
                "order-create",
                key,
                new Payload(
                        memberId,
                        req.getStoreId(),
                        req.getProductType(),
                        req.getProductId(),
                        req.getQuantity(),
                        req.getQuoteId()),
                Order.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var store = stores.selectForUpdate(req.getStoreId());
                    if (store == null || !"active".equals(store.getStatus()))
                        throw exception(COMMERCE_REQUEST_INVALID);
                    GymCourseQuoteDO quote =
                            req.getQuoteId() == null
                                    ? null
                                    : quotes.lockForCheckout(
                                            req.getQuoteId(),
                                            memberId,
                                            req.getStoreId(),
                                            req.getProductId());
                    if ("course".equals(req.getProductType())) {
                        var initial = products.selectById(req.getProductId());
                        if (initial == null || !req.getStoreId().equals(initial.getStoreId()))
                            throw exception(COMMERCE_REQUEST_INVALID);
                        bookingRules.requireCoach(initial.getCoachId(), req.getStoreId());
                    }
                    var member = members.selectForUpdate(memberId);
                    if (member == null
                            || !"active".equals(member.getStatus())
                            || memberships.selectCount(
                                            new LambdaQueryWrapper<GymMemberStoreDO>()
                                                    .eq(GymMemberStoreDO::getMemberId, memberId)
                                                    .eq(
                                                            GymMemberStoreDO::getStoreId,
                                                            req.getStoreId()))
                                    == 0) throw exception(COMMERCE_REQUEST_INVALID);
                    records.checkPurchase(memberId);
                    Instant now = clock.now();
                    Terms terms =
                            "card".equals(req.getProductType())
                                    ? cardTerms(req, now)
                                    : courseTerms(req, quote);
                    var row = new GymBusinessOrderDO();
                    row.setStoreId(req.getStoreId());
                    row.setMemberId(memberId);
                    row.setBuyerUserId(userId);
                    row.setMemberName(member.getName());
                    row.setBusinessType(req.getProductType() + "_sale");
                    row.setChannel("miniapp");
                    row.setRequestKey("mobile-order-" + UUID.randomUUID());
                    row.setOrderNo("G" + UUID.randomUUID().toString().replace("-", ""));
                    row.setPayableAmount(terms.amount());
                    row.setPaidAmount(ZERO);
                    row.setRefundedAmount(ZERO);
                    row.setStatus(
                            terms.amount().signum() == 0
                                    ? "pending_fulfillment"
                                    : "pending_payment");
                    Instant orderedAt = now.truncatedTo(ChronoUnit.MICROS);
                    row.setOrderedAt(orderedAt);
                    Instant deadline = orderedAt.plus(30, ChronoUnit.MINUTES);
                    if (quote != null && quote.getExpiresAt().isBefore(deadline))
                        deadline = quote.getExpiresAt();
                    row.setExpiresAt(deadline);
                    orders.insert(row);
                    var item = new GymOrderItemDO();
                    item.setOrderId(row.getId());
                    item.setProductType(req.getProductType());
                    item.setProductId(req.getProductId());
                    item.setProductNameSnapshot(terms.name());
                    item.setProductVersion(terms.version());
                    item.setQuantity(1);
                    item.setUnitPrice(terms.amount());
                    item.setPayableAmount(terms.amount());
                    items.insert(item);
                    var policy = new GymOrderPolicyDO();
                    policy.setOrderId(row.getId());
                    policy.setRequestHash(
                            cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                                    JsonUtils.toJsonString(req)));
                    policy.setTermsSnapshot(JsonUtils.toJsonString(terms.snapshot()));
                    policy.setApprovalRequired(terms.approval());
                    policy.setContractRequired(terms.contract());
                    policies.insert(policy);
                    if (quote != null) quotes.bindToOrder(quote.getId(), row.getId());
                    queue.enqueueMember(row.getId());
                    return project(List.of(row), now).getFirst();
                });
    }

    private Terms cardTerms(Create req, Instant now) {
        var card = cards.selectForUpdate(req.getProductId());
        if (card == null
                || !"published".equals(card.getStatus())
                || !"all".equals(card.getAccessScope())
                        && !JsonUtils.parseArray(card.getStoreIds(), Long.class)
                                .contains(req.getStoreId()))
            throw exception(COMMERCE_REQUEST_INVALID);
        var setting =
                settings.selectOne(
                        new LambdaQueryWrapper<GymSalesSettingDO>()
                                .eq(GymSalesSettingDO::getStoreId, req.getStoreId())
                                .eq(GymSalesSettingDO::getKind, "online-sales-settings")
                                .last("FOR UPDATE"));
        if (setting == null) throw exception(COMMERCE_REQUEST_INVALID);
        GymOnlineSalesSettingsReqVO config;
        try {
            config = JsonUtils.parseObject(setting.getContent(), GymOnlineSalesSettingsReqVO.class);
        } catch (RuntimeException e) {
            throw exception(COMMERCE_REQUEST_INVALID);
        }
        if (config == null || !validator.validate(config).isEmpty())
            throw exception(COMMERCE_REQUEST_INVALID);
        var matches =
                config.getOffers().stream()
                        .filter(o -> card.getId().equals(o.getCardTemplateId()))
                        .toList();
        if (matches.size() != 1 || !Boolean.TRUE.equals(matches.getFirst().getEnabled()))
            throw exception(COMMERCE_REQUEST_INVALID);
        var offer = matches.getFirst();
        if ((offer.getStartsAt() == null) != (offer.getEndsAt() == null)
                || offer.getStartsAt() != null
                        && (!offer.getEndsAt().isAfter(offer.getStartsAt())
                                || now.isBefore(offer.getStartsAt())
                                || !now.isBefore(offer.getEndsAt())))
            throw exception(COMMERCE_REQUEST_INVALID);
        return new Terms(
                card.getName(),
                card.getVersion(),
                offer.getSalePrice().setScale(2),
                Boolean.TRUE.equals(offer.getApprovalRequired()),
                Boolean.TRUE.equals(offer.getContractRequired()),
                Map.of("offer", offer, "template", card, "settingRevision", setting.getRevision()));
    }

    private Terms courseTerms(Create req, GymCourseQuoteDO quote) {
        var product = products.selectForUpdate(req.getProductId());
        if (product == null
                || !req.getStoreId().equals(product.getStoreId())
                || !"active".equals(product.getStatus())
                || !"private".equals(product.getKind())) throw exception(COMMERCE_REQUEST_INVALID);
        if (quote != null && !quote.getProductVersion().equals(product.getVersion()))
            throw exception(COURSE_QUOTE_UNAVAILABLE);
        var amount = quote == null ? product.getPrice() : quote.getAmount();
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("product", product);
        snapshot.put("quoteId", quote == null ? null : quote.getId().toString());
        return new Terms(
                product.getName(),
                product.getVersion(),
                amount.setScale(2),
                false,
                false,
                snapshot);
    }

    private LambdaQueryWrapper<GymBusinessOrderDO> own(Long memberId) {
        return new LambdaQueryWrapper<GymBusinessOrderDO>()
                .eq(GymBusinessOrderDO::getMemberId, memberId)
                .in(GymBusinessOrderDO::getBusinessType, List.of("card_sale", "course_sale"))
                .in(GymBusinessOrderDO::getChannel, List.of("miniapp", "app"))
                .apply(
                        "(SELECT COUNT(*) FROM gym_order_item oi WHERE oi.order_id=gym_business_order.id AND oi.tenant_id={0} AND oi.deleted=0)=1",
                        TenantContextHolder.getRequiredTenantId());
    }

    private GymBusinessOrderDO ownOrder(Long id, Long memberId, boolean lock) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var q = own(memberId).eq(GymBusinessOrderDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = orders.selectOne(q);
        if (row == null) throw exception(SALES_ORDER_NOT_EXISTS);
        return row;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Order get(Long id) {
        return project(List.of(ownOrder(id, identities.requireMemberId(), false)), clock.now())
                .getFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Order> page(Query req) {
        validate(req);
        Long memberId = identities.requireMemberId();
        Instant now = clock.now();
        var q = own(memberId);
        if (req.getStatus() != null) q.apply("(" + paymentSql() + ")={1}", now, req.getStatus());
        var page = orders.selectPage(req, q.orderByDesc(GymBusinessOrderDO::getId));
        return new PageResult<>(project(page.getList(), now), page.getTotal());
    }

    private String paymentSql() {
        return "CASE WHEN refunded_amount>0 AND refunded_amount>=paid_amount THEN 'refunded' "
                + "WHEN refunded_amount>0 THEN 'partially_refunded' WHEN status IN ('cancelled','expired') THEN 'closed' "
                + "WHEN paid_amount>=payable_amount THEN 'paid' "
                + "WHEN status='pending_payment' AND paid_amount=0 AND expires_at<={0} THEN 'closed' ELSE 'unpaid' END";
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Order cancel(Long id, Cancel req, String key) {
        validate(req);
        Long memberId = identities.requireMemberId();
        ownOrder(id, memberId, false);
        return idempotency.execute(
                "order-cancel",
                key,
                new Cancellation(id, req.getReason() == null ? null : req.getReason().trim()),
                Order.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var row = ownOrder(id, memberId, true);
                    if ("cancelled".equals(row.getStatus()))
                        return project(List.of(row), clock.now()).getFirst();
                    var policy = policies.selectOne(GymOrderPolicyDO::getOrderId, id);
                    if (!Set.of("pending_payment", "expired").contains(row.getStatus())
                            || row.getPaidAmount().signum() != 0
                            || row.getRefundedAmount().signum() != 0
                            || policy == null
                            || policy.getFulfilledAt() != null)
                        throw exception(MOBILE_ORDER_NOT_CANCELLABLE);
                    row.setStatus("cancelled");
                    row.setCancelledAt(clock.now().truncatedTo(ChronoUnit.MICROS));
                    row.setCancellationReason(
                            req.getReason() == null ? null : req.getReason().trim());
                    orders.updateById(row);
                    var task =
                            tasks.selectOne(
                                    new LambdaQueryWrapper<GymFulfillmentTaskDO>()
                                            .eq(GymFulfillmentTaskDO::getOrderId, id)
                                            .last("FOR UPDATE"));
                    if (task != null) {
                        if ("succeeded".equals(task.getStatus()))
                            throw exception(MOBILE_ORDER_NOT_CANCELLABLE);
                        task.setStatus("cancelled");
                        task.setNextAttemptAt(null);
                        task.setLeaseUntil(null);
                        task.setClaimToken(null);
                        task.setFailureReason("会员取消未支付订单");
                        tasks.updateById(task);
                    }
                    return project(List.of(row), clock.now()).getFirst();
                });
    }

    private List<Order> project(List<GymBusinessOrderDO> rows, Instant now) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(GymBusinessOrderDO::getId).toList();
        var itemRows =
                items
                        .selectList(
                                new LambdaQueryWrapper<GymOrderItemDO>()
                                        .in(GymOrderItemDO::getOrderId, ids))
                        .stream()
                        .collect(Collectors.toMap(GymOrderItemDO::getOrderId, x -> x));
        var policyRows =
                policies
                        .selectList(
                                new LambdaQueryWrapper<GymOrderPolicyDO>()
                                        .in(GymOrderPolicyDO::getOrderId, ids))
                        .stream()
                        .collect(Collectors.toMap(GymOrderPolicyDO::getOrderId, x -> x));
        var taskRows =
                tasks
                        .selectList(
                                new LambdaQueryWrapper<GymFulfillmentTaskDO>()
                                        .in(GymFulfillmentTaskDO::getOrderId, ids))
                        .stream()
                        .collect(Collectors.toMap(GymFulfillmentTaskDO::getOrderId, x -> x));
        return rows.stream()
                .map(
                        row -> {
                            var item = itemRows.get(row.getId());
                            var policy = policyRows.get(row.getId());
                            var task = taskRows.get(row.getId());
                            String payment =
                                    row.getRefundedAmount().signum() > 0
                                            ? (row.getRefundedAmount()
                                                                    .compareTo(row.getPaidAmount())
                                                            >= 0
                                                    ? "refunded"
                                                    : "partially_refunded")
                                            : Set.of("cancelled", "expired")
                                                            .contains(row.getStatus())
                                                    ? "closed"
                                                    : row.getPaidAmount()
                                                                            .compareTo(
                                                                                    row
                                                                                            .getPayableAmount())
                                                                    >= 0
                                                            ? "paid"
                                                            : "pending_payment"
                                                                                    .equals(
                                                                                            row
                                                                                                    .getStatus())
                                                                            && row.getPaidAmount()
                                                                                            .signum()
                                                                                    == 0
                                                                            && row.getExpiresAt()
                                                                                    != null
                                                                            && !now.isBefore(
                                                                                    row
                                                                                            .getExpiresAt())
                                                                    ? "closed"
                                                                    : "unpaid";
                            String fulfillment =
                                    policy != null && policy.getFulfilledAt() != null
                                            ? "succeeded"
                                            : task != null
                                                            && Set.of("processing", "failed")
                                                                    .contains(task.getStatus())
                                                    ? task.getStatus()
                                                    : "pending";
                            List<String> rights =
                                    item.getEntitlementIds() == null
                                            ? List.of()
                                            : JsonUtils.parseArray(
                                                    item.getEntitlementIds(), String.class);
                            return new Order(
                                    row.getId().toString(),
                                    row.getOrderNo(),
                                    row.getStoreId().toString(),
                                    item.getProductType(),
                                    item.getProductId().toString(),
                                    item.getProductNameSnapshot(),
                                    item.getQuantity(),
                                    row.getPayableAmount().setScale(2).toPlainString(),
                                    row.getPaidAmount().setScale(2).toPlainString(),
                                    row.getRefundedAmount().setScale(2).toPlainString(),
                                    payment,
                                    fulfillment,
                                    row.getExpiresAt() == null
                                            ? null
                                            : row.getExpiresAt()
                                                    .atZone(GymDateRange.ZONE)
                                                    .toOffsetDateTime()
                                                    .toString(),
                                    "card".equals(item.getProductType()) ? rights : List.of(),
                                    "course".equals(item.getProductType()) ? rights : List.of());
                        })
                .toList();
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
}
