package com.lab.emp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.emp.entity.Emp;

/**
 * 员工持久层。
 *
 * <p>继承 {@link BaseMapper} 即可获得单表 CRUD、条件构造器与分页能力，
 * 无需写一行 SQL：{@code selectById} / {@code selectPage} / {@code insert} /
 * {@code updateById} / {@code deleteById} 全部由 MyBatis-Plus 在启动时注入。
 *
 * <p>装配方式：由 {@code SpringConfig} 里的 {@code MapperScannerConfigurer}
 * 扫描本包生成代理 Bean，因此接口上不需要 {@code @Mapper} 注解。
 * 但注意工厂必须使用 {@code MybatisSqlSessionFactoryBean} 而非原生
 * {@code SqlSessionFactoryBean}，否则上面这些注入方法会整体失效，
 * 表现为运行期 {@code Invalid bound statement (not found)}。
 *
 * <p>若后续出现 BaseMapper 无法表达的 SQL（多表关联、批量更新带条件），
 * 再在本包下补 {@code EmpMapper.xml}，与接口同包同名。
 */
public interface EmpMapper extends BaseMapper<Emp> {
}
