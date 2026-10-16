package cn.iocoder.yudao.module.gym.controller.admin.card.vo;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class GymMemberCardRespVO {
    private String id;
    private String memberId;
    private String issuedStoreId;
    private String templateId;
    private String cardNo;
    private String displayPrefix;
    private String name;
    private String kind;
    private String accessScope;
    private List<String> storeIds;
    private Instant startsAt;
    private Instant expiresAt;
    private Integer totalUses;
    private Integer remainingUses;
    private Integer reservedUses;
    private String status;
}
