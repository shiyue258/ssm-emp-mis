#!/usr/bin/env bash
export no_proxy='*'
export NO_PROXY='*'
B="http://127.0.0.1:8080"
J=/tmp/all_cookie.txt
J2=/tmp/all_cookie2.txt
MYSQL="docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N"
rm -f "$J" "$J2"

for i in $(seq 1 30); do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$B/health" 2>/dev/null)
  [ "$code" = "200" ] && break
  sleep 2
done
echo "服务就绪: HTTP $code"

echo
echo "########## 一、健康检查与认证 ##########"
printf "H01 /health        : "; curl -sS "$B/health" | jq -c '{code,data}'
printf "H02 /health/db     : "; curl -sS "$B/health/db" | jq -c '{code,data}'
printf "A01 未登录 /emps    : "; curl -sS -o /dev/null -w "HTTP %{http_code}\n" "$B/emps"
printf "A02 错口令登录      : "; curl -sS -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"bad"}' | jq -c '{code,message}'
printf "A03 正确登录        : "; curl -sS -c "$J" -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' | jq -c '{code,display:.data.displayName}'

echo
echo "########## 二、员工 ##########"
printf "E01 列表           : "; curl -sS -b "$J" "$B/emps" | jq -c '{total:.data.total,n:(.data.records|length)}'
printf "E02 末页           : "; curl -sS -b "$J" "$B/emps?page=3&size=5" | jq -c '{current:.data.current,n:(.data.records|length)}'
printf "E03 薪资升序       : "; curl -sS -b "$J" -G "$B/emps" -d sortBy=salary -d sortDir=asc -d size=2 | jq -c '[.data.records[]|"\(.empName):\(.salary)"]'
printf "E04 单条           : "; curl -sS -b "$J" "$B/emps/1" | jq -c '{name:.data.empName,hired:.data.hireDate}'
printf "E05 不存在         : "; curl -sS -b "$J" "$B/emps/9999" | jq -c '{code,message}'
printf "E06 新增           : "; curl -sS -b "$J" -X POST "$B/emps" -H 'Content-Type: application/json' -d '{"empName":"验收员工","gender":"男","dept":"研发部","post":"工程师","salary":10000.00,"hireDate":"2025-01-01","status":1}' | jq -c '{code}'
EID=$($MYSQL -e "select emp_id from ssm_emp.emp where emp_name='验收员工';" 2>/dev/null | tr -d ' ')
printf "E07 空姓名校验     : "; curl -sS -b "$J" -X POST "$B/emps" -H 'Content-Type: application/json' -d '{"empName":"","gender":"男","dept":"研发部","salary":100,"hireDate":"2025-01-01","status":1}' | jq -c '{code,message}'
printf "E08 修改薪资       : "; curl -sS -b "$J" -X PUT "$B/emps" -H 'Content-Type: application/json' -d "{\"empId\":$EID,\"empName\":\"验收员工\",\"gender\":\"男\",\"dept\":\"研发部\",\"post\":\"工程师\",\"salary\":13500.00,\"hireDate\":\"2025-01-01\",\"status\":1}" | jq -c '{code}'
printf "    复查薪资       : "; curl -sS -b "$J" "$B/emps/$EID" | jq -c '{salary:.data.salary}'
printf "E09 删除（逻辑）   : "; curl -sS -b "$J" -X DELETE "$B/emps/$EID" | jq -c '{code}'
printf "    再查 / 物理行  : "; curl -sS -b "$J" "$B/emps/$EID" | jq -r '.message'; $MYSQL -e "select concat('deleted=','$EID') from ssm_emp.emp where emp_id=$EID;" 2>/dev/null >/dev/null; echo -n "                     "; $MYSQL -e "select concat('emp_id=',$EID,' deleted=',deleted) from ssm_emp.emp where emp_id=$EID;" 2>/dev/null
printf "E10 批量空数组     : "; curl -sS -b "$J" -X DELETE "$B/emps" -H 'Content-Type: application/json' -d '[]' | jq -c '{code,message}'

echo
echo "########## 三、部门 ##########"
printf "D01 列表           : "; curl -sS -b "$J" "$B/depts" | jq -c '{n:(.data|length)}'
printf "D02 部门员工       : "; curl -sS -b "$J" "$B/depts/1/employees" | jq -c '{n:(.data|length),names:[.data[].empName]}'
printf "D03 重名           : "; curl -sS -b "$J" -X POST "$B/depts" -H 'Content-Type: application/json' -d '{"deptName":"研发部","location":"X","status":1}' | jq -c '{code,message}'
printf "D04 改名级联       : "; curl -sS -b "$J" -X PUT "$B/depts" -H 'Content-Type: application/json' -d '{"deptId":1,"deptName":"研发中心","location":"A 座 3 层","status":1}' | jq -c '{code}'
printf "    员工同步结果   : "; $MYSQL -e "select concat('研发中心=',sum(dept='研发中心'),' 研发部=',sum(dept='研发部')) from ssm_emp.emp where deleted=0;" 2>/dev/null
printf "D05 删有员工部门   : "; curl -sS -b "$J" -X DELETE "$B/depts/1" | jq -c '{code,message}'
printf "D06 改回原名       : "; curl -sS -b "$J" -X PUT "$B/depts" -H 'Content-Type: application/json' -d '{"deptId":1,"deptName":"研发部","location":"A 座 3 层","status":1}' | jq -c '{code}'

