package com.lab.emp.service;

import com.lab.emp.config.SpringConfig;
import com.lab.emp.service.support.TransactionFailureDemo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.sql.DataSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 事务失效三场景的验证用例。
 *
 * <p>四个用例构成一组对照：A 证明「配对了确实会回滚」，B/C/D 分别破坏一个前提，
 * 证明「会静默不回滚」。只写 B/C/D 是不够的——没有 A 作参照，
 * 就无法排除「整个事务机制根本没配好」这种可能。
 *
 * <p>断言方式刻意选「查数据库里有没有那一行」而不是「方法有没有抛异常」：
 * 三个失效场景中异常照样会抛（B 除外），用异常判断会全部误判为正常。
 * 事务是否生效，只能看数据。
 *
 * <p>测试直接加载 {@link SpringConfig} 根容器，不启动 SpringMVC——
 * 事务是根容器的事，与 Web 层无关，这样跑得快，也不依赖 Tomcat。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfig.class)
public class TransactionFailureTest {

    /** 测试数据统一前缀，便于收尾时精确清理。 */
    private static final String NAME_PREFIX = "事务测试_";

    @Autowired
    private TransactionFailureDemo demo;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        cleanUp();
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    /**
     * 场景 A（对照）：注解在 public 方法、外部经代理调用、异常不被捕获 —— 应当回滚。
     */
    @Test
    public void case1_transactionWorks() {
        String name = NAME_PREFIX + "对照";
        try {
            demo.normalRollback(name);
            fail("对照方法应当抛出 IllegalStateException");
        } catch (IllegalStateException expected) {
            // 预期内
        }
        assertEquals("事务正常生效时，插入的行应随回滚消失", 0, countByName(name));
    }

    /**
     * 场景 B：异常被 try-catch 吞掉 —— 事务管理器收不到回滚信号，行会残留。
     */
    @Test
    public void case2_swallowedExceptionLeavesRow() {
        String name = NAME_PREFIX + "吞异常";
        demo.swallowException(name);
        assertEquals("异常被吞掉，方法正常返回，事务应被提交而非回滚", 1, countByName(name));
    }

    /**
     * 场景 C：this 自调用绕过代理 —— 内层方法的注解不生效，行会残留。
     */
    @Test
    public void case3_selfInvocationLeavesRow() {
        String name = NAME_PREFIX + "自调用";
        try {
            demo.selfInvocation(name);
            fail("内层方法应当抛出 IllegalStateException");
        } catch (IllegalStateException expected) {
            // 异常照常抛出，但事务从未开启
        }
        assertEquals("自调用未经代理，内层事务不生效，行应残留", 1, countByName(name));
    }

    /**
     * 场景 D：注解加在 private 方法上 —— 被静默忽略，行会残留。
     */
    @Test
    public void case4_privateMethodLeavesRow() {
        String name = NAME_PREFIX + "私有方法";
        try {
            demo.privateMethod(name);
            fail("private 方法应当抛出 IllegalStateException");
        } catch (IllegalStateException expected) {
            // 预期内
        }
        assertEquals("private 方法上的 @Transactional 被忽略，行应残留", 1, countByName(name));
    }

    // ------------------------------------------------------------------

    private int countByName(String empName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from emp where emp_name = ?", Integer.class, empName);
        return count == null ? 0 : count;
    }

    /**
     * 物理删除测试数据。
     *
     * <p>不能用 {@code EmpMapper.delete}：那个方法走的是逻辑删除（{@code deleted = 1}），
     * 行还在表里，既污染后续断言，也会让 {@code select count(*)} 的核对对不上。
     * 这里绕开 ORM 直接发 DELETE，正是「测试代码可以用生产代码不该用的手段」的例子。
     */
    private void cleanUp() {
        jdbcTemplate.update("delete from emp where emp_name like ?", NAME_PREFIX + "%");
    }
}
