package com.lab.emp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.Result;
import com.lab.emp.dto.SalaryGenerateResult;
import com.lab.emp.entity.SalaryRecord;
import com.lab.emp.service.SalaryService;
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

/**
 * 薪资接口。
 *
 * <p>状态流转只有一条单向路径：待发放（0）→ 已发放（1），由 {@code PUT /salaries/{id}/pay}
 * 触发。没有「撤销发放」的接口——钱一旦出去，系统层面就不该能一键撤回。
 */
@RestController
@RequestMapping("/salaries")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping
    public Result<Page<SalaryRecord>> page(@RequestParam(required = false) Integer empId,
                                           @RequestParam(required = false) String month,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(salaryService.page(empId, month, page, size));
    }

    @GetMapping("/{id}")
    public Result<SalaryRecord> get(@PathVariable("id") Integer id) {
        return Result.ok(salaryService.getById(id));
    }

    @PostMapping("/generate")
    public Result<SalaryGenerateResult> generate(@RequestParam String month) {
        return Result.ok(salaryService.generate(month));
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Valid SalaryRecord record) {
        if (record.getId() == null) {
            return Result.fail("薪资记录 ID 不能为空");
        }
        salaryService.update(record);
        return Result.ok();
    }

    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable("id") Integer id) {
        salaryService.pay(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Integer id) {
        salaryService.remove(id);
        return Result.ok();
    }
}
