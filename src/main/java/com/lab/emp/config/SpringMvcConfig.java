package com.lab.emp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.lab.emp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SpringMVC 子容器配置。
 *
 * <p>{@code @EnableWebMvc} 会注册 HandlerMapping、HandlerAdapter、
 * 消息转换器等一整套组件。它同时意味着「完全接管 SpringMVC 的默认配置」，
 * 因此不要与 {@code <mvc:annotation-driven/>} 同时出现。
 *
 * <p>{@code @EnableAspectJAutoProxy} 必须放在这里而不是根容器：切面织入的是
 * Controller，而 Controller 只存在于子容器。AOP 的 Advisor 不跨容器生效——
 * 在根容器开启代理，子容器里的 Controller 拿不到任何通知，切面会「注册成功但不执行」。
 *
 * <p>扫描范围除 controller 外还包含 aspect：切面组件必须与它要代理的目标
 * 处于同一个容器，否则同样不生效。根容器侧已通过正则过滤排除 aspect 包，
 * 避免同一个切面被两个容器各实例化一次。
 */
@Configuration
@EnableWebMvc
@EnableAspectJAutoProxy
@ComponentScan({"com.lab.emp.controller", "com.lab.emp.aspect"})
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

    /**
     * 定制 JSON 序列化器，重点是 Java 8 时间类型。
     *
     * <p>这里踩过一次坑，值得记下来：{@code @EnableWebMvc} 默认装配的
     * {@link Jackson2ObjectMapperBuilder} <b>并不会</b>关闭
     * {@code WRITE_DATES_AS_TIMESTAMPS}——那是 Spring Boot 的
     * {@code JacksonAutoConfiguration} 才做的事。纯 SSM 环境下不加这段配置，
     * {@code LocalDate} 会被序列化成 {@code [2021,3,15]} 这样的数组，
     * 前端拿到的不是日期字符串。
     *
     * <p>用 {@code extendMessageConverters} 而不是 {@code configureMessageConverters}：
     * 后者会清空默认转换器列表，导致 String、Resource 等基础类型的处理一起丢失。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializerByType(LocalDate.class,
                        new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .serializerByType(LocalDateTime.class,
                        new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();

        // 原位替换默认的 Jackson 转换器，保持它在列表中的优先级不变
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof MappingJackson2HttpMessageConverter) {
                converters.set(i, new MappingJackson2HttpMessageConverter(objectMapper));
                return;
            }
        }
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
    }

    /**
     * 注册登录拦截器。
     *
     * <p>放行名单里必须包含 {@code /login}：否则「未登录不能访问」会把登录接口本身
     * 也拦掉，形成死锁。{@code /health} 两条同样放行——它们是给运维和部署脚本用的
     * 探活端点，不该依赖会话状态。
     *
     * <p>没有放行 {@code /logout}：退出操作本身就要求已登录，
     * 未登录时调用返回 401 是合理语义。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/health", "/health/db");
    }
}
