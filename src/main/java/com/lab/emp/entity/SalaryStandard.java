package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 薪资标准，对应表 {@code ssm_emp.salary_standard}。
 *
 * <p>为什么不让 {@code emp.salary} 兼任：它只保存「当前」值，一旦调薪，用当前值
 * 补生成历史月份就会算错账——而薪资恰恰是最不能事后改的东西。这张表按
 * {@code (empId, effectiveFrom)} 留存每次调薪，生成时取「生效日不晚于该月末」的最后一条。
 *
 * <p>对外不单独开放接口，由 {@code EmpServiceImpl.update} 在薪资变化时自动写入，
 * 由 {@code SalaryServiceImpl} 在生成时读取。
 */
@Data
@TableName("salary_standard")
public class SalaryStandard implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "std_id", type = IdType.AUTO)
    private Integer stdId;

    @NotNull
    private Integer empId;

    @NotNull(message = "薪资标准不能为空")
    @DecimalMin(value = "0", message = "薪资标准不能为负数")
    private BigDecimal base;

    /** 生效日期（含）。生成某月薪资时取 effectiveFrom <= 该月末 的最后一条。 */
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveFrom;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
