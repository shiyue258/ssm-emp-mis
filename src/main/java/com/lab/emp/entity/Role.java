package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色，对应表 {@code ssm_emp.role}。
 *
 * <p>角色是权限的载体，本身不代表具体能力：能做什么由 {@code role_permission}
 * 决定，而不是在代码里判断「是不是 ADMIN」。这样新增一个「财务」角色时只改数据，
 * 不动注解与切面。
 */
@Data
@TableName("role")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "role_id", type = IdType.AUTO)
    private Integer roleId;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 30, message = "角色编码长度不能超过 30")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过 50")
    private String roleName;

    @Size(max = 100, message = "说明长度不能超过 100")
    private String remark;

    private LocalDateTime createTime;
}
