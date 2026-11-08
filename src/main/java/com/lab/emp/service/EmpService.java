package com.lab.emp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.dto.EmpQueryDTO;
import com.lab.emp.entity.Emp;

import java.util.List;

/**
 * 员工业务接口。
 *
 * <p>接口只暴露业务语义，不暴露持久化细节：
 * <ul>
 *   <li>入参用 {@link EmpQueryDTO} 而不是零散的 page/size/name，
 *       避免以后每加一个查询条件就改一次方法签名；分页参数的边界收敛
 *       也由 DTO 自己负责（见 {@code toPageNo} / {@code toPageSize}）；</li>
 *   <li>返回值不出现 {@code Result}——那是表现层的封装，业务层不应依赖它，
 *       否则同一套业务逻辑换一个出口（定时任务、消息消费者）就用不了。</li>
 * </ul>
 */
public interface EmpService {

    /**
     * 分页 + 多条件模糊查询。
     *
     * <p>条件为空或纯空白时视为不参与筛选；排序固定按 ID 倒序，保证分页结果稳定。
     *
     * @param query 查询条件，不可为 null
     * @return 分页结果，无匹配时 {@code records} 为空集合而非 null
     */
    Page<Emp> pageQuery(EmpQueryDTO query);

    /**
     * 按 ID 查询单条。
     *
     * @throws com.lab.emp.common.BusinessException ID 为空或员工不存在
     */
    Emp getById(Integer empId);

    /**
     * 新增员工。
     *
     * @return 是否成功
     */
    boolean create(Emp emp);

    /**
     * 按 ID 修改，仅更新非 null 字段。
     *
     * @throws com.lab.emp.common.BusinessException ID 为空，或目标记录不存在 / 已被删除
     */
    boolean update(Emp emp);

    /**
     * 删除单条。因实体标了 {@code @TableLogic}，实际是逻辑删除（deleted = 1）。
     *
     * @throws com.lab.emp.common.BusinessException ID 为空，或目标记录不存在 / 已删除
     */
    boolean remove(Integer empId);

    /**
     * 批量删除，语义同单条删除。
     *
     * @param empIds 待删除的 ID 列表，不可为空集合
     * @return 实际受影响的行数，可能小于列表长度（部分 ID 不存在时）
     * @throws com.lab.emp.common.BusinessException 列表为空
     */
    int removeBatch(List<Integer> empIds);
}
