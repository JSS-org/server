package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;
@Data
public class GymContractRespVO {
 private String id;
 private String contractNo;
 private String name;
 private String storeId;
 private String storeName;
 private String memberId;
 private String memberName;
 private String templateId;
 private String sourceType;
 private String sourceId;
 private String unsignedFileId;
 private String signedFileId;
 private String status;
 private String approvalStatus;
 private String approvedApprovalId;
 private Instant approvedAt;
 private String signingStatus;
 private Instant dueAt;
 private Instant signedAt;
 private String signIp;
 private String signDeviceUid;
 private String signUserAgent;
 private Instant issuedAt;
 private Map<String,Object> businessSnapshot;
 private List<Event> events;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymContractRespVOEvent")
 public static class Event {
  private String id;
  private String type;
  private Instant occurredAt;
  private String ip;
  private String detail;
 }
}
