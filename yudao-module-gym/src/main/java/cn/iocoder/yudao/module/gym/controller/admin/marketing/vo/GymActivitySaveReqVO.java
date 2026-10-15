package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
@Data public class GymActivitySaveReqVO {
 @NotBlank @Size(max=100) private String name;
 @Size(max=4000) private String description;
 @NotNull @Size(min=1,max=100) private List<@NotNull @Positive Long> storeIds;
 @NotNull private Instant registrationStartsAt;
 @NotNull private Instant registrationEndsAt;
 @NotNull private Instant startsAt;
 @NotNull private Instant endsAt;
 @Min(1) @Max(100000) private Integer capacity;
 @com.fasterxml.jackson.annotation.JsonIgnore private boolean capacityPresent;
 public void setCapacity(Integer value){capacity=value;capacityPresent=true;}
 @Positive private Long coverFileId;
 @com.fasterxml.jackson.annotation.JsonIgnore private boolean coverFileIdPresent;
 public void setCoverFileId(Long value){coverFileId=value;coverFileIdPresent=true;}
 @Pattern(regexp="draft|published|closed|cancelled") private String status;
}
