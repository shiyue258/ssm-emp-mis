package com.lab.emp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.dto.AttendanceSummaryVO;
import com.lab.emp.entity.Attendance;

import java.util.List;

/** 考勤业务接口。 */
public interface AttendanceService {

    /**
     * 分页查询。
     *
     * @param empId 员工 ID，为空表示不限
     * @param month 月份 YYYY-MM，为空表示不限
     */
    Page<Attendance> page(Integer empId, String month, int page, int size);

    Attendance getById(Integer attId);

    /** 登记考勤；同一员工同一天重复登记会被拒绝。 */
    void add(Attendance attendance);

    /** 修改考勤；改动员工或日期时同样要查重。 */
    void update(Attendance attendance);

    void remove(Integer attId);

    /** 某月的按人汇总。 */
    List<AttendanceSummaryVO> summary(String month);
}
