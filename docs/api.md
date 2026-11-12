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
| 路径不存在 | 404 | 容器默认页 |
| 方法不支持 | 405 | 容器默认页 |

> 业务失败仍返回 HTTP 200 是指导书指定的 `code/message/data` 风格；认证失败单独用 401，因为那是传输层语义，且 ApiFox 等工具会据此直接判定用例失败。

**失败提示示例**

| 触发条件 | message |
| :--- | :--- |
| 记录不存在 / 已删除 | `员工不存在：9999` |
| 校验失败（多字段） | `员工姓名不能为空；薪资不能为负数` |
| 请求体不是合法 JSON | `请求体格式错误，请检查 JSON 结构与字段类型` |
| 批量删除传空数组 | `批量删除的 ID 列表不能为空` |
| 口令错误 | `用户名或密码错误` |
| 未捕获异常 | `系统繁忙，请稍后重试` |

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

请求示例：`GET /emps?name=张&dept=研发部&page=1&size=5`

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

排序固定为 `emp_id` 倒序，保证分页结果稳定。

## 五、调用顺序示例

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

## 六、未实现

| 接口 | 状态 | 说明 |
| :--- | :--- | :--- |
| `POST /register` | 未实现 | 指导书未要求注册，账号由 `sql/init.sql` 初始化 |
| 令牌刷新 / JWT | 未实现 | 当前使用 Servlet 原生 session |
| 员工导出 | 未实现 | 超出实验范围 |
