package com.lab.emp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把各类异常统一翻译成 {@code Result} 结构。
 *
 * <p>不加这层时，异常会走容器默认流程——返回 HTML 错误页或 Spring 的
 * 默认 JSON，结构与成功响应不一致，前端要写两套解析逻辑。
 *
 * <p>三条出口，按从具体到宽泛排列：
 * <ol>
 *   <li>{@link BusinessException}：可预期的业务失败，warn 级日志，原样返回 message；</li>
 *   <li>{@link BindException}：参数校验失败，把字段级提示拼成一句话返回；
 *       注意 Spring 5.3 起 {@code MethodArgumentNotValidException} 继承自它，
 *       所以 {@code @RequestBody @Valid} 的场景也被这一条覆盖；</li>
 *   <li>{@link Exception}：兜底。只记录日志，对外返回固定文案，
 *       避免把表名、SQL、堆栈这类内部信息泄露给调用方。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：调用方能看懂，直接回传。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：把所有字段提示拼成一句，便于前端一次性展示。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", message);
        return Result.fail(message);
    }

    /** 请求体不是合法 JSON，或字段类型无法转换。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.fail("请求体格式错误，请检查 JSON 结构与字段类型");
    }

    /** 兜底：未预料的异常。对外模糊，对内完整。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
