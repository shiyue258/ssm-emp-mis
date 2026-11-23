package com.lab.emp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.emp.entity.Dept;

/**
 * 部门持久层。
 *
 * <p>与 {@code EmpMapper} 一样只用 {@code BaseMapper} 的能力：按名称查重、按状态过滤
 * 都可以用条件构造器表达，无需 XML。
 */
public interface DeptMapper extends BaseMapper<Dept> {
}
