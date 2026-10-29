package cn.iocoder.yudao.module.member.controller.app.feedback;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.feedback.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.feedback.MemberFeedbackAttachmentDO;
import cn.iocoder.yudao.module.member.service.feedback.MemberFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Tag(name = "用户 App - 意见反馈")
@RestController
@RequestMapping("/member/feedback")
@Validated
public class AppMemberFeedbackController {
    @Resource private MemberFeedbackService feedbackService;

    @PostMapping("/upload")
    @Operation(summary = "上传当前会员的私有反馈图片")
    public CommonResult<Long> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return success(feedbackService.uploadImage(getLoginUserId(), file));
    }

    @PostMapping("/create")
    @Operation(summary = "提交反馈并关联当前会员上传的图片")
    public CommonResult<Long> create(@Valid @RequestBody AppMemberFeedbackCreateReqVO request) {
        return success(feedbackService.createFeedback(getLoginUserId(), request));
    }

    @GetMapping("/get")
    @Operation(summary = "读取本人提交的反馈")
    public CommonResult<AppMemberFeedbackRespVO> get(@RequestParam("id") Long id) {
        return success(feedbackService.getFeedback(getLoginUserId(), id));
    }

    @GetMapping("/attachment")
    @Operation(summary = "读取本人反馈图片")
    public void attachment(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        MemberFeedbackAttachmentDO image = feedbackService.getAttachment(getLoginUserId(), id);
        response.setContentType(image.getContentType());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentLength(image.getContent().length);
        response.getOutputStream().write(image.getContent());
    }
}
