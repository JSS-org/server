package cn.iocoder.yudao.module.gym.controller.admin.salary.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class GymSalaryTemplateRespVO {
    private String id;
    private String familyId;
    private String name;
    private Integer version;
    private BigDecimal baseSalary;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private List<GymSalaryTemplateSaveReqVO.Rule> rules;
}
