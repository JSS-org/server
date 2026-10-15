package cn.iocoder.yudao.module.gym.controller.admin.member.vo;
import lombok.Data;
import java.time.Instant;
import java.math.BigDecimal;
@Data
public class GymBlacklistRespVO {
    private String id;
    private String memberName;
    private String storeName;
    private String memberId;
    private Boolean blockEntry;
    private Boolean blockPurchase;
    private String reason;
    private Instant startsAt;
    private Instant endsAt;
    private Instant revokedAt;
    private String status;
}
