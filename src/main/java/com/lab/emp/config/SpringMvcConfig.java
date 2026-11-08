package com.lab.emp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
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
