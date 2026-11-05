package com.lab.emp.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一返回结构：{@code {code, message, data}}。
 *
 * <p>设计要点：
 * <ul>
 *   <li>构造函数私有，只暴露静态工厂方法 —— 调用方无法拼出 {@code code=200} 却带错误信息的畸形结果；</li>
 *   <li>成功与失败使用固定语义码，前端只需判断 {@code code == 200}，不必解析 message 文本；</li>
 *   <li>响应体不包含时间戳与请求路径，保持结构稳定，便于接口契约长期不变。</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功 */
    public static final int CODE_SUCCESS = 200;

    /** 业务失败 / 服务端异常 */
    public static final int CODE_FAIL = 500;

    /** 未登录或登录态失效 */
    public static final int CODE_UNAUTHORIZED = 401;

    private Integer code;

    private String message;

    private T data;

    /** 成功，无返回数据。 */
    public static <T> Result<T> ok() {
        return new Result<>(CODE_SUCCESS, "success", null);
    }

    /** 成功，带返回数据。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    /** 业务失败，使用默认失败码。 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(CODE_FAIL, message, null);
    }

    /** 业务失败，指定返回码（例如参数校验失败时也可复用）。 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 未登录。 */
    public static <T> Result<T> unauthorized() {
        return new Result<>(CODE_UNAUTHORIZED, "未登录或登录已失效", null);
    }
}
