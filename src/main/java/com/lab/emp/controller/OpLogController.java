package com.lab.emp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.RequirePermission;
import com.lab.emp.common.Result;
import com.lab.emp.entity.OpLog;
import com.lab.emp.service.OpLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计查询接口。
 *
 * <p>只提供读，不提供写——审计记录由切面自动落库，没有任何接口能让业务方
 * 伪造或删除一条审计。这条约束比「有权限控制」更彻底：入口根本没有。
 */
@RestController
@RequestMapping("/op-logs")
public class OpLogController {

    private final OpLogService opLogService;

    public OpLogController(OpLogService opLogService) {
        this.opLogService = opLogService;
    }

    @RequirePermission("log:read")
    @GetMapping
    public Result<Page<OpLog>> page(@RequestParam(required = false) String operator,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        return Result.ok(opLogService.page(operator, page, size));
    }
}
