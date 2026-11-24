# 接口清单

Base URL：`http://localhost:8080`（Tomcat 上下文路径设为 `/`；若保留默认则为 `http://localhost:8080/ssm-emp-mis`）

## 一、通用约定

**请求**

- 方法：`GET` / `POST` / `PUT` / `DELETE`
- 请求体：`Content-Type: application/json`，字符集 UTF-8
- 鉴权：session（`JSESSIONID` Cookie）。除 `/login` 与 `/health` 外均要求已登录

**响应**

统一结构，HTTP 状态码与 body 的 `code` 分工不同：

```json
{ "code": 200, "message": "success", "data": {} }
```

| 字段 | 说明 |
| :--- | :--- |
| `code` | 业务码：200 成功 / 500 业务失败或服务端异常 / 401 未登录 |
| `message` | 提示文案，成功固定为 `success` |
| `data` | 业务数据，无数据时为 `null` |

| 场景 | HTTP 状态 | body.code |
| :--- | :--- | :--- |
| 成功 | 200 | 200 |
| 业务失败（员工不存在、参数非法等） | **200** | 500 |
| 未登录 | **401** | 401 |
| 缺少必需参数 / 路径变量类型不符 | **400** | 400 |
| 权限不足（已登录但缺少权限点） | **403** | 403 |
| Content-Type 不是 `application/json` | **415** | 415 |
| 路径不存在 | 404 | 容器默认页 |
| 方法不支持 | 405 | 容器默认页 |

> 业务失败仍返回 HTTP 200 是指导书指定的 `code/message/data` 风格；认证失败、参数缺失、媒体类型不支持则用传输层状态码——它们是协议层错误，不是业务结论，ApiFox 等工具也会据此直接判定用例失败。

**失败提示示例**

| 触发条件 | message |
| :--- | :--- |
| 记录不存在 / 已删除 | `员工不存在：9999` |
| 校验失败（多字段） | `员工姓名不能为空；薪资不能为负数` |
| 请求体不是合法 JSON | `请求体格式错误，请检查 JSON 结构与字段类型` |
| 批量删除传空数组 | `批量删除的 ID 列表不能为空` |
| 口令错误 | `用户名或密码错误` |
| 缺少必需查询参数 | `缺少必需参数：month` |
| 部门不存在 / 已停用 | `部门不存在：X，请先在部门管理中创建` / `部门已停用，不能分配员工：X` |
| 未捕获异常 | `系统繁忙，请稍后重试` |

**接口总数 47 个**：健康 2、认证 2 + 当前用户 1、员工 6、部门 6、考勤 6、薪资 7、账号 8、角色与权限 8、审计 1。

## 二、健康检查

### GET /health

框架存活检查，不访问数据库。

```json
{ "code": 200, "message": "success", "data": "UP" }
```

### GET /health/db

持久层检查，返回 `emp` 表行数（种子数据为 12）。数据源、Mapper 注入、分页插件任一环节异常都会在这里暴露。

```json
{ "code": 200, "message": "success", "data": 12 }
```

## 三、认证

### POST /login

请求：

```json
{ "username": "admin", "password": "123456" }
```

成功响应（同时下发 `JSESSIONID`）：

```json
{
  "code": 200,
  "message": "success",
  "data": { "userId": 1, "username": "admin", "displayName": "系统管理员" }
}
```

失败：

```json
{ "code": 500, "message": "用户名或密码错误", "data": null }
```

> 用户不存在与口令错误返回同一提示，避免用户名枚举。

### POST /logout

销毁会话，之后访问受保护接口返回 401。

```json
{ "code": 200, "message": "success", "data": null }
```

## 四、员工管理

**`dept` 必须已存在且处于启用状态**：新增与修改都会校验，否则返回「部门不存在」或「部门已停用」。这条校验与「部门改名级联刷新 `emp.dept`」互为补充——只做级联不校验写入，会形成单向一致：部门改了员工跟着变，员工却能挂到一个部门表里没有的名称上。

### POST /emps —— 新增

请求（`empId`、`createTime`、`updateTime`、`deleted` 由服务端生成，传入会被忽略）：

```json
{
  "empName": "张伟",
  "gender": "男",
  "dept": "研发部",
  "post": "后端工程师",
  "salary": 18500.00,
  "hireDate": "2021-03-15",
  "status": 1
}
```

