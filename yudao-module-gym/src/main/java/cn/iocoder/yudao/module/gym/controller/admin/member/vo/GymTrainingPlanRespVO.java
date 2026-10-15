package cn.iocoder.yudao.module.gym.controller.admin.member.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class GymTrainingPlanRespVO {
 private String id;
 private String memberId;
 private String memberName;
 private String storeId;
 private String storeName;
 private String title;
 private String content;
 private String fileId;
 private String status;
 private String creator;
 private String updater;
 private LocalDateTime createTime;
 private LocalDateTime updateTime;
}
