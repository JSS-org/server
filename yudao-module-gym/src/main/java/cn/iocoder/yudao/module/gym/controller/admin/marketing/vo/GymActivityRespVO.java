package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class GymActivityRespVO {
    private String id;
    private String name;
    private String description;
    private List<String> storeIds;
    private String storeNames;
    private Instant registrationStartsAt;
    private Instant registrationEndsAt;
    private Instant startsAt;
    private Instant endsAt;
    private Integer capacity;
    private Long registrationCount;
    private String coverFileId;
    private String status;
}