响应：`{ "code": 200, "message": "success", "data": null }`

校验规则：`empName` 非空且 ≤50；`gender` 只能为「男」或「女」；`dept` 非空且 ≤50；`post` ≤50；`salary` 非空且 ≥0；`hireDate` 非空且不晚于今天；`status` 只能为 0 或 1。

### PUT /emps —— 修改

请求体与新增相同，但必须带 `empId`。仅更新非 null 字段。

```json
{ "empId": 13, "empName": "张伟", "gender": "男", "dept": "研发部",
  "post": "高级后端工程师", "salary": 20000.00, "hireDate": "2021-03-15", "status": 1 }
```

`empId` 不存在或记录已逻辑删除时返回 `员工不存在或已删除：13`。

### DELETE /emps/{id} —— 删除单条

逻辑删除：接口层面查不到，数据库行仍在且 `deleted = 1`。重复删除返回 `员工不存在或已删除：{id}`。

### DELETE /emps —— 批量删除

请求（ID 列表放请求体，注意不是查询参数）：

```json
[14, 15]
```

响应 `data` 为实际受影响行数：

```json
{ "code": 200, "message": "success", "data": 2 }
```

传 `[]` 返回 `批量删除的 ID 列表不能为空`。

### GET /emps/{id} —— 单条查询

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "empId": 1, "empName": "张伟", "gender": "男", "dept": "研发部",
    "post": "后端工程师", "salary": 18500.00, "hireDate": "2021-03-15",
    "status": 1, "createTime": "2021-03-15 09:00:00", "updateTime": "2021-03-15 09:00:00"
  }
}
```

注意两点：金额保持两位小数；`deleted` 字段被 `@JsonIgnore` 屏蔽，不出现在响应中。

### GET /emps —— 列表 + 多条件 + 分页

查询参数（全部可选）：

| 参数 | 类型 | 默认 | 说明 |
| :--- | :--- | :--- | :--- |
| `name` | string | — | 姓名模糊匹配，前后模糊；空白字符串视为未传 |
| `gender` | string | — | 性别精确匹配 |
| `dept` | string | — | 部门精确匹配 |
| `page` | int | 1 | 页码，从 1 开始；<1 回落到 1 |
| `size` | int | 5 | 每页条数；<1 回落到 5；上限 100 |
| `sortBy` | string | empId | 排序字段，白名单：`empId` / `empName` / `salary` / `hireDate`；越界值回落 `empId` |
| `sortDir` | string | desc | `asc` 升序；其余（含缺省、拼错）一律降序 |

> `sortBy` 走**白名单**而不是直接拼进 SQL。排序列无法使用 `?` 占位符，只能拼接，若直接拼用户输入就是注入入口（`sortBy=empName;drop table emp--`）。白名单把可影响的 SQL 片段限制为几个常量，越界即回落默认。

请求示例：`GET /emps?name=张&dept=研发部&page=1&size=5&sortBy=salary&sortDir=asc`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      { "empId": 2, "empName": "张敏", "gender": "女", "dept": "研发部",
        "post": "前端工程师", "salary": 16800.00, "hireDate": "2021-07-01",
        "status": 1, "createTime": "2021-07-01 09:00:00", "updateTime": "2021-07-01 09:00:00" }
    ],
    "total": 2,
    "size": 5,
    "current": 1,
    "pages": 1
  }
}
```

排序默认 `emp_id` 倒序，保证分页结果稳定；可用 `sortBy` + `sortDir` 改排序（见上表）。

## 五、部门管理

部门与员工靠**部门名称**关联（`emp.dept` 存名称），因此这个模块有两处跨模块行为：删除前校验、改名级联。

`status` 不是装饰字段：**停用部门不能再分配员工**。否则「停用」只影响显示，列表里照样挂人，按部门做的统计也会把已撤销的部门算进去。

### GET /depts

查询参数：`name`（可选，名称模糊）。

```json
{
  "code": 200, "message": "success",
  "data": [
    { "deptId": 1, "deptName": "研发部", "location": "A 座 3 层", "status": 1,
      "createTime": "2026-01-01 00:00:00", "updateTime": "2026-01-01 00:00:00" }
  ]
}
```

不分页：部门是低频变更的基础数据，量级在两位数，分页对使用者是负担。

### GET /depts/{id}

不存在时返回 `部门不存在：{id}`。

### POST /depts —— 新增

