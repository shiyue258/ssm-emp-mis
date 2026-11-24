package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 角色-权限关联，对应表 {@code ssm_emp.role_permission}（联合主键）。 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer roleId;

    private Integer permId;
}
