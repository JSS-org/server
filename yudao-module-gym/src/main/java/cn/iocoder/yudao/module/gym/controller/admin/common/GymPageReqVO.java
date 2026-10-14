package cn.iocoder.yudao.module.gym.controller.admin.common;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "门店/员工分页筛选，数据范围由服务端限定")
public class GymPageReqVO extends PageParam {
    @Size(max = 100) private String keyword;
    @Positive private Long storeId;
    @Pattern(regexp = "active|inactive") private String status;
}
