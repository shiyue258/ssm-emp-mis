package com.lab.emp.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 月度考勤汇总的一行。
 *
 * <p>这是**查询模型**，不是表映射：一张表存明细，这里按人聚合出统计值。
 * 与之相对，{@code Attendance} 是写入模型。两者分开的好处是聚合口径
 * 可以独立调整（比如以后要加「加班天数」），不必动表结构。
 */
@Data
public class AttendanceSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer empId;

    private String empName;

    /** 该月有考勤记录的天数（含缺勤）。 */
    private int totalDays;

    private int normalDays;

    private int lateDays;

    private int earlyDays;

    private int absentDays;

    /** 出勤率 = (总天数 - 缺勤天数) / 总天数，保留两位小数；无记录时为 0。 */
    private double attendanceRate;
}
