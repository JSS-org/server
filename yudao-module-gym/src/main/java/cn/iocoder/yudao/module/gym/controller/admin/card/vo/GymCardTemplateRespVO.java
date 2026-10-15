package cn.iocoder.yudao.module.gym.controller.admin.card.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
@Data
public class GymCardTemplateRespVO {
    private String id;
    private String familyId;
    private Integer version;
    private String name;
    private String kind;
    private Integer durationDays;
    private Integer includedUses;
    private BigDecimal price;
    private String memberPrefix;
    private String accessScope;
    private List<String> storeIds;
    private String status;
}
