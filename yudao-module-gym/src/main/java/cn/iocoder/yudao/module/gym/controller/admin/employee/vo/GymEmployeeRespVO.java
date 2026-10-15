package cn.iocoder.yudao.module.gym.controller.admin.employee.vo;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
@Data
@Schema(description = "员工档案；所有关联 ID 为字符串，任职关系仅返回授权门店")
public class GymEmployeeRespVO {
    private String id;
    private String userId;
    private String username;
    private String employeeNo;
    private String name;
    private String phone;
    private String jobTitle;
    private LocalDate hiredOn;
    private String status;
    private List<String> storeIds;
    private List<StoreAssignment> storeAssignments;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymEmployeeRespVOStoreAssignment")
 public static class StoreAssignment {
        private String storeId;
        private Boolean isPrimary;
        private Boolean isManager;
        private LocalDate joinedOn;
    }
}
