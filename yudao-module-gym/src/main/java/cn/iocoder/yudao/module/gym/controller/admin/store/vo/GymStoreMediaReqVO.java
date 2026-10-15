package cn.iocoder.yudao.module.gym.controller.admin.store.vo;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
@Data
@Schema(description = "门店媒体整体替换；最多 50 个，封面至多一个且必须是图片")
public class GymStoreMediaReqVO {
    @NotNull @Size(max = 50) private List<@NotNull @Valid Item> items;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymStoreMediaReqVOItem")
 public static class Item {
        @NotNull @Positive private Long fileId;
        @Size(max = 200) private String caption;
        @NotNull private Boolean isCover;
        @NotNull @Min(0) @Max(10000) private Integer sortOrder;
    }
}
