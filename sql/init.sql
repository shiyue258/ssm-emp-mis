-- ============================================================================
-- ssm-emp-mis 数据库初始化脚本
--
-- 用法（必须显式指定 utf8mb4，否则中文会按 latin1 二次编码成乱码）：
--   docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 < sql/init.sql
--
-- 本脚本可重复执行：先 DROP 再 CREATE，不会因重复导入报错。
-- ============================================================================

SET NAMES utf8mb4;

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
