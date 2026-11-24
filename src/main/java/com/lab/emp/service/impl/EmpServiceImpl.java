package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.BusinessException;
import com.lab.emp.dto.EmpQueryDTO;
import com.lab.emp.entity.Dept;
import com.lab.emp.entity.Emp;
import com.lab.emp.entity.SalaryStandard;
import com.lab.emp.mapper.DeptMapper;
import com.lab.emp.mapper.EmpMapper;
import com.lab.emp.mapper.SalaryStandardMapper;
import com.lab.emp.service.EmpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工业务实现，事务边界所在层。
 *
 * <p>{@code @Transactional} 标在实现类的方法上而不是接口上，原因有二：
 * <ol>
 *   <li>Spring 基于代理实现事务，注解写在接口方法上时，JDK 动态代理可以读到，
 *       但一旦切换为 CGLIB 代理（类没有实现接口、或强制 proxyTargetClass），
 *       接口上的注解就不可见，事务会静默失效；</li>
 *   <li>实现类才是真正执行 SQL 的地方，把边界画在这里语义更准确。</li>
 * </ol>
 *
 * <p>统一写 {@code rollbackFor = Exception.class}：Spring 默认只对
 * {@link RuntimeException} 与 {@link Error} 回滚，受检异常不回滚。
 * 现在方法里没有受检异常，但以后加一个「导出失败要回滚」之类的逻辑时，
 * 默认规则会悄悄吞掉回滚，这类问题排查成本极高。
 */
@Service
public class EmpServiceImpl implements EmpService {

    private final EmpMapper empMapper;
    private final DeptMapper deptMapper;
    private final SalaryStandardMapper salaryStandardMapper;

    /**
     * 允许排序的字段白名单。
     *
     * <p><b>为什么必须是白名单。</b>排序列无法用 {@code ?} 占位符，只能拼进 SQL，
     * 直接拼用户传入的字段名就是标准注入点（{@code sortBy=empName;drop table emp--}）。
     * 白名单把「用户可影响的 SQL 片段」限制成有限的几个常量，越界的键直接回落默认列。
     *
     * <p>用静态块 + {@code HashMap} 而不是 {@code Map.of}：后者是 JDK 9 API，
     * 会让 {@code mvn -Pjdk8-lint} 复核失败，而本工程刻意保持源码在 JDK 8 可编译
     * （升级到 17 属运行时决策，不引入语法依赖）。
     */
    private static final Map<String, SFunction<Emp, ?>> SORTABLE_COLUMNS = new HashMap<>(8);

    static {
        SORTABLE_COLUMNS.put("empId", Emp::getEmpId);
        SORTABLE_COLUMNS.put("empName", Emp::getEmpName);
        SORTABLE_COLUMNS.put("salary", Emp::getSalary);
        SORTABLE_COLUMNS.put("hireDate", Emp::getHireDate);
    }

    /** 构造器注入：单一构造器时无需 @Autowired，也便于单元测试直接 new。 */
    public EmpServiceImpl(EmpMapper empMapper, DeptMapper deptMapper,
                          SalaryStandardMapper salaryStandardMapper) {
        this.empMapper = empMapper;
        this.deptMapper = deptMapper;
        this.salaryStandardMapper = salaryStandardMapper;
    }

    @Override
    public Page<Emp> pageQuery(EmpQueryDTO query) {
        EmpQueryDTO condition = (query == null) ? new EmpQueryDTO() : query;

        LambdaQueryWrapper<Emp> wrapper = new LambdaQueryWrapper<>();
        // 空白字符串等于未传：少了 hasText 这层判断，传 "   " 会拼出 LIKE '%   %'，
        // 静默返回 0 行，排查时极难定位。
        wrapper.like(StringUtils.hasText(condition.getName()), Emp::getEmpName, condition.getName())
                .eq(StringUtils.hasText(condition.getGender()), Emp::getGender, condition.getGender())
                .eq(StringUtils.hasText(condition.getDept()), Emp::getDept, condition.getDept());

        // 排序：白名单外的字段回落 empId，方向缺省为降序，
        // 保证「不传参数」与升级前的行为完全一致。
        SFunction<Emp, ?> sortColumn = SORTABLE_COLUMNS.get(condition.getSortBy());
        if (sortColumn == null) {
            sortColumn = Emp::getEmpId;
        }
        wrapper.orderBy(true, condition.isAsc(), sortColumn);

        Page<Emp> page = new Page<>(condition.toPageNo(), condition.toPageSize());
        // 逻辑删除由 MyBatis-Plus 自动追加 deleted = 0，这里不必手写
        return empMapper.selectPage(page, wrapper);
    }

