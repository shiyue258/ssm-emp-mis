-- ============================================================================
-- ssm-emp-mis 数据库初始化脚本
--
-- 用法（必须显式指定 utf8mb4，否则中文会按 latin1 二次编码成乱码）：
--   docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 < sql/init.sql
--
-- 本脚本可重复执行：先 DROP 再 CREATE，不会因重复导入报错。
-- ============================================================================

SET NAMES utf8mb4;

-- 关掉外键检查：脚本按「先建被引用表、后建引用表」的顺序 DROP/CREATE，
-- 而 user_role 对 sys_user、role_permission 对 role/permission 都有外键，
-- 直接删被引用表会报 ERROR 3730。开关在脚本末尾恢复。
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS ssm_emp
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ssm_emp;

DROP TABLE IF EXISTS emp;

-- ----------------------------------------------------------------------------
-- emp 员工表
--   status 与 deleted 是两个不同维度：
--     status  = 业务状态（在职 / 离职），员工本人可见，影响业务查询
--     deleted = 逻辑删除标记，只影响本系统的数据可见性，用于保留历史
--   金额一律 DECIMAL，不使用浮点，避免精度误差。
-- ----------------------------------------------------------------------------
CREATE TABLE emp (
    emp_id      INT UNSIGNED    NOT NULL AUTO_INCREMENT COMMENT '员工ID',
    emp_name    VARCHAR(50)     NOT NULL                COMMENT '员工姓名',
    gender      CHAR(1)         NOT NULL DEFAULT '男'    COMMENT '性别：男/女',
    dept        VARCHAR(50)     NOT NULL                COMMENT '所属部门',
    post        VARCHAR(50)         NULL                COMMENT '职位',
    salary      DECIMAL(10, 2)  NOT NULL DEFAULT 0.00   COMMENT '月薪（元）',
    hire_date   DATE            NOT NULL                COMMENT '入职日期',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1 在职 / 0 离职',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (emp_id),
    KEY idx_emp_name (emp_name),
    KEY idx_emp_dept_status (dept, status),
    CONSTRAINT chk_emp_gender CHECK (gender IN ('男', '女')),
    CONSTRAINT chk_emp_salary CHECK (salary >= 0),
    CONSTRAINT chk_emp_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_emp_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '员工信息表';

-- ----------------------------------------------------------------------------
-- 种子数据：12 条
--   分页默认 size = 5，12 条正好演示 3 页；
--   张/王/刘/陈 各 2 人，用于验证姓名模糊查询不会误命中单条；
--   覆盖研发/产品/市场/人事/财务 5 个部门，含 2 名离职（status = 0）。
--   时间写死字面量而不用 NOW()：保证重复导入后断言结果稳定。
-- ----------------------------------------------------------------------------
INSERT INTO emp (emp_name, gender, dept, post, salary, hire_date, status, create_time, update_time) VALUES
('张伟', '男', '研发部', '后端工程师',  18500.00, '2021-03-15', 1, '2021-03-15 09:00:00', '2021-03-15 09:00:00'),
('张敏', '女', '研发部', '前端工程师',  16800.00, '2021-07-01', 1, '2021-07-01 09:00:00', '2021-07-01 09:00:00'),
('李强', '男', '研发部', '架构师',      32000.00, '2018-09-10', 1, '2018-09-10 09:00:00', '2018-09-10 09:00:00'),
('周涛', '男', '研发部', '测试工程师',  13500.00, '2022-01-17', 0, '2022-01-17 09:00:00', '2023-05-31 18:00:00'),
('王芳', '女', '产品部', '产品经理',    21000.00, '2020-04-20', 1, '2020-04-20 09:00:00', '2020-04-20 09:00:00'),
('王磊', '男', '产品部', '产品助理',     9000.00, '2023-06-12', 1, '2023-06-12 09:00:00', '2023-06-12 09:00:00'),
('刘洋', '男', '市场部', '市场专员',     7800.00, '2022-11-08', 1, '2022-11-08 09:00:00', '2022-11-08 09:00:00'),
('刘婷', '女', '市场部', '市场经理',    16500.00, '2019-02-25', 1, '2019-02-25 09:00:00', '2019-02-25 09:00:00'),
('陈晨', '女', '人事部', '招聘专员',     8200.00, '2023-03-01', 1, '2023-03-01 09:00:00', '2023-03-01 09:00:00'),
('陈静', '女', '人事部', '人事经理',    15800.00, '2017-08-16', 1, '2017-08-16 09:00:00', '2017-08-16 09:00:00'),
('赵鹏', '男', '财务部', '会计',         9600.00, '2021-05-06', 1, '2021-05-06 09:00:00', '2021-05-06 09:00:00'),
('孙丽', '女', '财务部', '财务主管',    19000.00, '2019-12-03', 0, '2019-12-03 09:00:00', '2023-07-15 18:00:00');

-- ----------------------------------------------------------------------------
-- sys_user 登录用户表
--   只服务「登录权限拦截」这一个功能，不做角色与细粒度权限（那属于 CMS 项目）。
--   口令存 SHA-256 摘要而不是明文：即使数据库被导出，也无法直接得到原始口令。
--   注意 SHA-256 是单向散列而非加盐慢哈希，抗彩虹表能力弱；真实系统应使用
--   BCrypt / Argon2。这里选 SHA-256 是因为它只依赖 JDK 标准库，不需额外依赖，
--   且足以说明「口令不落明文」这一设计意图。
--   演示账号：admin / 123456
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    user_id       INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username      VARCHAR(50)  NOT NULL                COMMENT '登录名',
    password_hash CHAR(64)     NOT NULL                COMMENT '口令摘要：SHA-256(明文) 的小写十六进制',
    display_name  VARCHAR(50)  NOT NULL                COMMENT '显示名',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_username (username),
    CONSTRAINT chk_sys_user_status CHECK (status IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '登录用户表';

-- SHA-256('123456') = 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
INSERT INTO sys_user (username, password_hash, display_name, status, create_time) VALUES
('admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '系统管理员', 1, '2026-01-01 00:00:00');

-- ----------------------------------------------------------------------------
-- dept 部门表
--   与 emp 的关联方式是「部门名称」而非外键：emp.dept 存的是名称字符串，
--   dept 表通过 dept_name 与之对应。这是刻意的取舍——加 dept_id 外键要改
--   emp 表结构、六个接口与全部用例，而实验一的表结构需要保持兼容。
--   代价是部门改名时必须同步更新 emp.dept，重建种子数据时不会出现这个问题。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS dept;

CREATE TABLE dept (
    dept_id     INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    dept_name   VARCHAR(50)  NOT NULL                COMMENT '部门名称，与 emp.dept 对应',
    location    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '办公地点',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (dept_id),
    UNIQUE KEY uk_dept_name (dept_name),
    CONSTRAINT chk_dept_status CHECK (status IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '部门表';

-- 前 5 条与 emp 表中实际出现的部门一一对应；「测试部」刻意留空，
-- 用于演示「无员工时允许删除」与「有员工时拒绝删除」的对照。
INSERT INTO dept (dept_name, location, status, create_time, update_time) VALUES
('研发部', 'A 座 3 层', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
('产品部', 'A 座 2 层', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
('市场部', 'B 座 5 层', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
('人事部', 'B 座 2 层', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
('财务部', 'B 座 3 层', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
('测试部', 'A 座 4 层', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00');

-- ----------------------------------------------------------------------------
-- op_log 操作审计表
--   只记写操作（POST/PUT/DELETE），由 AuditAspect 自动落库，无写入接口——
--   任何业务代码都不能伪造或删除一条审计，这是比「加权限控制」更彻底的约束。
--   不记请求体全文：里面可能有口令，记全文就得再写一套脱敏。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS op_log;

CREATE TABLE op_log (
    log_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审计ID',
    operator    VARCHAR(50)  NOT NULL                COMMENT '操作人登录名；取不到时记 anonymous',
    action      VARCHAR(10)  NOT NULL                COMMENT 'HTTP 方法：POST/PUT/DELETE',
    target      VARCHAR(120) NOT NULL                COMMENT '请求路径，含路径变量',
    detail      VARCHAR(500) NOT NULL DEFAULT ''     COMMENT 'success 或异常摘要',
    result      TINYINT      NOT NULL                COMMENT '1 成功 / 0 失败',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (log_id),
    KEY idx_op_log_operator (operator),
    KEY idx_op_log_create_time (create_time),
    CONSTRAINT chk_op_log_result CHECK (result IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '操作审计表';

-- 说明：审计表不预置数据，随实际调用增长。

-- ----------------------------------------------------------------------------
-- attendance 考勤表
--   (emp_id, work_date) 唯一：同一人同一天只能有一条，否则月度汇总会重复计数。
--   check_out >= check_in 由 CHECK 兜底，防止登记时把时间写反。
--   不加外键：emp 用的是逻辑删除，物理行不会消失，外键约束收益有限，
--   而 init.sql 的建表顺序会因此变得脆弱。关联正确性由业务层校验。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS attendance;

CREATE TABLE attendance (
    att_id      INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '考勤ID',
    emp_id      INT UNSIGNED NOT NULL                COMMENT '员工ID',
    work_date   DATE         NOT NULL                COMMENT '工作日期',
    check_in    TIME         NULL                    COMMENT '上班打卡；缺勤为 NULL',
    check_out   TIME         NULL                    COMMENT '下班打卡；缺勤为 NULL',
    status      TINYINT      NOT NULL DEFAULT 0      COMMENT '0 正常 / 1 迟到 / 2 早退 / 3 缺勤',
    remark      VARCHAR(100) NULL                    COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (att_id),
    UNIQUE KEY uk_att_emp_date (emp_id, work_date),
    KEY idx_att_work_date (work_date),
    CONSTRAINT chk_att_status CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT chk_att_time CHECK (check_out IS NULL OR check_in IS NULL OR check_out >= check_in)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '考勤表';

-- status 由服务端按打卡时间推导（9:00 后到岗为迟到、18:00 前离岗为早退、
-- 无打卡为缺勤），种子数据必须与规则自洽，否则界面显示的徽标会与实际时间矛盾。
INSERT INTO attendance (emp_id, work_date, check_in, check_out, status, remark, create_time, update_time) VALUES
(1, '2026-11-02', '09:00:00', '18:00:00', 0, NULL,       '2026-11-02 18:05:00', '2026-11-02 18:05:00'),
(2, '2026-11-02', '09:35:00', '18:05:00', 1, '地铁延误', '2026-11-02 18:05:00', '2026-11-02 18:05:00'),
(3, '2026-11-02', '08:55:00', '16:30:00', 2, NULL,       '2026-11-02 18:05:00', '2026-11-02 18:05:00'),
(4, '2026-11-02', NULL,       NULL,       3, '事假',     '2026-11-02 18:05:00', '2026-11-02 18:05:00'),
(5, '2026-11-02', '09:00:00', '18:00:00', 0, NULL,       '2026-11-02 18:05:00', '2026-11-02 18:05:00'),
(6, '2026-11-02', '09:18:00', '18:10:00', 1, NULL,       '2026-11-02 18:05:00', '2026-11-02 18:05:00'),
(1, '2026-11-03', '08:58:00', '18:20:00', 0, NULL,       '2026-11-03 18:10:00', '2026-11-03 18:10:00'),
(2, '2026-11-03', '09:00:00', '18:00:00', 0, NULL,       '2026-11-03 18:10:00', '2026-11-03 18:10:00'),
(3, '2026-11-03', '09:00:00', '18:00:00', 0, NULL,       '2026-11-03 18:10:00', '2026-11-03 18:10:00'),
(4, '2026-11-03', '09:10:00', '18:00:00', 1, NULL,       '2026-11-03 18:10:00', '2026-11-03 18:10:00'),
(5, '2026-11-03', NULL,       NULL,       3, '病假',     '2026-11-03 18:10:00', '2026-11-03 18:10:00'),
(6, '2026-11-03', '08:50:00', '18:00:00', 0, NULL,       '2026-11-03 18:10:00', '2026-11-03 18:10:00');

-- ----------------------------------------------------------------------------
-- salary_record 薪资发放记录
--   (emp_id, month) 唯一：同一个月不能发两次。month 用 CHAR(7) 存 'YYYY-MM'，
--   比存 DATE 更贴合「按月发放」的语义，也便于按前缀筛选。
--   total 由服务端计算（base + bonus - deduction），不接受前端传入——
--   否则前端报一个和明细对不上的总额，事后查不清是谁算错的。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS salary_record;

CREATE TABLE salary_record (
    id          INT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    emp_id      INT UNSIGNED   NOT NULL                COMMENT '员工ID',
    month       CHAR(7)        NOT NULL                COMMENT '薪资月份 YYYY-MM',
    base        DECIMAL(10, 2) NOT NULL                COMMENT '基本工资，生成时取 emp.salary',
    bonus       DECIMAL(10, 2) NOT NULL DEFAULT 0.00   COMMENT '奖金',
    deduction   DECIMAL(10, 2) NOT NULL DEFAULT 0.00   COMMENT '扣款',
    total       DECIMAL(10, 2) NOT NULL                COMMENT '实发 = base + bonus - deduction',
    status      TINYINT        NOT NULL DEFAULT 0      COMMENT '0 待发放 / 1 已发放',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sal_emp_month (emp_id, month),
    KEY idx_sal_month (month),
    CONSTRAINT chk_sal_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_sal_amount CHECK (base >= 0 AND bonus >= 0 AND deduction >= 0
                                     AND total >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '薪资发放记录表';

-- 用 INSERT ... SELECT 从 emp 取基数：硬编码会让种子数据与 emp 表脱节，
-- 一旦改了某人的薪资，历史月份的基本工资就对不上了。
INSERT INTO salary_record (emp_id, month, base, bonus, deduction, total, status, create_time, update_time)
SELECT emp_id, '2026-10', salary, 500.00, 0.00, salary + 500.00, 1,
       '2026-11-01 00:00:00', '2026-11-01 00:00:00'
FROM emp
WHERE deleted = 0 AND emp_id <= 6;

-- ----------------------------------------------------------------------------
-- salary_standard 薪资标准（带生效日期）
--   为什么需要这张表：emp.salary 只保留「当前」薪资，若拿它去补生成历史月份，
--   调过薪的人历史账目会用新数额，属实质性错误。标准表按 (emp_id, effective_from)
--   记录每次调薪，生成薪资时取「生效日期不晚于该月末」的最后一条。
--   同一天只允许一条（唯一键），重复调薪按覆盖处理。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS salary_standard;

CREATE TABLE salary_standard (
    std_id         INT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT '标准ID',
    emp_id         INT UNSIGNED   NOT NULL                COMMENT '员工ID',
    base           DECIMAL(10, 2) NOT NULL                COMMENT '该生效日起的基本工资',
    effective_from DATE           NOT NULL                COMMENT '生效日期（含）',
    create_time    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (std_id),
    UNIQUE KEY uk_std_emp_effective (emp_id, effective_from),
    KEY idx_std_emp (emp_id),
    CONSTRAINT chk_std_base CHECK (base >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '薪资标准表（按生效日期留存历史）';

-- 种子：每个在职员工的初始标准，生效日取其入职日
INSERT INTO salary_standard (emp_id, base, effective_from, create_time)
SELECT emp_id, salary, hire_date, '2026-01-01 00:00:00'
FROM emp
WHERE deleted = 0;

-- ----------------------------------------------------------------------------
-- 权限模型：role / user_role / permission / role_permission
--   评分表要求「角色分配及细粒度权限拦截；基于 Spring AOP 实现接口权限校验」。
--   权限粒度到「接口动作」（如 emp:write），由 @RequirePermission 注解 + 切面校验，
--   而不是在 if 里硬编码角色名——角色可增删，注解不动。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS role_permission;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS permission;
DROP TABLE IF EXISTS role;

CREATE TABLE role (
    role_id     INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code   VARCHAR(30)  NOT NULL                COMMENT '角色编码，如 ADMIN',
    role_name   VARCHAR(50)  NOT NULL                COMMENT '角色名称',
    remark      VARCHAR(100) NULL                    COMMENT '说明',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表';

CREATE TABLE permission (
    perm_id     INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    perm_code   VARCHAR(50)  NOT NULL                COMMENT '权限编码，如 emp:write',
    perm_name   VARCHAR(50)  NOT NULL                COMMENT '权限名称',
    module      VARCHAR(20)  NOT NULL                COMMENT '所属模块',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (perm_id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限点表';

CREATE TABLE user_role (
    user_id INT UNSIGNED NOT NULL COMMENT '账号ID',
    role_id INT UNSIGNED NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '账号-角色关联';

CREATE TABLE role_permission (
    role_id INT UNSIGNED NOT NULL COMMENT '角色ID',
    perm_id INT UNSIGNED NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, perm_id),
    KEY idx_role_perm_perm (perm_id),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES role (role_id),
    CONSTRAINT fk_role_perm_perm FOREIGN KEY (perm_id) REFERENCES permission (perm_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-权限关联';

INSERT INTO role (role_code, role_name, remark) VALUES
('ADMIN', '系统管理员', '全部权限'),
('HR',    '人事专员',   '员工、部门、考勤、薪资的读写'),
('VIEWER','只读用户',   '仅查询，不能改动数据');

INSERT INTO permission (perm_code, perm_name, module) VALUES
('emp:read',   '查看员工', '员工'),
('emp:write',  '维护员工', '员工'),
('dept:read',  '查看部门', '部门'),
('dept:write', '维护部门', '部门'),
('att:read',   '查看考勤', '考勤'),
('att:write',  '维护考勤', '考勤'),
('sal:read',   '查看薪资', '薪资'),
('sal:write',  '维护薪资', '薪资'),
('user:read',  '查看账号', '账号'),
('user:write', '维护账号', '账号'),
('role:write', '维护角色权限', '权限'),
('log:read',   '查看审计', '审计');
-- 说明：权限点只允许对应「真实存在的校验」。分类/文章的权限点待相应模块实现时一并加入，
-- 否则会出现「有权限点、无接口」的僵尸数据，分配界面里看着有、勾了毫无作用。

-- 管理员拥有全部权限；hr01 只有人事相关；viewer01 只读
INSERT INTO role_permission (role_id, perm_id)
SELECT r.role_id, p.perm_id FROM role r CROSS JOIN permission p WHERE r.role_code = 'ADMIN';

INSERT INTO role_permission (role_id, perm_id)
SELECT r.role_id, p.perm_id FROM role r JOIN permission p
  ON p.perm_code IN ('emp:read','emp:write','dept:read','dept:write','att:read','att:write','sal:read','sal:write')
WHERE r.role_code = 'HR';

INSERT INTO role_permission (role_id, perm_id)
SELECT r.role_id, p.perm_id FROM role r JOIN permission p
  ON p.perm_code IN ('emp:read','dept:read','att:read','sal:read','log:read')
WHERE r.role_code = 'VIEWER';

-- 示例账号：hr01 / viewer01，口令均为 123456
INSERT INTO sys_user (username, password_hash, display_name, status, create_time) VALUES
('hr01', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '人事专员', 1, '2026-01-01 00:00:00'),
('viewer01', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '只读用户', 1, '2026-01-01 00:00:00');

INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN role r ON r.role_code = 'ADMIN' WHERE u.username = 'admin';
INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN role r ON r.role_code = 'HR' WHERE u.username = 'hr01';
INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id FROM sys_user u JOIN role r ON r.role_code = 'VIEWER' WHERE u.username = 'viewer01';

SET FOREIGN_KEY_CHECKS = 1;
