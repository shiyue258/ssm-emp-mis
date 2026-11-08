package com.lab.emp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.Result;
import com.lab.emp.dto.EmpQueryDTO;
import com.lab.emp.entity.Emp;
import com.lab.emp.service.EmpService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 员工管理 RESTful 接口。
 *
 * <p>路径与方法语义遵循资源风格：
 * <ul>
 *   <li>{@code POST /emps} 新增 —— 由服务端生成 ID；</li>
 *   <li>{@code PUT /emps} 全量修改 —— ID 放在请求体里，与指导书的接口清单一致；</li>
 *   <li>{@code DELETE /emps/{id}} 删除单条，{@code DELETE /emps} 批量删除 —— 批量删除的 ID 列表放在请求体；</li>
 *   <li>{@code GET /emps/{id}} 单条查询，{@code GET /emps} 列表 + 多条件 + 分页。</li>
 * </ul>
 *
 * <p>本类刻意保持「薄」：只做参数接收与结果封装，校验失败由
 * {@code GlobalExceptionHandler} 统一接管，因此方法签名里没有 {@code BindingResult}。
 * 这样做的好处是每加一个接口不必重复写一遍 {@code if (br.hasErrors())}。
 */
@RestController
@RequestMapping("/emps")
public class EmpController {

    private final EmpService empService;

    public EmpController(EmpService empService) {
        this.empService = empService;
    }

    /** 新增员工。 */
    @PostMapping
    public Result<Void> add(@RequestBody @Valid Emp emp) {
        empService.create(emp);
        return Result.ok();
    }

    /** 修改员工信息。 */
    @PutMapping
    public Result<Void> update(@RequestBody @Valid Emp emp) {
        empService.update(emp);
        return Result.ok();
    }

    /** 删除单条。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        empService.remove(id);
        return Result.ok();
    }

    /** 批量删除，返回实际影响的条数。 */
    @DeleteMapping
    public Result<Integer> deleteBatch(@RequestBody List<Integer> ids) {
        return Result.ok(empService.removeBatch(ids));
    }

    /** 单条查询。 */
    @GetMapping("/{id}")
    public Result<Emp> getById(@PathVariable("id") Integer id) {
        return Result.ok(empService.getById(id));
    }

    /**
     * 列表 + 多条件模糊 + 分页。
     *
     * <p>查询参数直接绑定到 {@link EmpQueryDTO}，不写 {@code @RequestParam} 逐个接：
     * 后者在参数增多后签名会膨胀，且缺省值处理散落在方法体内。
     */
    @GetMapping
    public Result<Page<Emp>> page(EmpQueryDTO query) {
        return Result.ok(empService.pageQuery(query));
    }
}
