package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限点，对应表 {@code ssm_emp.permission}。
 *
 * <p>编码形如 {@code emp:write}（模块:动作），与 {@code @RequirePermission} 的取值一一对应。
 * 权限点由种子数据维护，不提供增删接口——权限点是代码里真实存在的校验规则，
 * 允许运行时新增会造出「永远不可能被使用」的僵尸权限。
 */
@Data
@TableName("permission")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "perm_id", type = IdType.AUTO)
    private Integer permId;

    /** 权限编码，如 emp:write */
    private String permCode;

    /** 权限名称，如「维护员工」 */
    private String permName;

    /** 所属模块 */
    private String module;

    private LocalDateTime createTime;
}
