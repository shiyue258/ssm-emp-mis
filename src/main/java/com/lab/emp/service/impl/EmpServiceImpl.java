package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.BusinessException;
import com.lab.emp.dto.EmpQueryDTO;
import com.lab.emp.entity.Emp;
import com.lab.emp.mapper.EmpMapper;
import com.lab.emp.service.EmpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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

    /** 构造器注入：单一构造器时无需 @Autowired，也便于单元测试直接 new。 */
    public EmpServiceImpl(EmpMapper empMapper) {
        this.empMapper = empMapper;
    }

    @Override
    public Page<Emp> pageQuery(EmpQueryDTO query) {
        EmpQueryDTO condition = (query == null) ? new EmpQueryDTO() : query;

        LambdaQueryWrapper<Emp> wrapper = new LambdaQueryWrapper<>();
        // 空白字符串等于未传：少了 hasText 这层判断，传 "   " 会拼出 LIKE '%   %'，
        // 静默返回 0 行，排查时极难定位。
        wrapper.like(StringUtils.hasText(condition.getName()), Emp::getEmpName, condition.getName())
                .eq(StringUtils.hasText(condition.getGender()), Emp::getGender, condition.getGender())
                .eq(StringUtils.hasText(condition.getDept()), Emp::getDept, condition.getDept())
                .orderByDesc(Emp::getEmpId);

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
        return empMapper.insert(emp) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Emp emp) {
        if (emp == null) {
            throw new BusinessException("员工信息不能为空");
        }
        requireId(emp.getEmpId());
        int rows = empMapper.updateById(emp);
        if (rows == 0) {
            // 影响 0 行只有两种可能：ID 不存在，或该记录已被逻辑删除
            throw new BusinessException("员工不存在或已删除：" + emp.getEmpId());
        }
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