echo
echo "########## 四、账号与审计 ##########"
printf "U01 列表           : "; curl -sS -b "$J" "$B/users" | jq -c '{total:.data.total,hasHash:(.data.records[0]|has("passwordHash"))}'
printf "U02 新增账号       : "; curl -sS -b "$J" -X POST "$B/users" -H 'Content-Type: application/json' -d '{"username":"qa01","password":"qa123456","displayName":"验收员","status":1}' | jq -c '{code}'
UID2=$($MYSQL -e "select user_id from ssm_emp.sys_user where username='qa01';" 2>/dev/null | tr -d ' ')
printf "U03 重置口令       : "; curl -sS -b "$J" -X PUT "$B/users/$UID2/password" -H 'Content-Type: application/json' -d '{"newPassword":"newqa123"}' | jq -c '{code}'
printf "    新口令登录     : "; curl -sS -c "$J2" -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"qa01","password":"newqa123"}' | jq -c '{code,user:.data.username}'
printf "U04 停用自己       : "; curl -sS -b "$J" -X PUT "$B/users" -H 'Content-Type: application/json' -d '{"userId":1,"displayName":"系统管理员","status":0}' | jq -c '{code,message}'
printf "U05 删自己         : "; curl -sS -b "$J" -X DELETE "$B/users/1" | jq -c '{code,message}'
printf "U06 删他人         : "; curl -sS -b "$J" -X DELETE "$B/users/$UID2" | jq -c '{code}'
printf "L01 审计记录       : "; curl -sS -b "$J" "$B/op-logs?size=5" | jq -c '{total:.data.total,recent:[.data.records[]|"\(.operator) \(.action) \(.result)"]}'
printf "    审计含失败记录 : "; $MYSQL -e "select concat('失败=',sum(result=0),' 成功=',sum(result=1)) from ssm_emp.op_log;" 2>/dev/null

echo
echo "########## 五、考勤 ##########"
printf "T01 列表           : "; curl -sS -b "$J" "$B/attendance" | jq -c '{total:.data.total}'
printf "T02 月度汇总       : "; curl -sS -b "$J" -G "$B/attendance/summary" -d month=2026-11 | jq -c '{n:(.data|length),emp4:(.data[]|select(.empId==4)|{absentDays,attendanceRate})}'
printf "T03 新增           : "; curl -sS -b "$J" -X POST "$B/attendance" -H 'Content-Type: application/json' -d '{"empId":7,"workDate":"2026-11-04","checkIn":"09:00:00","checkOut":"18:00:00","status":0,"remark":"补录"}' | jq -c '{code}'
AID=$($MYSQL -e "select att_id from ssm_emp.attendance where emp_id=7 and work_date='2026-11-04';" 2>/dev/null | tr -d ' ')
printf "T04 同人同日重复   : "; curl -sS -b "$J" -X POST "$B/attendance" -H 'Content-Type: application/json' -d '{"empId":7,"workDate":"2026-11-04","status":0}' | jq -c '{code,message}'
printf "T05 时间倒挂       : "; curl -sS -b "$J" -X POST "$B/attendance" -H 'Content-Type: application/json' -d '{"empId":7,"workDate":"2026-11-06","checkIn":"18:00:00","checkOut":"09:00:00","status":0}' | jq -c '{code,message}'
printf "T06 员工不存在     : "; curl -sS -b "$J" -X POST "$B/attendance" -H 'Content-Type: application/json' -d '{"empId":999,"workDate":"2026-11-06","status":0}' | jq -c '{code,message}'
printf "T07 非法状态       : "; curl -sS -b "$J" -X POST "$B/attendance" -H 'Content-Type: application/json' -d '{"empId":7,"workDate":"2026-11-06","status":5}' | jq -c '{code,message}'
printf "T08 修改           : "; curl -sS -b "$J" -X PUT "$B/attendance" -H 'Content-Type: application/json' -d "{\"attId\":$AID,\"empId\":7,\"workDate\":\"2026-11-04\",\"checkIn\":\"09:00:00\",\"checkOut\":\"18:30:00\",\"status\":0,\"remark\":\"加班\"}" | jq -c '{code}'
printf "    复查           : "; curl -sS -b "$J" "$B/attendance/$AID" | jq -c '{out:.data.checkOut,remark:.data.remark}'
printf "T09 删除           : "; curl -sS -b "$J" -X DELETE "$B/attendance/$AID" | jq -c '{code}'
printf "T10 非法月份       : "; curl -sS -b "$J" -G "$B/attendance/summary" -d month=2026-13 | jq -c '{code,message}'

