package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Getter
@Setter
public class MeituanReceiptHistoryRequest {
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String date;

    private Integer bizType;

    @NotNull
    @Min(0)
    private Integer offset = 0;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer type;

    @NotNull
    @Min(1)
    @Max(300)
    private Integer limit = 20;

    @AssertTrue(message = "date 必须为有效日期")
    @JsonIgnore
    public boolean isDateValid() {
        if (date == null || date.isBlank()) return true;
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
