package com.lab.emp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录用户实体，对应表 {@code ssm_emp.sys_user}。
 *
 * <p>本表只服务「登录权限拦截」这一个功能，不含角色与细粒度权限。
 * 口令字段存的是 SHA-256 摘要，不是明文——实体里也不出现 password 字段，
 * 从类型层面杜绝「顺手把口令返回给前端」的可能。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.AUTO)
    private Integer userId;

    /** 登录名，唯一。 */
    private String username;

    /** 口令摘要：SHA-256(明文) 的小写十六进制，长度固定 64。 */
    private String passwordHash;

    /** 显示名，登录后返回给前端展示。 */
    private String displayName;

    /** 状态：1 启用 / 0 停用。停用账号即使口令正确也不允许登录。 */
    private Integer status;

    private LocalDateTime createTime;
}
