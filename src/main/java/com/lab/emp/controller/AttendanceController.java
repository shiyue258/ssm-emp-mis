package com.lab.emp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.RequirePermission;
import com.lab.emp.common.Result;
import com.lab.emp.dto.AttendanceSummaryVO;
import com.lab.emp.entity.Attendance;
import com.lab.emp.service.AttendanceService;
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
 * 考勤接口。
 *
 * <p>{@code /attendance/summary} 与 {@code /attendance/{id}} 形状相似，但前者是字面量路径。
 * Spring 的路径匹配对字面量优先于路径变量，所以 {@code GET /attendance/summary} 不会被
 * 当成「查询 id 为 summary 的记录」而报类型转换错误。
 */
@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @RequirePermission("att:read")
    @GetMapping
    public Result<Page<Attendance>> page(@RequestParam(required = false) Integer empId,
                                         @RequestParam(required = false) String month,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return Result.ok(attendanceService.page(empId, month, page, size));
    }

    @RequirePermission("att:read")
    @GetMapping("/summary")
    public Result<List<AttendanceSummaryVO>> summary(@RequestParam String month) {
        return Result.ok(attendanceService.summary(month));
    }

    @RequirePermission("att:read")
    @GetMapping("/{id}")
    public Result<Attendance> get(@PathVariable("id") Integer id) {
        return Result.ok(attendanceService.getById(id));
    }

    @RequirePermission("att:write")
    @PostMapping
    public Result<Void> add(@RequestBody @Valid Attendance attendance) {
        attendanceService.add(attendance);
        return Result.ok();
    }

    @RequirePermission("att:write")
    @PutMapping
    public Result<Void> update(@RequestBody @Valid Attendance attendance) {
        if (attendance.getAttId() == null) {
            return Result.fail("考勤记录 ID 不能为空");
        }
        attendanceService.update(attendance);
        return Result.ok();
    }

    @RequirePermission("att:write")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Integer id) {
        attendanceService.remove(id);
        return Result.ok();
    }
}
