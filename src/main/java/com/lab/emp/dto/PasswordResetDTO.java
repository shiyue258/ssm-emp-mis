package com.lab.emp.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/** 重置口令的请求体。用户 ID 走路径变量，不放在 body 里。 */
@Data
public class PasswordResetDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "新口令不能为空")
    @Size(min = 6, max = 32, message = "口令长度需在 6 到 32 之间")
    private String newPassword;
}
