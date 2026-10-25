package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalCreateReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.Approval;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileCardIssueVO;
import cn.iocoder.yudao.module.gym.service.approval.GymApprovalService;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.member.GymMemberService;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.UUID;

@Service
public class GymMobileCardIssueService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymMemberService members;
    @Resource private GymApprovalService approvals;
    @Resource private GymMobileApprovalsService projections;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private Validator validator;

    private record Payload(
            Long storeId,
            Long memberId,
            String productType,
            Long productId,
            String reason,
            Long approvalTemplateId) {}

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Approval create(GymMobileCardIssueVO req, String key) {
        access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        scope.check(req.getStoreId());
        members.requireMember(req.getMemberId(), false);
        members.checkMemberStore(req.getMemberId(), req.getStoreId());
        String reason = req.getReason().trim();
        return idempotency.execute(
                "card-issue-application",
                key,
                new Payload(
                        req.getStoreId(),
                        req.getMemberId(),
                        req.getProductType(),
                        req.getProductId(),
                        reason,
                        req.getApprovalTemplateId()),
                Approval.class,
                () -> {
                    var request = new GymApprovalCreateReqVO();
                    request.setStoreId(req.getStoreId());
                    request.setTemplateId(req.getApprovalTemplateId());
                    request.setBusinessType("card_opening");
                    request.setMemberId(req.getMemberId());
                    request.setReason(reason);
                    request.setTitle("card".equals(req.getProductType()) ? "会员卡发放申请" : "私教课包发放申请");
                    request.setSourceId(req.getProductId());
                    request.setSourceType(
                            "card".equals(req.getProductType())
                                    ? "card-templates"
                                    : "course-products");
                    if ("card".equals(req.getProductType()))
                        request.setCardTemplateId(req.getProductId());
                    var created = approvals.create(request, "mobile-issue-" + UUID.randomUUID());
                    return projections.get(Long.valueOf(created.getId()));
                });
    }
}
