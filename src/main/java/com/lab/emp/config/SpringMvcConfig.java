package com.lab.emp.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SpringMVC 子容器配置。
 *
 * <p>{@code @EnableWebMvc} 会注册 HandlerMapping、HandlerAdapter、
 * 消息转换器等一整套组件。它同时意味着「完全接管 SpringMVC 的默认配置」，
 * 因此不要与 {@code <mvc:annotation-driven/>} 同时出现。
 */
@Configuration
@EnableWebMvc
@ComponentScan("com.lab.emp.controller")
public class SpringMvcConfig implements WebMvcConfigurer {

    /**
     * 静态资源交给容器默认 Servlet 处理。
     *
     * <p>DispatcherServlet 映射为 {@code "/"} 后会吞掉所有请求，
     * 不开启这一项的话，war 内的静态文件（如 ApiFox 导出的 HTML 报告）
     * 会 404。
     */
    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    /**
     * 全局跨域配置，适配前后端分离调试。
     *
     * <p>注意 {@code allowedOriginPatterns} 与 {@code allowCredentials(true)} 的组合：
     * 若用 {@code allowedOrigins("*")} 再开启凭证，浏览器会直接拒绝响应，
     * 因为规范不允许「任意来源 + 携带凭证」。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /*
     * 登录拦截器注册位（任务 6 补齐）：
     *
     *   @Override
     *   public void addInterceptors(InterceptorRegistry registry) {
     *       registry.addInterceptor(new LoginInterceptor())
     *               .addPathPatterns("/**")
     *               .excludePathPatterns("/login", "/health", "/health/db");
     *   }
     *
     * 现在不写，是因为 LoginInterceptor 尚未实现，提前注册会编译不过。
     */
}
