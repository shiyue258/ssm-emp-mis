package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薪资发放记录，对应表 {@code ssm_emp.salary_record}。
 *
 * <p>{@code total} 由服务端计算，不接受前端传入。理由：若允许前端报总额，
 * 一旦出现 {@code base + bonus - deduction != total}，事后无法判断是前端算错
 * 还是有人手工改过。金额一致性应由服务端保证，且只有一处计算逻辑。
 */
@Data
@TableName("salary_record")
public class SalaryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @NotNull(message = "员工 ID 不能为空")
    private Integer empId;

    @NotBlank(message = "薪资月份不能为空")
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "薪资月份格式需为 YYYY-MM")
    private String month;

    @NotNull(message = "基本工资不能为空")
    @DecimalMin(value = "0", message = "基本工资不能为负数")
    private BigDecimal base;

    @NotNull(message = "奖金不能为空")
    @DecimalMin(value = "0", message = "奖金不能为负数")
    private BigDecimal bonus;

    @NotNull(message = "扣款不能为空")
    @DecimalMin(value = "0", message = "扣款不能为负数")
    private BigDecimal deduction;

    /** 实发金额，服务端计算，入参忽略。 */
    private BigDecimal total;

    @Min(value = 0, message = "发放状态只能是 0 或 1")
    @Max(value = 1, message = "发放状态只能是 0 或 1")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 员工姓名，仅用于列表展示，不映射数据库列；员工已删时回填「已删除员工」。 */
    @TableField(exist = false)
    private String empName;
}
