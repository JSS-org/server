package cn.iocoder.yudao.module.gym.controller.admin.store.vo;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
@Data
@Schema(description = "门店资料，id 等于 system_dept.id，以字符串返回")
public class GymStoreRespVO {
    private String id;
    private String code;
    private String name;
    private String description;
    private BigDecimal areaSqm;
    private List<String> features;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String servicePhone;
    private String serviceWechat;
    private String status;
    private GymStoreSaveReqVO.BusinessHours businessHours;
    private List<Media> media;
    @Data
    public static class Media {
        private String id;
        private String fileId;
        private String category;
        private String originalName;
        private String name;
        private String mimeType;
        private Long byteSize;
        private String caption;
        private Boolean isCover;
        private Integer sortOrder;
    }
}
