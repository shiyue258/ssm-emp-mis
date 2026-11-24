package com.lab.emp.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 薪资计算规则。
 *
 * <p>集中在一个类里，是为了让「扣多少钱」这件事只有一处定义。散落在 Service 里的
 * 数字（21.75、20、0.5）过两个月没人说得清依据，改一次要全文搜索。
 *
 * <p>规则本身刻意保持简单，但每条都有出处可讲：
 * <ul>
 *   <li><b>月计薪天数 21.75</b>：法定标准（(365−104)/12），用于把月薪折算成日薪；</li>
 *   <li><b>缺勤扣整日工资</b>：缺勤当天没有提供劳动，按日薪全额扣；</li>
 *   <li><b>迟到 / 早退每次扣 20 元</b>：固定金额而非按分钟折算——本系统没有精确到
 *       分钟的考核依据，按比例扣会算出「迟到 3 分钟扣 8.6 元」这种经不起追问的数字。</li>
 * </ul>
 *
 * <p>需要说明的是：<b>这不等于真实企业的薪酬规则</b>。真实场景还涉及社保公积金、
 * 个税累进、加班费折算、绩效系数，本系统一概没有。这里实现的是「考勤影响薪资」
 * 这条链路本身，而非一套可用的薪酬方案。
 */
public final class SalaryRule {

    /** 月计薪天数（法定标准）。 */
    public static final BigDecimal MONTHLY_WORK_DAYS = new BigDecimal("21.75");

    /** 迟到一次扣款。 */
    public static final BigDecimal LATE_PENALTY = new BigDecimal("20.00");

    /** 早退一次扣款。 */
    public static final BigDecimal EARLY_PENALTY = new BigDecimal("20.00");

    private SalaryRule() {
    }

    /** 日薪 = 月基本工资 / 21.75，保留两位。 */
    public static BigDecimal dailyWage(BigDecimal base) {
        if (base == null) {
            return BigDecimal.ZERO;
        }
        return base.divide(MONTHLY_WORK_DAYS, 2, RoundingMode.HALF_UP);
    }

    /**
     * 按考勤计算扣款总额。
     *
     * @param base         月基本工资
     * @param absentDays   缺勤天数
     * @param lateCount    迟到次数
     * @param earlyCount   早退次数
     */
    public static BigDecimal deductionOf(BigDecimal base, int absentDays, int lateCount, int earlyCount) {
        BigDecimal absent = dailyWage(base).multiply(BigDecimal.valueOf(absentDays));
        BigDecimal late = LATE_PENALTY.multiply(BigDecimal.valueOf(lateCount));
        BigDecimal early = EARLY_PENALTY.multiply(BigDecimal.valueOf(earlyCount));
        return absent.add(late).add(early).setScale(2, RoundingMode.HALF_UP);
    }
}
