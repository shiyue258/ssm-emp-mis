package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工实体，对应表 {@code ssm_emp.emp}。
 *
 * <p>字段与列类型的对应关系：
 * <ul>
 *   <li>{@code salary} 用 {@link BigDecimal} 而非 double —— DECIMAL(10,2) 必须用精确小数承接，
 *       否则 0.1 + 0.2 这类运算会产生肉眼可见的对账误差；</li>
 *   <li>{@code hireDate} 用 {@link LocalDate}，对应 DATE；{@code createTime}/{@code updateTime}
 *       用 {@link LocalDateTime}，对应 DATETIME；</li>
 *   <li>{@code deleted} 标 {@link TableLogic}，MyBatis-Plus 会把 deleteById 改写为
 *       UPDATE ... SET deleted = 1，并在所有查询上自动追加 deleted = 0。</li>
 * </ul>
 *
 * <p>校验注解（JSR-303）写在这里而不是 DTO 上：本项目的写接口直接用实体接收请求体，
 * 实体是唯一的入参契约。字段长度上限与 DDL 中的 VARCHAR 长度保持一致，避免
 * 「应用层放过、数据库截断」的双重标准。
 */
@Data
@TableName("emp")
public class Emp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 员工ID，数据库自增。 */
    @TableId(value = "emp_id", type = IdType.AUTO)
    private Integer empId;

    /** 员工姓名，VARCHAR(50)。 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String empName;

    /** 性别，CHAR(1)，仅允许「男」「女」。 */
    @NotBlank(message = "性别不能为空")
    @Pattern(regexp = "男|女", message = "性别只能是男或女")
    private String gender;

    /** 所属部门，VARCHAR(50)。 */
    @NotBlank(message = "所属部门不能为空")
    @Size(max = 50, message = "部门名称长度不能超过 50")
    private String dept;

    /** 职位，VARCHAR(50)，可为空。 */
    @Size(max = 50, message = "职位名称长度不能超过 50")
    private String post;

    /** 月薪，DECIMAL(10,2)。 */
    @NotNull(message = "薪资不能为空")
    @DecimalMin(value = "0", message = "薪资不能为负数")
    @Digits(integer = 8, fraction = 2, message = "薪资最多 8 位整数与 2 位小数")
    private BigDecimal salary;

    /** 入职日期，DATE。 */
    @NotNull(message = "入职日期不能为空")
    @PastOrPresent(message = "入职日期不能晚于今天")
    private LocalDate hireDate;

    /** 状态：1 在职 / 0 离职，与逻辑删除标记 deleted 是两个不同维度。 */
    @NotNull(message = "在职状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    /** 创建时间，由数据库默认值填充，应用层不赋值。 */
    private LocalDateTime createTime;

    /** 更新时间，由数据库 ON UPDATE 维护。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 正常 / 1 已删除。内部字段，不对外暴露。 */
    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
