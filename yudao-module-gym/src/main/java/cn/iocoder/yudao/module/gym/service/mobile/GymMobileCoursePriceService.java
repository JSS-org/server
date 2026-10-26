package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalCreateReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.Approval;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileCoursePriceVO;
import cn.iocoder.yudao.module.gym.service.approval.GymApprovalService;
import cn.iocoder.yudao.module.gym.service.course.GymCourseService;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class GymMobileCoursePriceService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymCourseService courses;
    @Resource private GymApprovalService approvals;
    @Resource private GymMobileApprovalsService projections;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private Validator validator;

    private record Payload(
            Long storeId,
            Long productId,
            Long memberId,
            BigDecimal proposedAmount,
            String reason,
            Long approvalTemplateId) {}

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Approval create(GymMobileCoursePriceVO req, String key) {
        access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        // Recheck current scope before even returning a persisted idempotent response.
        courses.checkQuoteApplicationAccess(
                req.getMemberId(), req.getProductId(), req.getStoreId());
        var amount = req.getProposedAmount().setScale(2);
        var reason = req.getReason().trim();
        return idempotency.execute(
                "course-price-application",
                key,
                new Payload(
                        req.getStoreId(),
                        req.getProductId(),
                        req.getMemberId(),
                        amount,
                        reason,
                        req.getApprovalTemplateId()),
                Approval.class,
                () -> {
                    var request = new GymApprovalCreateReqVO();
                    request.setStoreId(req.getStoreId());
                    request.setTemplateId(req.getApprovalTemplateId());
                    request.setBusinessType("price_change");
                    request.setTitle("私教专属报价改价申请");
                    request.setSourceType("course-quote-products");
                    request.setSourceId(req.getProductId());
                    request.setMemberId(req.getMemberId());
                    request.setRequestedPrice(amount);
                    request.setReason(reason);
                    var created = approvals.create(request, "mobile-price-" + UUID.randomUUID());
                    return projections.get(Long.valueOf(created.getId()));
                });
    }
}
