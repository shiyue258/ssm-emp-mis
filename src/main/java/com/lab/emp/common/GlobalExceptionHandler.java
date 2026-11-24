package com.lab.emp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * 路径存在但不支持该 HTTP 方法，例如对 {@code /health} 发 POST。
     *
     * <p>必须显式声明，否则会被下面的兜底分支吞掉：{@code Exception.class} 的优先级最低，
     * 但 {@code HttpRequestMethodNotSupportedException} 属于 {@code Exception}，
     * 一旦没有更具体的处理器，就会被翻译成「系统繁忙」并以 HTTP 200 返回——
     * 调用方看到的是一个业务错误，而真实原因是「方法用错了」。
     * 这个缺陷曾实际存在，由接口用例集回归时发现。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法：{}", e.getMethod());
        return Result.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                "不支持的请求方法：" + e.getMethod());
    }

    /**
     * 权限不足，由 {@code PermissionAspect} 抛出。
     *
     * <p>用 HTTP 403 而不是 200 + code=500：调用方（含 ApiFox 用例）需要能一眼区分
     * 「业务失败」与「没权限」。若混在 200 里，只能靠解析 message 判断。
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleForbidden(ForbiddenException e) {
        log.warn("权限不足：{}", e.getMessage());
        return Result.fail(HttpStatus.FORBIDDEN.value(), e.getMessage());
    }

    /**
     * 唯一键冲突，例如两个并发请求同时登记同一天考勤。
     *
     * <p>业务层都做了「先查重再插入」的预检查，但预检查挡不住并发：两个线程可能
     * 同时通过检查，最后由数据库唯一键裁定胜负。此时若不单独处理，异常会落到
     * 兜底分支变成「系统繁忙」——用户完全不知道是自己重复提交了。
     *
     * <p>实测：5 个并发登记同一天考勤，1 个成功、4 个命中此分支。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突：{}", e.getMessage());
        return Result.fail("该记录已存在，请勿重复提交");
    }

    /**
     * 缺少必需的查询参数，例如 {@code GET /attendance/summary} 不带 month。
     *
     * <p>与 405 同类：不加这一条会被兜底成 HTTP 200 + 「系统繁忙」，调用方完全
     * 不知道是自己漏传了参数。这类「框架自带状态码的异常」必须逐个显式声明，
     * 兜底分支只应处理真正预料之外的情况。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必需参数：{}", e.getParameterName());
        return Result.fail(HttpStatus.BAD_REQUEST.value(),
                "缺少必需参数：" + e.getParameterName());
    }

    /**
     * 请求的 Content-Type 不受支持，例如把 JSON 接口当表单提交。
     *
     * <p>不声明的话同样会被兜底吃掉，正确的语义是 415。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型：{}", e.getContentType());
        return Result.fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "不支持的请求类型：" + e.getContentType() + "，请使用 application/json");
    }

    /**
     * 路径变量类型不匹配，例如 {@code GET /emps/abc}。
     *
     * <p>同理，不加这一条会被兜底成 HTTP 200 + 「系统繁忙」，
     * 而正确的语义是 400：客户端传了不该传的东西。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}={}", e.getName(), e.getValue());
        return Result.fail(HttpStatus.BAD_REQUEST.value(),
                "参数格式不正确：" + e.getName());
    }

    /** 兜底：未预料的异常。对外模糊，对内完整。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
