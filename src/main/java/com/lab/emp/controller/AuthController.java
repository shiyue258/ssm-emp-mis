package com.lab.emp.controller;

import com.lab.emp.common.LoginUser;
import com.lab.emp.common.Result;
import com.lab.emp.dto.LoginDTO;
import com.lab.emp.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

/**
 * 认证接口：登录、退出。
 *
 * <p>这两个路径在 {@code SpringMvcConfig} 的拦截器放行名单里，
 * 否则「未登录不能登录」会形成死锁。
 *
 * <p>登录成功后把 {@link LoginUser} 写入 session，后续请求由容器凭
 * JSESSIONID 找回会话。这里用的是 Servlet 原生 session 而不是自建令牌，
 * 因为指导书的示例就是围绕 session 展开的；真实前后端分离项目通常改用
 * JWT 或 Redis 共享会话，那是另一个话题。
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录。
     *
     * <p>返回体里带上用户信息，便于前端直接渲染「当前登录人」，
     * 省掉一次额外的查询接口。
     */
    @PostMapping("/login")
    public Result<LoginUser> login(@RequestBody @Valid LoginDTO loginDTO, HttpSession session) {
        LoginUser loginUser = authService.login(loginDTO.getUsername(), loginDTO.getPassword());
        session.setAttribute(LoginUser.SESSION_KEY, loginUser);
        return Result.ok(loginUser);
    }

    /**
     * 退出。
     *
     * <p>用 {@code invalidate()} 而不是 {@code removeAttribute()}：
     * 前者销毁整个会话并让 JSESSIONID 失效，后者只删掉一个属性，
     * 会话本身仍然有效，语义上不叫「退出」。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.ok();
    }
}
