package cn.iocoder.yudao.module.gym.controller.admin.member.vo;

import lombok.Data;

import java.time.*;
import java.util.List;

@Data
public class GymMemberRespVO {
    private String id;
    private String storeId;
    private String memberNo;
    private String name;
    private String phone;
    private String nickname;
    private String gender;
    private LocalDate birthday;
    private String status;
    private String customerStage;
    private Instant registeredAt;
    private List<cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymMemberCardRespVO> cards;
    private List<cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymCoursePackRespVO>
            coursePacks;
    private List<String> storeIds;
    private String storeNames;
    private List<Store> stores;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMemberRespVOStore")
    public static class Store {
        private String id;
        private String name;
    }
}