    @Override
    public Emp getById(Integer empId) {
        requireId(empId);
        Emp emp = empMapper.selectById(empId);
        if (emp == null) {
            throw new BusinessException("员工不存在：" + empId);
        }
        return emp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(Emp emp) {
        if (emp == null) {
            throw new BusinessException("员工信息不能为空");
        }
        // 新增时 ID 由数据库自增，前端传入的 empId 一律忽略，避免覆盖既有记录
        emp.setEmpId(null);
        requireDeptExists(emp.getDept());
        boolean inserted = empMapper.insert(emp) > 0;
        if (inserted) {
            // 初始薪资标准：生效日取入职日，保证之后补生成任意月份都能查到基准
            writeStandard(emp.getEmpId(), emp.getSalary(),
                    emp.getHireDate() == null ? LocalDate.now() : emp.getHireDate());
        }
        return inserted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Emp emp) {
        if (emp == null) {
            throw new BusinessException("员工信息不能为空");
        }
        requireId(emp.getEmpId());
        requireDeptExists(emp.getDept());
        Emp existing = empMapper.selectById(emp.getEmpId());
        if (existing == null) {
            throw new BusinessException("员工不存在或已删除：" + emp.getEmpId());
        }
        int rows = empMapper.updateById(emp);
        if (rows == 0) {
            // 影响 0 行只有两种可能：ID 不存在，或该记录已被逻辑删除
            throw new BusinessException("员工不存在或已删除：" + emp.getEmpId());
        }
        upsertSalaryStandard(existing, emp);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Integer empId) {
        requireId(empId);
        int rows = empMapper.deleteById(empId);
        if (rows == 0) {
            throw new BusinessException("员工不存在或已删除：" + empId);
        }
        return true;
    }

    /**
     * 员工改薪时补一条薪资标准，生效日取**当月 1 日**。
     *
     * <p>为什么不是「今天」：薪资按月核算，月中改薪若按天切分，需要按日折算，
     * 那套规则本工程没有实现；取当月 1 日意味着「本月起按新标准」，
     * 语义简单且不会在月内产生两套基准。
     *
     * <p>同月重复调薪时先删后插（唯一键是 {@code emp_id + effective_from}），
     * 避免一次调薪留下两条同日标准。
     */
    private void upsertSalaryStandard(Emp existing, Emp incoming) {
        if (incoming.getSalary() == null || existing.getSalary() == null
                || incoming.getSalary().compareTo(existing.getSalary()) == 0) {
            return;
        }
        LocalDate effective = LocalDate.now().withDayOfMonth(1);
        salaryStandardMapper.delete(new LambdaQueryWrapper<SalaryStandard>()
                .eq(SalaryStandard::getEmpId, existing.getEmpId())
                .eq(SalaryStandard::getEffectiveFrom, effective));
        writeStandard(existing.getEmpId(), incoming.getSalary(), effective);
    }

    private void writeStandard(Integer empId, java.math.BigDecimal base, LocalDate effectiveFrom) {
        if (empId == null || base == null) {
            return;
        }
        SalaryStandard std = new SalaryStandard();
        std.setEmpId(empId);
        std.setBase(base);
        std.setEffectiveFrom(effectiveFrom);
        salaryStandardMapper.insert(std);
    }

    /**
     * 校验部门名称在 {@code dept} 表中确实存在。
     *
     * <p>这是两个模块之间「写方向」唯一的一致性约束。反方向（部门改名时级联刷新
     * {@code emp.dept}）已经做了；若这里不校验，就形成单向一致：部门改了员工跟着变，
     * 员工却能挂到一个部门表里根本没有的名称上。那条记录永远不会出现在
     * {@code GET /depts/{id}/employees} 的结果里，按部门做的统计也会漏掉它。
     *
     * <p>dept 为空时不校验：允许暂时不指定部门（表上该列也允许为空字符串）。
     */
    private void requireDeptExists(String dept) {
        if (!StringUtils.hasText(dept)) {
            return;
        }
        Dept existing = deptMapper.selectOne(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getDeptName, dept));
        if (existing == null) {
            throw new BusinessException("部门不存在：" + dept + "，请先在部门管理中创建");
        }
        // 停用部门不允许再接收新员工：否则「停用」只是个装饰字段，
        // 列表里还挂着人，报表按部门汇总时也会把已撤销的部门算进去。
        if (existing.getStatus() != null && existing.getStatus() != 1) {
            throw new BusinessException("部门已停用，不能分配员工：" + dept);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeBatch(List<Integer> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            throw new BusinessException("批量删除的 ID 列表不能为空");
        }
        return empMapper.deleteBatchIds(empIds);
    }

    /** 参数校验下沉到 Service：Controller 只负责接收，任何入口调用都能被拦住。 */
    private void requireId(Integer empId) {
        if (empId == null) {
            throw new BusinessException("员工ID不能为空");
        }
    }
}
