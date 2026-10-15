package cn.iocoder.yudao.module.gym.controller.admin.device.vo;
import lombok.Data;
import java.time.Instant;
import java.util.List;
@Data public class GymDeviceRespVO {
 private String id;private String storeId;private String storeName;private String name;private String type;private String provider;
 private String deviceUid;private String location;private String status;private Boolean online;private Instant lastSeenAt;private List<Compartment> compartments;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymDeviceRespVOCompartment")
 public static class Compartment {private String id;private String number;private String status;private String occupancyState;}
}
