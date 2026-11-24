package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤记录，对应表 {@code ssm_emp.attendance}。
 *
 * <p>{@code (empId, workDate)} 上有唯一键：同一人同一天只能有一条记录。
 * 这既是业务规则（一天打一次卡），也是月度汇总不出错的前提——两条数据会让
 * 出勤天数多算一天。
 *
 * <p>{@code checkIn} / {@code checkOut} 用 {@link LocalTime}，对应 MySQL 的 {@code TIME}；
 * 缺勤时两者为 null，因此不能加 {@code @NotNull}。
 */
@Data
@TableName("attendance")
public class Attendance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "att_id", type = IdType.AUTO)
    private Integer attId;

    @NotNull(message = "员工 ID 不能为空")
    private Integer empId;

    @NotNull(message = "工作日期不能为空")
    @PastOrPresent(message = "考勤日期不能晚于今天")
    private LocalDate workDate;

    private LocalTime checkIn;

    private LocalTime checkOut;

    /**
     * 考勤状态：0 正常 / 1 迟到 / 2 早退 / 3 缺勤。
     *
     * <p>**由服务端根据打卡时间推导，不接受客户端传入。** 理由：状态是一份事实的结论，
     * 让录入者手选会出现「09:35 打卡却标成正常」这类自相矛盾的数据，而月度出勤率
     * 完全建立在这个字段上。推导规则见 {@code AttendanceServiceImpl#deriveStatus}。
     */
    private Integer status;

    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;

    /**
     * 员工姓名，仅用于列表展示，**不映射到数据库列**。
     *
     * <p>不加这个字段的话，前端要显示「谁」的考勤就必须拿到 empId 再批量查一次员工接口，
     * 等于把一次关联查询推给了调用方。服务端有现成的批量查询能力，放在这里更合理。
     * 员工被逻辑删除后查不到姓名，回填「已删除员工」。
     */
    @TableField(exist = false)
    private String empName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
