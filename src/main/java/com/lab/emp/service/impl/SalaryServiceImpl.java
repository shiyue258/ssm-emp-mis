package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.BusinessException;
import com.lab.emp.common.SalaryRule;
import com.lab.emp.dto.SalaryGenerateResult;
import com.lab.emp.entity.Attendance;
import com.lab.emp.entity.Emp;
import com.lab.emp.entity.SalaryRecord;
import com.lab.emp.mapper.AttendanceMapper;
import com.lab.emp.mapper.EmpMapper;
import com.lab.emp.mapper.SalaryRecordMapper;
import com.lab.emp.service.SalaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 薪资业务实现。
 *
 * <p>三条规则都围绕「钱不能算错、也不能事后说不清」：
 * <ol>
 *   <li><b>实发金额只由服务端算</b>：{@code total = base + bonus - deduction}，
 *       前端传入的 total 一律忽略；</li>
 *   <li><b>已发放不可改、不可删</b>：发放意味着钱已经出门，改数字等于篡改账目；</li>
 *   <li><b>生成幂等</b>：同一月份重复调用只补缺失的员工，不覆盖已调整过的记录。</li>
 * </ol>
 *
 * <p>生成过程同样避开 N+1：先用一次查询取出「该月已有记录的员工 ID 集合」，
 * 再决定为谁插入，而不是逐个员工查一次是否存在。
 */
