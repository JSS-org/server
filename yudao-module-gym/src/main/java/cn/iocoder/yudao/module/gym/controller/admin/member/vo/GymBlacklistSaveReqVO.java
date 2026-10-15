package cn.iocoder.yudao.module.gym.controller.admin.member.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.Instant;
@Data
public class GymBlacklistSaveReqVO {
    @NotNull @Positive private Long memberId;
    @NotNull private Boolean blockEntry;
    @NotNull private Boolean blockPurchase;
    @Size(max = 2000) private String reason;
    @NotNull private Instant startsAt;
    private Instant endsAt;
    private Boolean revoked;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean endsAtPresent;
    public void setEndsAt(Instant value) { endsAt=value; endsAtPresent=true; }
}
