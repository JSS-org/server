package cn.iocoder.yudao.module.gym.controller.admin.content.vo;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
@Data public class GymClientSettingsReqVO {
 @NotNull @Pattern(regexp="miniapp|app") private String platform;
 @Positive private Long storeId;
 @NotBlank @Size(max=120) private String systemName;
 @Size(max=128) private String appId;
 @Positive private Long coverFileId;
 @Size(max=2000) private String welcomeText;
 @Size(max=300) private String customerService;
 @Size(max=2000) private String appDownloadUrl;
 @NotNull private Boolean enabled;
 @Size(max=100000)
 @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.fasterxml.jackson.databind.deser.std.StringDeserializer.class)
 private String contentHtml;
 @NotNull @Size(max=100) private List<@NotNull @Valid Block> blocks;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymClientSettingsReqVOBlock")
 public static class Block {
  @NotNull @Pattern(regexp="banner|video|text|link") private String blockType;
  @Size(max=120) private String title;
  @NotNull private Boolean enabled;
  @NotNull @Min(0) @Max(100) private Integer sortOrder;
  @Size(max=10000) private String body;
  @Positive private Long fileId;
  @Size(max=2000) private String linkUrl;
 }
}
