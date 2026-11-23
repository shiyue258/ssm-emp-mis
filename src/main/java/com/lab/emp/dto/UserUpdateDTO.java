package com.lab.emp.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/** 修改账号的请求体：只允许改显示名与状态，用户名不可变。 */
@Data
public class UserUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户 ID 不能为空")
    private Integer userId;

    @NotBlank(message = "显示名不能为空")
    @Size(max = 50, message = "显示名长度不能超过 50")
    private String displayName;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
