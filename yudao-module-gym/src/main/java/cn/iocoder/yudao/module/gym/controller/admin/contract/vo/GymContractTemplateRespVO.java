package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;

import lombok.Data;

@Data
public class GymContractTemplateRespVO {
    private String id;
    private String familyId;
    private Integer version;
    private String name;
    private String applicableType;
    private Integer reminderDays;
    private String fileId;
    private String status;
}
