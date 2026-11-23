package com.lab.emp.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 新增账号的请求体。
 *
 * <p>不复用 {@code SysUser} 实体：实体里的 {@code passwordHash} 是摘要，
 * 而这里需要的是明文口令。让同一个类既表示「库里那行数据」又表示「用户提交的口令」，
 * 很容易出现把摘要字段当明文用的错误。
 */
@Data
public class UserCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    @NotBlank(message = "口令不能为空")
    @Size(min = 6, max = 32, message = "口令长度需在 6 到 32 之间")
    private String password;

    @NotBlank(message = "显示名不能为空")
    @Size(max = 50, message = "显示名长度不能超过 50")
    private String displayName;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