echo
echo "########## 六、薪资 ##########"
printf "S01 列表           : "; curl -sS -b "$J" "$B/salaries" | jq -c '{total:.data.total}'
printf "S02 生成 2026-11   : "; curl -sS -b "$J" -X POST "$B/salaries/generate" -G --data-urlencode "month=2026-11" | jq -c '{code,data}'
printf "S03 重复生成       : "; curl -sS -b "$J" -X POST "$B/salaries/generate" -G --data-urlencode "month=2026-11" | jq -c '{code,data}'
SID=$($MYSQL -e "select id from ssm_emp.salary_record where emp_id=1 and month='2026-11';" 2>/dev/null | tr -d ' ')
SBASE=$($MYSQL -e "select base from ssm_emp.salary_record where id=$SID;" 2>/dev/null | tr -d ' ')
printf "S04 调整奖金扣款   : "; curl -sS -b "$J" -X PUT "$B/salaries" -H 'Content-Type: application/json' -d "{\"id\":$SID,\"empId\":1,\"month\":\"2026-11\",\"base\":$SBASE,\"bonus\":1200.00,\"deduction\":200.00}" | jq -c '{code}'
printf "    复查 total     : "; curl -sS -b "$J" "$B/salaries/$SID" | jq -c '.data|{base,bonus,deduction,total}'
PID=$($MYSQL -e "select id from ssm_emp.salary_record where emp_id=1 and month='2026-10';" 2>/dev/null | tr -d ' ')
printf "S05 改已发放       : "; curl -sS -b "$J" -X PUT "$B/salaries" -H 'Content-Type: application/json' -d "{\"id\":$PID,\"empId\":1,\"month\":\"2026-10\",\"base\":18500.00,\"bonus\":1.00,\"deduction\":0.00}" | jq -c '{code,message}'
printf "S06 发放           : "; curl -sS -b "$J" -X PUT "$B/salaries/$SID/pay" | jq -c '{code}'
printf "    重复发放       : "; curl -sS -b "$J" -X PUT "$B/salaries/$SID/pay" | jq -c '{code,message}'
printf "S07 删已发放       : "; curl -sS -b "$J" -X DELETE "$B/salaries/$PID" | jq -c '{code,message}'
PEND=$($MYSQL -e "select id from ssm_emp.salary_record where month='2026-11' and status=0 limit 1;" 2>/dev/null | tr -d ' ')
printf "S08 删待发放       : "; curl -sS -b "$J" -X DELETE "$B/salaries/$PEND" | jq -c '{code}'
printf "S09 非法月份       : "; curl -sS -b "$J" -X POST "$B/salaries/generate" -G --data-urlencode "month=2026-1" | jq -c '{code,message}'
SAL2=$($MYSQL -e "select id from ssm_emp.salary_record where month='2026-11' and status=0 limit 1;" 2>/dev/null | tr -d ' ')
printf "S10 扣款超限       : "; curl -sS -b "$J" -X PUT "$B/salaries" -H 'Content-Type: application/json' -d "{\"id\":$SAL2,\"empId\":2,\"month\":\"2026-11\",\"base\":100.00,\"bonus\":0.00,\"deduction\":9999.00}" | jq -c '{code,message}'

echo
echo "########## 七、协议边界 ##########"
printf "X01 未知路径       : "; curl -sS -o /dev/null -w "HTTP %{http_code}\n" -b "$J" "$B/nope"
printf "X02 方法不支持     : "; curl -sS -o /dev/null -w "HTTP %{http_code}\n" -b "$J" -X POST "$B/health"
printf "X03 路径变量类型   : "; curl -sS -b "$J" "$B/emps/abc" | jq -c '{code,message}'
printf "X04 非法 JSON      : "; curl -sS -b "$J" -X POST "$B/emps" -H 'Content-Type: application/json' -d '{bad' | jq -c '{code,message}'

echo
echo "########## 八、数据一致性 ##########"
printf "员工总数/逻辑删行  : "; $MYSQL -e "select concat('total=',count(*),' deleted=',sum(deleted=1)) from ssm_emp.emp;" 2>/dev/null
printf "部门数/审计数      : "; $MYSQL -e "select concat('dept=',(select count(*) from ssm_emp.dept),' oplog=',(select count(*) from ssm_emp.op_log),' att=',(select count(*) from ssm_emp.attendance),' sal=',(select count(*) from ssm_emp.salary_record));" 2>/dev/null
printf "审计表中明文口令   : "; $MYSQL -e "select concat('命中=',count(*)) from ssm_emp.op_log where detail like '%123456%' or detail like '%qa123456%' or detail like '%newqa123%';" 2>/dev/null
