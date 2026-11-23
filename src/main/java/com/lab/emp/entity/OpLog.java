package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计记录，对应表 {@code ssm_emp.op_log}。
 *
 * <p>与控制台日志的区别：控制台日志重启即失、按行混在一起；这张表可按操作人、
 * 按时间检索，回答「这条数据是谁在什么时候改坏的」。
 *
 * <p>**只记写操作**（POST / PUT / DELETE）。查询操作量大且不改数据，落库会把表撑爆，
 * 而它们能从控制台日志里查到。也**不记请求体全文**——里面可能有口令，
 * 记全文就得再写一套脱敏，得不偿失。
 */
@Data
@TableName("op_log")
public class OpLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 操作人登录名；未登录或取不到时记 anonymous。 */
    private String operator;

    /** HTTP 方法：POST / PUT / DELETE。 */
    private String action;

    /** 目标：请求路径，含路径变量，例如 /users/3/password。 */
    private String target;

    /** 结果说明：成功记 success，失败记异常摘要。 */
    private String detail;

    /** 结果：1 成功 / 0 失败。失败也要留痕——排查时最想知道「谁试图改但没改成」。 */
    private Integer result;

    private LocalDateTime createTime;
}
