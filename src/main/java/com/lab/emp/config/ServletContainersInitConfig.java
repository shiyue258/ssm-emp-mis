package com.lab.emp.config;

import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.Filter;

/**
 * Web 容器初始化器，取代 {@code web.xml}。
 *
 * <p>Servlet 3.0 起，容器启动时会通过 SPI 扫描 classpath 下所有
 * {@code ServletContainerInitializer} 实现，Spring 提供的
 * {@code SpringServletContainerInitializer} 会回调本类。
 * 因此本工程不需要 {@code web.xml}，但 war 插件必须关闭
 * {@code failOnMissingWebXml}，否则打包阶段直接失败。
 *
 * <p>父子容器划分：
 * <ul>
 *   <li>根容器（{@link SpringConfig}）：Service、Mapper、事务管理器、数据源，
 *       由 ContextLoaderListener 加载，对子容器可见但不可反向访问；</li>
 *   <li>子容器（{@link SpringMvcConfig}）：Controller、HandlerMapping、
 *       视图与消息转换器，由 DispatcherServlet 加载。</li>
 * </ul>
 * 划分错误的典型症状是「Controller 注入了 Service 却报
 * NoSuchBeanDefinitionException」——根容器扫到 Controller 不会报错，
 * 但子容器里那个 Controller 是新实例，与根容器里的不是同一个。
 */
public class ServletContainersInitConfig extends AbstractAnnotationConfigDispatcherServletInitializer {

    /** 根容器配置 */
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{SpringConfig.class};
    }

    /** SpringMVC 子容器配置 */
    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{SpringMvcConfig.class};
    }

    /** DispatcherServlet 映射到根路径，由 SpringMVC 接管全部请求 */
    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }

    /**
     * 编码过滤器。
     *
     * <p>{@code forceEncoding = true} 是必需的：它不仅设置响应编码，
     * 还会强制把请求编码也设为 UTF-8。只设 {@code encoding} 时，
     * POST 表单与某些容器下的请求体会按 ISO-8859-1 解析，中文变问号。
     */
    @Override
    protected Filter[] getServletFilters() {
        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding("UTF-8");
        encodingFilter.setForceEncoding(true);
        return new Filter[]{encodingFilter};
    }
}
