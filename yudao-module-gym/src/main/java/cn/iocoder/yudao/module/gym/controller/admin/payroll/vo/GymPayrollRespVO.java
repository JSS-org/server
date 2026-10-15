package cn.iocoder.yudao.module.gym.controller.admin.payroll.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Data public class GymPayrollRespVO {
 private String id; private String storeId; private String storeName; private String month;
 private Integer employeeCount; private BigDecimal netAmount; private String status;
 private Instant calculatedAt; private Instant confirmedAt; private List<Item> items;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymPayrollRespVOItem")
 public static class Item {
  private String id; private String employeeId; private String employeeName;
  private BigDecimal baseSalary; private BigDecimal cardCommission; private BigDecimal ptCommission;
  private BigDecimal lessonCommission; private BigDecimal allowances; private BigDecimal deductions;
  private BigDecimal grossAmount; private BigDecimal netAmount;
  private Map<String,Object> calculationSnapshot; private List<Component> components;
 }
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymPayrollRespVOComponent")
 public static class Component {
  private String id; private String label; private String component;
  private BigDecimal basisAmount; private Long basisQuantity; private BigDecimal rate; private BigDecimal amount;
  private Map<String,Object> sourceSnapshot;
 }
}
