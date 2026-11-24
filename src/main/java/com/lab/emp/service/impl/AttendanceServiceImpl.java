package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.BusinessException;
import com.lab.emp.dto.AttendanceSummaryVO;
import com.lab.emp.entity.Attendance;
import com.lab.emp.entity.Emp;
import com.lab.emp.mapper.AttendanceMapper;
import com.lab.emp.mapper.EmpMapper;
import com.lab.emp.service.AttendanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考勤业务实现。
 *
 * <p>汇总接口刻意**只用两条查询**：一条按月取全部明细，一条按 ID 批量取员工姓名。
 * 若写成「先查明细，再为每条记录查一次员工」，12 条记录就是 13 条 SQL，
 * 典型的 N+1。这里的写法可以推广到任何「列表 + 关联名称」的场景。
 *
 * <p>月份校验统一收敛在 {@link #parseMonth(String)}：非法格式抛业务异常而不是
 * 让 {@code YearMonth.parse} 的 DateTimeParseException 冒到兜底分支变成「系统繁忙」。
 */
@Service
public class AttendanceServiceImpl implements AttendanceService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    /** 状态取值，与表上的 CHECK 约束一一对应。 */
    private static final int NORMAL = 0;
    private static final int LATE = 1;
    private static final int EARLY = 2;
    private static final int ABSENT = 3;

    /** 标准上下班时间，用于推导迟到/早退；真实系统应放到排班表里，本工程固定为 9:00–18:00。 */
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);

    private final AttendanceMapper attendanceMapper;
    private final EmpMapper empMapper;

    public AttendanceServiceImpl(AttendanceMapper attendanceMapper, EmpMapper empMapper) {
        this.attendanceMapper = attendanceMapper;
        this.empMapper = empMapper;
    }

    @Override
    public Page<Attendance> page(Integer empId, String month, int page, int size) {
        int pageNo = page < 1 ? 1 : page;
        int pageSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(empId != null, Attendance::getEmpId, empId);
        if (StringUtils.hasText(month)) {
            YearMonth ym = parseMonth(month);
            wrapper.ge(Attendance::getWorkDate, ym.atDay(1))
                    .lt(Attendance::getWorkDate, ym.plusMonths(1).atDay(1));
        }
        wrapper.orderByDesc(Attendance::getWorkDate).orderByAsc(Attendance::getEmpId);
        Page<Attendance> result = attendanceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        fillEmpNames(result.getRecords());
        return result;
    }

    /**
     * 给列表补上员工姓名：一次 IN 查询取回本页涉及的所有员工，避免逐条查询。
     *
     * <p>这样做并非「为了让前端省事」，而是因为关联展示属于服务端职责：
     * 交给调用方去拼，N+1 只是从服务端挪到了客户端。
     */
    private void fillEmpNames(List<Attendance> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Integer> empIds = records.stream().map(Attendance::getEmpId).collect(Collectors.toSet());
        Map<Integer, String> names = empMapper.selectList(new LambdaQueryWrapper<Emp>()
                        .in(Emp::getEmpId, empIds))
                .stream().collect(Collectors.toMap(Emp::getEmpId, Emp::getEmpName));
        for (Attendance record : records) {
            record.setEmpName(names.getOrDefault(record.getEmpId(), "已删除员工"));
        }
    }

    @Override
    public Attendance getById(Integer attId) {
        Attendance attendance = attendanceMapper.selectById(attId);
        if (attendance == null) {
            throw new BusinessException("考勤记录不存在：" + attId);
        }
        return attendance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Attendance attendance) {
        validate(attendance);
        ensureNotDuplicated(attendance.getEmpId(), attendance.getWorkDate(), null);
        attendanceMapper.insert(attendance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Attendance attendance) {
        getById(attendance.getAttId());
        validate(attendance);
        ensureNotDuplicated(attendance.getEmpId(), attendance.getWorkDate(), attendance.getAttId());
        attendanceMapper.updateById(attendance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Integer attId) {
        getById(attId);
        attendanceMapper.deleteById(attId);
    }

    @Override
    public List<AttendanceSummaryVO> summary(String month) {
        YearMonth ym = parseMonth(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        // 第一条查询：该月全部明细。
        // 显式按 emp_id 排序，不只是为了好看——汇总结果用 LinkedHashMap 按「首次出现顺序」
        // 组织，明细无序会让同一个月的两次调用返回不同的人员顺序，前端表格会莫名跳动。
        List<Attendance> records = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .ge(Attendance::getWorkDate, start)
                .lt(Attendance::getWorkDate, end)
                .orderByAsc(Attendance::getEmpId)
                .orderByAsc(Attendance::getWorkDate));

        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        // 按员工聚合
        Map<Integer, int[]> counters = new LinkedHashMap<>();
        for (Attendance record : records) {
            int[] c = counters.computeIfAbsent(record.getEmpId(), k -> new int[5]);
            c[0]++;                                     // totalDays
            switch (record.getStatus()) {
                case NORMAL: c[1]++; break;
                case LATE:   c[2]++; break;
                case EARLY:  c[3]++; break;
                case ABSENT: c[4]++; break;
                default: break;
            }
        }

        // 第二条查询：一次 IN 取回所有涉及的员工姓名，避免 N+1
        Set<Integer> empIds = counters.keySet();
        Map<Integer, String> names = new HashMap<>(empIds.size() * 2);
        List<Emp> emps = empMapper.selectList(new LambdaQueryWrapper<Emp>()
                .in(Emp::getEmpId, empIds));
        for (Emp emp : emps) {
            names.put(emp.getEmpId(), emp.getEmpName());
        }

        List<AttendanceSummaryVO> result = new ArrayList<>(counters.size());
        for (Map.Entry<Integer, int[]> entry : counters.entrySet()) {
            int[] c = entry.getValue();
            AttendanceSummaryVO vo = new AttendanceSummaryVO();
            vo.setEmpId(entry.getKey());
            vo.setEmpName(names.getOrDefault(entry.getKey(), "已删除员工"));
            vo.setTotalDays(c[0]);
            vo.setNormalDays(c[1]);
            vo.setLateDays(c[2]);
            vo.setEarlyDays(c[3]);
            vo.setAbsentDays(c[4]);
            double present = c[0] - c[4];
            vo.setAttendanceRate(c[0] == 0 ? 0.0 : Math.round(present / c[0] * 10000.0) / 100.0);
            result.add(vo);
        }
        return result;
    }

    /**
     * 业务层校验，并在校验过程中**推导考勤状态**（不接受客户端传入）。
     *
     * <p>为什么状态要推导：它是对「打卡时间」这一事实的判定结论。若允许手选，
     * 就会出现「09:35 打卡却标成正常」这类自相矛盾的数据，而月度出勤率、
     * 薪资扣款都建立在这个字段上——一处手误，两处报表都错。
     *
     * <p>推导规则（{@code deriveStatus}）：两次打卡都为空 → 缺勤；上班晚于 09:00 →
     * 迟到；下班早于 18:00 → 早退；其余 → 正常。同时成立时按迟到记，
     * 因为迟到意味着未按时到岗，是更主要的问题。
     *
     * <p>其余两条校验：
     * <ul>
     *   <li>员工必须存在——表上没有外键，不校验就能给不存在的 empId 登记考勤；</li>
     *   <li>上下班要么都填、要么都不填，且下班不得早于上班——后者数据库有 CHECK 兜底，
     *       但 CHECK 抛出的异常会变成「系统繁忙」，用户看不出是自己填反了。</li>
     * </ul>
     */
    private void validate(Attendance attendance) {
        if (empMapper.selectById(attendance.getEmpId()) == null) {
            throw new BusinessException("员工不存在：" + attendance.getEmpId());
        }
        boolean hasIn = attendance.getCheckIn() != null;
        boolean hasOut = attendance.getCheckOut() != null;
        if (hasIn != hasOut) {
            throw new BusinessException("上班与下班打卡时间必须同时填写，或同时留空");
        }
        if (hasIn && attendance.getCheckOut().isBefore(attendance.getCheckIn())) {
            throw new BusinessException("下班打卡时间不能早于上班打卡时间");
        }
        attendance.setStatus(deriveStatus(attendance.getCheckIn(), attendance.getCheckOut()));
    }

    /** 由打卡时间推导状态；两个时间都为空表示缺勤。 */
    private int deriveStatus(LocalTime checkIn, LocalTime checkOut) {
        if (checkIn == null && checkOut == null) {
            return ABSENT;
        }
        if (checkIn != null && checkIn.isAfter(WORK_START)) {
            return LATE;
        }
        if (checkOut != null && checkOut.isBefore(WORK_END)) {
            return EARLY;
        }
        return NORMAL;
    }

    /** 同一员工同一天只能有一条记录，改记录时排除自身。 */
    private void ensureNotDuplicated(Integer empId, LocalDate workDate, Integer excludeId) {
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getEmpId, empId).eq(Attendance::getWorkDate, workDate);
        if (excludeId != null) {
            wrapper.ne(Attendance::getAttId, excludeId);
        }
        Long count = attendanceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("该员工在 " + workDate + " 已有考勤记录");
        }
    }

    /** 解析 YYYY-MM；非法格式转成业务异常，避免落到兜底分支变成「系统繁忙」。 */
    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            throw new BusinessException("月份不能为空，格式 YYYY-MM");
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式不正确，应为 YYYY-MM：" + month);
        }
    }
}
