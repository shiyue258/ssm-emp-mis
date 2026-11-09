package com.lab.emp.interceptor;

import com.lab.emp.common.LoginUser;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 登录拦截器：未登录的请求在进入 Controller 之前就被挡下。
 *
 * <p>三个实现细节：
 * <ol>
 *   <li>用 {@code request.getSession(false)} 而不是 {@code getSession()}。
 *       后者在没有会话时会新建一个，结果是「每个匿名请求都产生一个空会话」，
 *       既浪费内存，也让「是否已登录」的判断变成「会话里有没有 loginUser」
 *       这种需要再判空的写法。</li>
 *   <li>HTTP 状态码返回 401，响应体仍是 {@code Result} 结构。
 *       其余业务失败沿用 HTTP 200 + body code，唯独认证失败用 401——
 *       因为 401 是传输层语义，ApiFox 之类的工具默认会据此判定用例失败，
 *       比埋在 body 里更容易被发现。</li>
 *   <li>放行 OPTIONS 预检。跨域请求在携带自定义头时会先发 OPTIONS，
 *       若被拦截器挡下，浏览器控制台只会报一个含糊的 CORS 错误，
 *       真正的原因藏在预检失败里，排查成本很高。</li>
 * </ol>
 *
 * <p>JSON 字符串直接拼接而不是注入 ObjectMapper：本拦截器由
 * {@code SpringMvcConfig} 手动 new 出来（它属于子容器，无法用根容器的 Bean 注入），
 * 而这段内容是固定常量，不含任何用户输入，不存在注入与转义问题。
 */
public class LoginInterceptor implements HandlerInterceptor {

    /** 未登录响应体，与 Result 的结构保持一致。 */
    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"message\":\"未登录或登录已失效\",\"data\":null}";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        Object loginUser = (session == null) ? null : session.getAttribute(LoginUser.SESSION_KEY);
        if (loginUser instanceof LoginUser) {
            return true;
        }
        writeUnauthorized(response);
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
