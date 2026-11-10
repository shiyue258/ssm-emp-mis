package com.lab.emp.service.support;

import com.lab.emp.entity.Emp;
import com.lab.emp.mapper.EmpMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 事务失效的四个对照示例，**仅供测试使用**。
 *
 * <p>本类刻意写了三段「看起来有事务、实际不回滚」的代码。放在 {@code src/test} 而不是
 * {@code src/main}，原因有两点：
 * <ul>
 *   <li>打包 war 时测试源码不参与编译，生产产物里不会出现这些明知有问题的代码；</li>
 *   <li>但它必须在 Spring 容器里才能验证代理行为，所以仍需 {@code @Service} 注解——
 *       测试运行时由 {@code SpringConfig} 的包扫描装配，属于「只在测试期存在的 Bean」。</li>
 * </ul>
 *
 * <p>四个方法的预期差异见 {@code TransactionFailureTest}，那里有逐条断言。
 */
@Service
public class TransactionFailureDemo {

    private final EmpMapper empMapper;

    public TransactionFailureDemo(EmpMapper empMapper) {
        this.empMapper = empMapper;
    }

    // ------------------------------------------------------------------
    // 场景 A：对照组 —— 事务正常生效
    // ------------------------------------------------------------------

    /**
     * 标准写法：注解在 public 方法上、由外部通过代理调用、异常不被捕获。
     * 三条都满足，事务才会真正回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void normalRollback(String empName) {
        insertOne(empName);
        throw new IllegalStateException("对照组：故意抛出，验证回滚");
    }

    // ------------------------------------------------------------------
    // 场景 B：异常被 try-catch 吞掉
    // ------------------------------------------------------------------

    /**
     * 失效原因：事务管理器只能通过「方法是否抛出异常」判断要不要回滚。
     * 异常在这里被吞掉，方法正常返回，事务被正常提交。
     *
     * <p>这是三者中最隐蔽的一种——代码里明明写了 {@code @Transactional}，
     * 日志里也没有任何异常，只有数据对不上。
     */
    @Transactional(rollbackFor = Exception.class)
    public void swallowException(String empName) {
        insertOne(empName);
        try {
            throw new IllegalStateException("场景B：异常被吞掉");
        } catch (Exception ignored) {
            // 吞掉异常 = 告诉事务管理器「一切正常」
        }
    }

    // ------------------------------------------------------------------
    // 场景 C：this 自调用，绕过代理
    // ------------------------------------------------------------------

    /**
     * 失效原因：Spring 事务依赖代理对象。外部调用先进代理，代理再调真实对象；
     * 而 {@code this.innerTransactional(...)} 中的 {@code this} 是**真实对象自身**，
     * 调用根本不经过代理，所以内层方法上的 {@code @Transactional} 不会被处理。
     *
     * <p>注意异常仍然会向外抛出——失效的不是「抛不抛」，而是「内层方法压根没开事务」。
     */
    public void selfInvocation(String empName) {
        this.innerTransactional(empName);
    }

    @Transactional(rollbackFor = Exception.class)
    public void innerTransactional(String empName) {
        insertOne(empName);
        throw new IllegalStateException("场景C：内层方法抛出，但事务从未开启");
    }

    // ------------------------------------------------------------------
    // 场景 D：注解加在 private 方法上
    // ------------------------------------------------------------------

    /**
     * 失效原因：CGLIB 通过继承生成代理类，无法覆写 {@code private} 方法；
     * 同时 Spring 默认的 {@code AnnotationTransactionAttributeSource}
     * 只处理方法可见性为 public 的注解，非 public 上的 {@code @Transactional}
     * 会被**静默忽略**——不报错、不告警。
     *
     * <p>顺带一提：同理，{@code final} 方法与 {@code static} 方法上的注解也不会生效。
     */
    public void privateMethod(String empName) {
        doPrivate(empName);
    }

    @Transactional(rollbackFor = Exception.class)
    private void doPrivate(String empName) {
        insertOne(empName);
        throw new IllegalStateException("场景D：private 方法上的注解被忽略");
    }

    // ------------------------------------------------------------------

    private void insertOne(String empName) {
        Emp emp = new Emp();
        emp.setEmpName(empName);
        emp.setGender("男");
        emp.setDept("事务测试部");
        emp.setPost("事务测试岗");
        emp.setSalary(new BigDecimal("1000.00"));
        emp.setHireDate(LocalDate.of(2026, 1, 1));
        emp.setStatus(1);
        empMapper.insert(emp);
    }
}
