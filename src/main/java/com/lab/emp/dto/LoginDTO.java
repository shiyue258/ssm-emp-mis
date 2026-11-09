package com.lab.emp.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 登录请求参数。
 *
 * <p>单独建这个 DTO 而不是复用 {@code SysUser}：实体里带 {@code passwordHash}，
 * 若直接用它接请求体，前端就得传摘要而非明文，语义反了；
 * 更重要的是请求参数与数据库字段解耦后，改动一端不会牵连另一端。
 *
 * <p>{@code password} 字段在日志切面里会被显式打码，见 {@code WebLogAspect}。
 */
@Data
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