@Service
public class SalaryServiceImpl implements SalaryService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private static final int PENDING = 0;
    private static final int PAID = 1;

    private final SalaryRecordMapper salaryRecordMapper;
    private final EmpMapper empMapper;
    private final AttendanceMapper attendanceMapper;

    public SalaryServiceImpl(SalaryRecordMapper salaryRecordMapper, EmpMapper empMapper,
                             AttendanceMapper attendanceMapper) {
        this.salaryRecordMapper = salaryRecordMapper;
        this.empMapper = empMapper;
        this.attendanceMapper = attendanceMapper;
    }

    @Override
    public Page<SalaryRecord> page(Integer empId, String month, int page, int size) {
        int pageNo = page < 1 ? 1 : page;
        int pageSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<SalaryRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(empId != null, SalaryRecord::getEmpId, empId)
                .eq(StringUtils.hasText(month), SalaryRecord::getMonth, month)
                .orderByDesc(SalaryRecord::getMonth)
                .orderByAsc(SalaryRecord::getEmpId);
        Page<SalaryRecord> result = salaryRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        fillEmpNames(result.getRecords());
        return result;
    }

    /** 一次 IN 查询补齐本页员工姓名，避免逐条查询把 N+1 推给调用方。 */
    private void fillEmpNames(List<SalaryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Integer> empIds = records.stream().map(SalaryRecord::getEmpId).collect(Collectors.toSet());
        Map<Integer, String> names = empMapper.selectList(new LambdaQueryWrapper<Emp>()
                        .in(Emp::getEmpId, empIds))
                .stream().collect(Collectors.toMap(Emp::getEmpId, Emp::getEmpName));
        for (SalaryRecord record : records) {
            record.setEmpName(names.getOrDefault(record.getEmpId(), "已删除员工"));
        }
    }

    @Override
    public SalaryRecord getById(Integer id) {
        SalaryRecord record = salaryRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("薪资记录不存在：" + id);
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalaryGenerateResult generate(String month) {
        String normalized = normalizeMonth(month);
        YearMonth ym = YearMonth.parse(normalized);

        // 在职员工：deleted = 0 由逻辑删除自动追加，这里只需状态过滤
        List<Emp> emps = empMapper.selectList(new LambdaQueryWrapper<Emp>()
                .eq(Emp::getStatus, 1));
        if (emps.isEmpty()) {
            throw new BusinessException("没有在职员工，无法生成薪资");
        }

        // 一次查询取出该月已有记录的员工，避免逐个查询
        Set<Integer> existing = salaryRecordMapper.selectList(new LambdaQueryWrapper<SalaryRecord>()
                        .eq(SalaryRecord::getMonth, normalized))
                .stream().map(SalaryRecord::getEmpId).collect(Collectors.toSet());

        // 一次查询取出该月考勤，按员工聚合出「缺勤天数 / 迟到次数 / 早退次数」。
        // 这是「考勤 → 薪资」这条业务链的唯一接口点：薪资不再只看基本工资，
        // 而是先根据考勤事实算扣款，再得出实发。
        Map<Integer, int[]> attendanceStats = loadAttendanceStats(ym);

        int generated = 0;
        int skipped = 0;
        BigDecimal deductedTotal = BigDecimal.ZERO;
        for (Emp emp : emps) {
            if (existing.contains(emp.getEmpId())) {
                skipped++;
                continue;
            }
            BigDecimal base = scale(emp.getSalary());
            int[] stats = attendanceStats.getOrDefault(emp.getEmpId(), new int[3]);
            BigDecimal deduction = SalaryRule.deductionOf(base, stats[0], stats[1], stats[2]);
            // 扣款不得超过应发：否则会出现负数工资，表上的 CHECK 也会拒绝
            if (deduction.compareTo(base) > 0) {
                deduction = base;
            }

            SalaryRecord record = new SalaryRecord();
            record.setEmpId(emp.getEmpId());
            record.setMonth(normalized);
            record.setBase(base);
            record.setBonus(BigDecimal.ZERO);
            record.setDeduction(deduction);
            record.setTotal(base.subtract(deduction));
            record.setStatus(PENDING);
            salaryRecordMapper.insert(record);
            generated++;
            deductedTotal = deductedTotal.add(deduction);
        }

        SalaryGenerateResult result = new SalaryGenerateResult();
        result.setMonth(normalized);
        result.setGenerated(generated);
        result.setSkipped(skipped);
        result.setDeductedTotal(deductedTotal);
        return result;
    }

    /**
     * 按员工聚合当月考勤：返回 {缺勤天数, 迟到次数, 早退次数}。
     *
     * <p>只用一条查询取明细再在内存里聚合，而不是「每人查一次考勤」——
     * 10 个员工那样做就是 10 条 SQL。
     */
    private Map<Integer, int[]> loadAttendanceStats(YearMonth ym) {
        List<Attendance> records = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .ge(Attendance::getWorkDate, ym.atDay(1))
                .lt(Attendance::getWorkDate, ym.plusMonths(1).atDay(1)));

        Map<Integer, int[]> stats = new HashMap<>();
        for (Attendance a : records) {
            int[] s = stats.computeIfAbsent(a.getEmpId(), k -> new int[3]);
            if (a.getStatus() == null) {
                continue;
            }
            switch (a.getStatus()) {
                case 3: s[0]++; break;   // 缺勤
                case 1: s[1]++; break;   // 迟到
                case 2: s[2]++; break;   // 早退
                default: break;
            }
        }
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SalaryRecord record) {
        SalaryRecord existing = getById(record.getId());
        if (existing.getStatus() != null && existing.getStatus() == PAID) {
            throw new BusinessException("该月薪资已发放，不能再修改");
        }

        // 三个分量各自先量化到两位小数，再相加。
        // 若直接拿未量化的值算总额，会与落库后的分量对不上：
        // 例如 base=0.005、bonus=0.005 时，库里两列各存 0.01（合计 0.02），
        // 而未量化的总额 (0.005+0.005) 量化后是 0.01——账就差了 1 分钱。
        BigDecimal base = scale(record.getBase());
        BigDecimal bonus = scale(record.getBonus());
        BigDecimal deduction = scale(record.getDeduction());
        BigDecimal total = base.add(bonus).subtract(deduction);
        if (total.signum() < 0) {
            throw new BusinessException("扣款不能大于基本工资与奖金之和");
        }

        SalaryRecord update = new SalaryRecord();
        update.setId(existing.getId());
        update.setBase(base);
        update.setBonus(bonus);
        update.setDeduction(deduction);
        update.setTotal(total);
        salaryRecordMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Integer id) {
        SalaryRecord existing = getById(id);
        if (existing.getStatus() != null && existing.getStatus() == PAID) {
            throw new BusinessException("该月薪资已是已发放状态");
        }
        SalaryRecord update = new SalaryRecord();
        update.setId(id);
        update.setStatus(PAID);
        salaryRecordMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Integer id) {
        SalaryRecord existing = getById(id);
        if (existing.getStatus() != null && existing.getStatus() == PAID) {
            throw new BusinessException("该月薪资已发放，不能删除");
        }
        salaryRecordMapper.deleteById(id);
    }

    /** 金额统一保留两位小数，避免 0E-2 之类的科学计数法写进日志。 */
    private BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeMonth(String month) {
        if (!StringUtils.hasText(month)) {
            throw new BusinessException("月份不能为空，格式 YYYY-MM");
        }
        try {
            return YearMonth.parse(month).toString();
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式不正确，应为 YYYY-MM：" + month);
        }
    }
}
