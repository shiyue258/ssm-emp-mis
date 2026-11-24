package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 账号-角色关联，对应表 {@code ssm_emp.user_role}（联合主键，无自增 id）。 */
@Data
@TableName("user_role")
public class UserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer userId;

    private Integer roleId;
}
