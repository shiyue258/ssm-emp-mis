package com.lab.emp.common;

import java.io.Serializable;

/**
 * 业务异常：表示「可预期的、调用方能自行处理」的失败，例如员工不存在、ID 为空。
 *
 * <p>与运行时异常的区别在于语义：本类抛出的异常一定会被
 * {@link GlobalExceptionHandler} 捕获并翻译成 {@code Result.fail}，
 * 不会把堆栈泄露给前端，也不需要记录 error 级日志。
 *
 * <p>继承 {@link RuntimeException} 而非受检异常，是为了不污染 Service 接口签名——
 * 若继承 Exception，每个方法都要声明 throws，调用方还要逐个 try，得不偿失。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务返回码，默认沿用统一返回结构里的失败码。 */
    private final int code;

    public BusinessException(String message) {
        this(Result.CODE_FAIL, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
