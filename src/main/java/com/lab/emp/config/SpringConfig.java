package com.lab.emp.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Spring 根容器配置：数据源、MyBatis-Plus、Mapper 扫描、事务管理器。
 *
 * <p>扫描边界是本类最容易出错的地方，三个排除项各有原因：
 * <ul>
 *   <li><b>排除 {@code @Controller}</b>：否则 Controller 会被实例化两份——根容器一份、
 *       子容器一份，表现是「Service 注入为 null」或「拦截器不生效」；</li>
 *   <li><b>排除 {@code aspect} 包</b>：切面必须与它代理的 Controller 同处子容器才生效，
 *       留在根容器里只会多出一个不工作的实例；</li>
 *   <li><b>排除 {@link SpringMvcConfig}</b>：它本身也是 {@code @Configuration}，
 *       同样会被组件扫描命中。若不清除，根容器会照着 {@code @EnableWebMvc}
 *       再建一整套 HandlerMapping / HandlerAdapter——Web 环境下因为有
 *       ServletContext 而不报错，但在纯容器测试环境里会直接抛
 *       {@code IllegalStateException: No ServletContext set}。</li>
 * </ul>
 */
@Configuration
@ComponentScan(
        basePackages = "com.lab.emp",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.lab\\.emp\\.aspect\\..*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SpringMvcConfig.class)
        })
@PropertySource("classpath:jdbc.properties")
@EnableTransactionManagement
public class SpringConfig {

    @Value("${jdbc.driver}")
    private String driver;

    @Value("${jdbc.url}")
    private String url;

    @Value("${jdbc.username}")
    private String username;

    @Value("${jdbc.password}")
    private String password;

    @Value("${jdbc.initialSize:5}")
    private int initialSize;

    @Value("${jdbc.maxActive:20}")
    private int maxActive;

    @Value("${jdbc.minIdle:5}")
    private int minIdle;

    @Value("${jdbc.maxWait:3000}")
    private long maxWait;

    /**
     * 保证 {@code ${...}} 占位符能被解析。
     *
     * <p>必须是 static 方法：该后置处理器负责解析其它 Bean 定义里的占位符，
     * 若它本身被延迟到普通 Bean 的实例化阶段才创建，轮到自己人时已经晚了。
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    /**
     * Druid 数据源。
     *
     * <p>密码支持环境变量 {@code EMP_DB_PASSWORD} 覆盖：配置文件里写的是本地
     * Docker 环境的默认值，接入真实环境时不应把口令提交进仓库。
     */
    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        DruidDataSource ds = new DruidDataSource();
        ds.setDriverClassName(driver);
        ds.setUrl(url);
        ds.setUsername(username);
        String envPassword = System.getenv("EMP_DB_PASSWORD");
        ds.setPassword((envPassword == null || envPassword.isEmpty()) ? password : envPassword);
        ds.setInitialSize(initialSize);
        ds.setMaxActive(maxActive);
        ds.setMinIdle(minIdle);
        ds.setMaxWait(maxWait);
        ds.setValidationQuery("SELECT 1");
        ds.setTestWhileIdle(true);
        return ds;
    }

    /**
     * MyBatis-Plus 的 SqlSessionFactory。
     *
     * <p>必须用 {@link MybatisSqlSessionFactoryBean} 而不是 mybatis-spring 的
     * {@code SqlSessionFactoryBean}：后者产出的是原生 {@code Configuration}，
     * BaseMapper 上的通用 CRUD 不会被注入，运行期报
     * {@code Invalid bound statement (not found)}。这与实验一
     * 「必须用 MybatisXMLConfigBuilder」是同一类坑。
     */
    @Bean
    public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("com.lab.emp.entity");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(Log4jImpl.class);
        factory.setConfiguration(configuration);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(interceptor);

        return factory;
    }

    /** 扫描 Mapper 接口，生成代理 Bean；接口上无需 @Mapper 注解。 */
    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.lab.emp.mapper");
        return configurer;
    }

    /** 声明式事务的实现基础，@Transactional 最终由它控制连接提交与回滚。 */
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
