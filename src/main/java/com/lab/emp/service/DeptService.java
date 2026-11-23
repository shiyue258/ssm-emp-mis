package com.lab.emp.service;

import com.lab.emp.entity.Dept;
import com.lab.emp.entity.Emp;

import java.util.List;

/**
 * 部门业务接口。
 *
 * <p>返回 {@code List} 而不是分页对象：部门是低频变更的基础数据，量级在两位数，
 * 分页对使用者是负担而非帮助。这一点与员工列表刻意不同——那里数据会增长到需要分页。
 */
public interface DeptService {

    /**
     * 查询部门列表。
     *
     * @param name 名称关键字，前后模糊；为空表示不过滤
     */
    List<Dept> listAll(String name);

    /** 按 ID 查询，不存在时抛业务异常。 */
    Dept getById(Integer deptId);

    /** 新增部门，名称重复时拒绝。 */
    void add(Dept dept);

    /** 修改部门，名称与其他部门冲突时拒绝。 */
    void update(Dept dept);

    /**
     * 删除部门。
     *
     * @throws com.lab.emp.common.BusinessException 部门不存在，或该部门下仍有员工
     */
    void remove(Integer deptId);

    /** 查询该部门下的员工（按部门名称匹配 emp.dept）。 */
    List<Emp> listEmployees(Integer deptId);
}
