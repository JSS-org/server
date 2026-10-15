package cn.iocoder.yudao.module.gym.dal.mysql.report;
import lombok.Data;
import java.math.BigDecimal;
@Data public class GymCardReportAggregate {
 private Long templateId; private String name; private Integer version;
 private String templateSnapshot; private Long count; private BigDecimal amount;
}