```json
{ "deptName": "运维部", "location": "C 座 1 层", "status": 1 }
```

`status` 不传默认为 1。名称重复返回 `部门名称已存在：{name}`；`deptName` 为空或超 50 字返回校验提示。

### PUT /depts —— 修改

请求体必须带 `deptId`。**名称变更时会在同一事务内把 `emp.dept` 的旧名一并刷成新名**，否则那些员工的部门字段会指向一个不存在的部门。

```json
{ "deptId": 1, "deptName": "研发中心", "location": "A 座 3 层", "status": 1 }
```

### DELETE /depts/{id} —— 删除

部门下仍有员工时拒绝：

```json
{ "code": 500, "message": "该部门下仍有 4 名员工，无法删除", "data": null }
```

空部门可以正常删除。

### GET /depts/{id}/employees —— 该部门员工

路径里的 `{id}` 是**部门 ID**。按部门名称匹配 `emp.dept`，按 `emp_id` 升序返回，不分页。

```json
{
  "code": 200, "message": "success",
  "data": [ { "empId": 1, "empName": "张伟", "dept": "研发部", "salary": 18500.00 } ]
}
```

## 六、考勤

**状态由服务端推导，不接收客户端传入。** 请求体只需给打卡时间，`status` 字段即使传了也会被忽略。

| 打卡情况 | 推导状态 | 取值 |
| :--- | :--- | :-- |
| 上下班都不填 | 缺勤 | 3 |
| 上班晚于 09:00 | 迟到 | 1 |
| 下班早于 18:00 | 早退 | 2 |
| 其余 | 正常 | 0 |

同时满足迟到与早退时按**迟到**记。理由：状态是对打卡事实的判定结论，允许手选会出现「09:35 打卡却标正常」，而出勤率与薪资扣款都建立在这个字段上。

### GET /attendance —— 分页查询

| 参数 | 类型 | 默认 | 说明 |
| :--- | :--- | :--- | :--- |
| `empId` | int | 不限 | 员工 ID |
| `month` | string | 不限 | `YYYY-MM`，按 `work_date` 落在该月过滤 |
| `page` | int | 1 | 页码 |
| `size` | int | 10 | 每页条数，上限 100 |

返回 `Page`，每条记录含 `empName`（服务端批量回填，员工已删时为「已删除员工」）。

```json
{
  "code": 200, "message": "success",
  "data": {
    "records": [
      { "attId": 1, "empId": 1, "empName": "张伟", "workDate": "2026-11-02",
        "checkIn": "09:00:00", "checkOut": "18:00:00", "status": 0, "remark": null }
    ],
    "total": 12, "size": 10, "current": 1, "pages": 2
  }
}
```

### GET /attendance/{id}

不存在返回 `考勤记录不存在：{id}`。

### POST /attendance —— 登记

```json
{ "empId": 7, "workDate": "2026-11-05", "checkIn": "09:00:00", "checkOut": "18:00:00", "remark": "补录" }
```

`checkIn` / `checkOut` 必须**同时填写或同时留空**。留空表示缺勤。

| 触发条件 | message |
| :--- | :--- |
| 员工不存在 | `员工不存在：999` |
| 只填一个时间 | `上班与下班打卡时间必须同时填写，或同时留空` |
| 下班早于上班 | `下班打卡时间不能早于上班打卡时间` |
| 考勤日期晚于今天 | `考勤日期不能晚于今天` |
| 同一人同一天已有记录 | `该员工在 2026-11-04 已有考勤记录` |

### PUT /attendance —— 修改

请求体必须带 `attId`；改动时间后状态会重新推导。

### DELETE /attendance/{id}

物理删除，历史考勤不保留。若该月考勤已参与薪资计算，删除后需调用薪资「重算」同步。

### GET /attendance/summary —— 月度汇总

| 参数 | 必填 | 说明 |
| :--- | :--: | :--- |
| `month` | 是 | `YYYY-MM`；缺失返回 400 `缺少必需参数：month`，格式非法返回业务失败 |

```json
{ "code": 200, "message": "success",
  "data": [ { "empId": 1, "empName": "张伟", "totalDays": 2, "normalDays": 2,
              "lateDays": 0, "earlyDays": 0, "absentDays": 0, "attendanceRate": 100.0 } ] }
```

`attendanceRate = (总天数 − 缺勤天数) / 总天数 × 100`，保留两位。

