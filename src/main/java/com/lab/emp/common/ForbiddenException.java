package com.lab.emp.common;

/**
 * 权限不足。
 *
 * <p>与 {@code BusinessException} 分开，是为了让 HTTP 状态码能区分：
 * 业务失败返回 200 + code=500（指导书的统一返回风格），权限不足返回 **403**。
 * 混在一起的话，调用方只能靠解析 message 判断是不是没权限。
 */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
