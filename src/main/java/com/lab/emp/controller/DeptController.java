package com.lab.emp.controller;

import com.lab.emp.common.Result;
import com.lab.emp.entity.Dept;
import com.lab.emp.entity.Emp;
import com.lab.emp.service.DeptService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 部门接口。
 *
 * <p>风格与 {@code EmpController} 保持一致：只做协议适配，不写业务判断；
 * 校验靠实体上的 JSR-303 注解 + {@code @Valid}，失败由全局异常处理器统一返回。
 *
 * <p>路径变量 {@code {id}} 在 {@code GET /depts/{id}/employees} 里指的是**部门 ID**，
 * 不是员工 ID——两个资源共用一层路径，命名上容易混淆，故在此写明。
 */
@RestController
@RequestMapping("/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    /** 列表，可选按名称模糊过滤。 */
    @GetMapping
    public Result<List<Dept>> list(@RequestParam(required = false) String name) {
        return Result.ok(deptService.listAll(name));
    }

    /** 单条查询。 */
    @GetMapping("/{id}")
    public Result<Dept> get(@PathVariable("id") Integer id) {
        return Result.ok(deptService.getById(id));
    }

    /** 新增。 */
    @PostMapping
    public Result<Void> add(@RequestBody @Valid Dept dept) {
        deptService.add(dept);
        return Result.ok();
    }

    /** 修改。名称变更时会级联刷新员工所属部门。 */
    @PutMapping
    public Result<Void> update(@RequestBody @Valid Dept dept) {
        if (dept.getDeptId() == null) {
            return Result.fail("部门 ID 不能为空");
        }
        deptService.update(dept);
        return Result.ok();
    }

    /** 删除。部门下仍有员工时返回业务失败。 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Integer id) {
        deptService.remove(id);
        return Result.ok();
    }

    /** 该部门下的员工列表。 */
    @GetMapping("/{id}/employees")
    public Result<List<Emp>> employees(@PathVariable("id") Integer id) {
        return Result.ok(deptService.listEmployees(id));
    }
}
