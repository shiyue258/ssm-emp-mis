package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lab.emp.common.BusinessException;
import com.lab.emp.entity.Dept;
import com.lab.emp.entity.Emp;
import com.lab.emp.mapper.DeptMapper;
import com.lab.emp.mapper.EmpMapper;
import com.lab.emp.service.DeptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 部门业务实现。
 *
 * <p>本类有两个地方体现了「部门」与「员工」两个模块并非彼此孤立：
 * <ol>
 *   <li><b>删除前检查</b>：部门下还有员工时拒绝删除，否则那些员工的 {@code dept}
 *       会指向一个不存在的部门，列表筛选将永远查不到它们；</li>
 *   <li><b>改名时级联</b>：{@code emp.dept} 存的是名称字符串，部门一改名，
 *       若不刷新区内数据，关联立刻断裂。这是「用名称做关联」这个取舍必须付的代价。</li>
 * </ol>
 * 第 2 条是整个模块里唯一必须放进事务的地方——改部门名与刷员工部门要么都成功，
 * 要么都不做。
 */
@Service
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;
    private final EmpMapper empMapper;

    public DeptServiceImpl(DeptMapper deptMapper, EmpMapper empMapper) {
        this.deptMapper = deptMapper;
        this.empMapper = empMapper;
    }

    @Override
    public List<Dept> listAll(String name) {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Dept::getDeptName, name)
                .orderByAsc(Dept::getDeptId);
        return deptMapper.selectList(wrapper);
    }

    @Override
    public Dept getById(Integer deptId) {
        Dept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException("部门不存在：" + deptId);
        }
        return dept;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Dept dept) {
        ensureNameAvailable(dept.getDeptName(), null);
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        deptMapper.insert(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Dept dept) {
        Dept existing = getById(dept.getDeptId());
        ensureNameAvailable(dept.getDeptName(), dept.getDeptId());

        deptMapper.updateById(dept);

        // 名称变了就把员工的 redundancy 字段一起改掉，否则关联断裂。
        // 这条 UPDATE 与上面的 updateById 必须在同一个事务里：只改一半，
        // 员工的 dept 就会指向一个已不存在的名称。
        if (StringUtils.hasText(dept.getDeptName())
                && !dept.getDeptName().equals(existing.getDeptName())) {
            empMapper.update(null, new LambdaUpdateWrapper<Emp>()
                    .eq(Emp::getDept, existing.getDeptName())
                    .set(Emp::getDept, dept.getDeptName()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Integer deptId) {
        Dept dept = getById(deptId);
        long employees = empMapper.selectCount(new LambdaQueryWrapper<Emp>()
                .eq(Emp::getDept, dept.getDeptName()));
        if (employees > 0) {
            throw new BusinessException("该部门下仍有 " + employees + " 名员工，无法删除");
        }
        deptMapper.deleteById(deptId);
    }

    @Override
    public List<Emp> listEmployees(Integer deptId) {
        Dept dept = getById(deptId);
        return empMapper.selectList(new LambdaQueryWrapper<Emp>()
                .eq(Emp::getDept, dept.getDeptName())
                .orderByAsc(Emp::getEmpId));
    }

    /**
     * 名称查重。
     *
     * <p>这条预检查挡的是常规重复提交；数据库上的唯一键才是最终防线——
     * 两个请求同时通过预检查时，只有唯一键能拦住第二个。此时抛出的
     * {@code DuplicateKeyException} 会落到全局异常处理器兜底，返回「系统繁忙」，
     * 语义上不理想，但并发重复提交在本场景属于极端情况，暂不为此增加分支。
     *
     * @param excludeDeptId 修改时排除自身，新增时传 null
     */
    private void ensureNameAvailable(String deptName, Integer excludeDeptId) {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Dept::getDeptName, deptName);
        if (excludeDeptId != null) {
            wrapper.ne(Dept::getDeptId, excludeDeptId);
        }
        if (deptMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("部门名称已存在：" + deptName);
        }
    }
}