## 七、薪资

**实发金额只由服务端计算**，`total` 即使传入也会被忽略。三个分量在计算前各自量化到两位小数，避免 `0.005 + 0.005` 与落库值出现 1 分钱偏差。

**基本工资取「薪资标准」而非 `emp.salary`**：生成某月薪资时，从 `salary_standard` 取「生效日期不晚于该月最后一天」的最后一条，查不到才回退 `emp.salary`。这样调薪后补生成历史月份，用的仍是当时的数额。员工改薪时由服务端自动写入一条标准，生效日为**当月 1 日**（同月重复调薪覆盖），不开放独立接口。

**扣款来自考勤**（规则见 [4.5](../README.md#45-考勤如何影响薪资)）：

```
日薪 = 基本工资 ÷ 21.75
扣款 = 缺勤天数 × 日薪 + 迟到次数 × 20 + 早退次数 × 20   （上限为基本工资）
实发 = 基本工资 + 奖金 − 扣款
```

### GET /salaries —— 分页查询

参数 `empId` / `month` / `page` / `size`，返回 `Page`，记录含 `empName`。

### GET /salaries/{id}

不存在返回 `薪资记录不存在：{id}`。

### POST /salaries/generate —— 按月批量生成

`POST /salaries/generate?month=2026-11`

对**在职**员工逐个生成：`base` 取 `emp.salary`，`bonus` 为 0，`deduction` 按当月考勤计算，`status` 为待发放。已存在的员工跳过，不覆盖。

```json
{ "code": 200, "message": "success",
  "data": { "month": "2026-11", "generated": 10, "skipped": 0, "deductedTotal": 1025.52 } }
```

### POST /salaries/recalculate —— 按当前考勤重算

`POST /salaries/recalculate?month=2026-11`

存在的理由：生成是幂等的，所以「先算薪、后补录考勤」时薪资不会自动跟随。重算提供显式修正通道：

- 只处理**待发放**记录，已发放一律跳过（`skipped` 计数）；
- 会覆盖手工调整过的扣款——语义就是「以考勤为准」；
- 该月没有任何记录时返回 `该月还没有薪资记录，请先生成：{month}`。

返回结构与 `generate` 一致，`generated` 表示重算条数。

### PUT /salaries —— 调整

```json
{ "id": 8, "empId": 1, "month": "2026-11", "base": 18500.00, "bonus": 1200.00, "deduction": 200.00 }
```

`empId` 与 `month` 仅作校验占位，**改了不生效**（记录归属不允许修改）；实发按三分量重算。

| 触发条件 | message |
| :--- | :--- |
| 已发放 | `该月薪资已发放，不能再修改` |
| 扣款大于应发 | `扣款不能大于基本工资与奖金之和` |

### PUT /salaries/{id}/pay —— 发放

状态从待发放（0）单向流转到已发放（1）。没有「撤销发放」的接口——钱一旦出去，系统层面不应能一键撤回。重复调用返回 `该月薪资已是已发放状态`。

### DELETE /salaries/{id}

仅待发放可删；已发放返回 `该月薪资已发放，不能删除`。

## 八、账号

口令以 SHA-256 摘要存储，实体上的 `passwordHash` 标注 `@JsonIgnore`，任何响应都不会包含它。

### GET /users —— 分页查询

参数 `keyword`（用户名或显示名模糊）/ `status` / `page` / `size`。

### GET /users/{id}

不存在返回 `账号不存在：{id}`。

### POST /users —— 新增

```json
{ "username": "qa01", "password": "qa123456", "displayName": "验收员", "status": 1 }
```

`password` 长度 6–32；用户名唯一，重复返回 `用户名已存在：{username}`。

### PUT /users —— 修改显示名与状态

```json
{ "userId": 2, "displayName": "张三丰", "status": 1 }
```

用户名不可改。**不能停用当前登录账号**：`不能停用当前登录的账号`。

### PUT /users/{id}/password —— 重置口令

```json
{ "newPassword": "newpass1" }
```

### DELETE /users/{id}

两道防护：不能删当前登录账号（`不能删除当前登录的账号`）；不能删掉最后一个启用账号（`这是最后一个启用状态的账号，不能删除`）——本系统没有找回口令通道，启用账号归零就只能进库手工改。

## 九、操作审计

审计由 `AuditAspect` 在写操作（POST / PUT / DELETE）执行时自动落库，**没有任何写入接口**，业务代码无法伪造或删除记录。

### GET /op-logs —— 分页查询

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `operator` | string | 操作人登录名，模糊匹配 |
| `page` / `size` | int | 默认 1 / 10，上限 100 |

```json
{ "code": 200, "message": "success",
  "data": { "records": [
    { "logId": 12, "operator": "admin", "action": "DELETE", "target": "/users/1",
      "detail": "不能删除当前登录的账号", "result": 0, "createTime": "2026-11-24 16:35:08" }
  ], "total": 33, "size": 10, "current": 1, "pages": 4 } }
```

| 字段 | 说明 |
| :--- | :--- |
| `result` | 1 成功 / 0 失败。**失败也落库**——排查时最想知道「谁试图改、为什么没改成」 |
| `detail` | 成功记 `success`；失败记业务异常原文或异常类名，异常堆栈只进控制台 |
| `target` | 请求路径，含路径变量 |

**覆盖边界**：`@Valid` 触发的参数校验失败发生在 Controller 方法体之前，切面看不到，因此**校验失败不进审计表**。这是有意的：校验失败意味着数据没进业务层，不存在被改动的可能，控制台日志仍有 `[REQ]` 记录。

## 十、角色与权限

**两层校验**：`LoginInterceptor` 判断有没有登录（401），`PermissionAspect` 判断能不能做这件事（403）。权限点编码形如 `emp:write`，由 `@RequirePermission` 标注在接口上。

受保护接口在缺少权限时返回：

```json
{ "code": 403, "message": "无权限执行该操作，需要：emp:write", "data": null }
```

### GET /me —— 当前登录人与权限

```json
{ "code": 200, "message": "success",
  "data": { "userId": 1, "username": "admin", "displayName": "系统管理员",
            "permissions": ["emp:read", "emp:write", "..."] } }
```

权限实时查库，管理员调整角色后刷新即生效。前端用它决定显示哪些入口——**隐藏按钮不等于限制调用**，越权仍返回 403。

### GET /roles —— 角色列表

返回全部角色，按 `role_id` 升序。以下角色相关接口均要求 `role:write`（只有 ADMIN 拥有）。

### POST /roles / PUT /roles / DELETE /roles/{id}

```json
{ "roleCode": "FINANCE", "roleName": "财务专员", "remark": "只看薪资" }
```

| 触发条件 | message |
| :--- | :--- |
| 编码重复 | `角色编码已存在：FINANCE` |
| 删除内置管理员 | `内置管理员角色不能删除` |
| 删除仍有账号的角色 | `该角色仍被 2 个账号使用，不能删除` |
| 修改内置管理员编码 | `内置管理员角色的编码不可修改` |

### GET /roles/permissions —— 全部权限点

供分配界面渲染勾选框，按模块、编码排序。权限点由种子数据维护，**不提供增删接口**——权限点是代码里真实存在的校验规则，运行时新增只会造出永远不会被使用的僵尸权限。

### GET /roles/{id}/permissions —— 角色已分配权限

返回权限 ID 数组。

### PUT /roles/{id}/permissions —— 覆盖式分配

请求体为权限 ID 数组，传 `[]` 表示清空：

```json
[1, 2, 3, 4]
```

### GET /users/{id}/roles / PUT /users/{id}/roles

查询与覆盖式分配账号角色（`role:write`）。最后一条防呆：

```json
{ "code": 500, "message": "这是最后一个管理员账号，不能取消其管理员角色" }
```

一旦没有管理员，系统内没有任何后门可以改回来，所以这条必须拦住。

## 十一、调用顺序示例

```bash
# 1) 登录，保存 Cookie
curl -c cookie.txt -X POST http://127.0.0.1:8080/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"123456"}'

# 2) 带 Cookie 调用业务接口
curl -b cookie.txt 'http://127.0.0.1:8080/emps?page=1&size=5'

# 3) 退出
curl -b cookie.txt -X POST http://127.0.0.1:8080/logout
```

## 十二、未实现

| 接口 | 状态 | 说明 |
| :--- | :--- | :--- |
| `POST /register` | 未实现 | 指导书未要求注册，账号由 `sql/init.sql` 初始化 |
| 令牌刷新 / JWT | 未实现 | 当前使用 Servlet 原生 session |
| 员工导出 | 未实现 | 超出实验范围 |
