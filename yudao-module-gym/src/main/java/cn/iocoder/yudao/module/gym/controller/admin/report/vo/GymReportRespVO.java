package cn.iocoder.yudao.module.gym.controller.admin.report.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public final class GymReportRespVO {
 private GymReportRespVO() {}
 public record Page<T>(List<T> list,long total,Summary summary) {}
 public record Summary(BigDecimal netAmount,long orderCount) {}
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymReportRespVOPerformance")
 public static class Performance {
  private String id; private String name;
  private BigDecimal netAmount; private BigDecimal cardAmount; private BigDecimal ptAmount;
  private long orderCount; private long employeeCount;
  private BigDecimal averageEmployeeAmount; private BigDecimal averageOrderAmount;
 }
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymReportRespVOMemberCards")
 public static class MemberCards {
  private String id; private String name; private Integer version;
  private long soldCount; private BigDecimal netAmount=new BigDecimal("0.00");
  private long holderCount; private long usageCount;
 }
 public record Members(String id,String storeName,long totalMembers,long newMembers,long leads,long expiringCards,long expiringCourses) {}
 public record Dashboard(String from,String to,Metrics metrics,List<Trend> trend,List<Segment> memberSegments,List<PendingApproval> pendingApprovals) {}
 public record Metrics(BigDecimal netAmount,long entries,long newMembers,long activeMembers) {}
 public record Trend(String date,BigDecimal netAmount,long entries) {}
 public record Segment(String name,long value) {}
 public record PendingApproval(String id,String title,String businessType,String storeName,String applicantName,Instant submittedAt) {}
}
