package cn.iocoder.yudao.module.gym.controller.admin.member.vo;
import lombok.Data;
import java.time.Instant;
import java.math.BigDecimal;
@Data
public class GymMeasurementRespVO {
    private String id;
    private String memberName;
    private String storeName;
    private String storeId;
    private String memberId;
    private Instant measuredAt;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bodyFatPercent;
    private BigDecimal muscleMassKg;
    private String note;
    private String reportFileId;

}
