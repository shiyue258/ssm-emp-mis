#!/usr/bin/env bash
export no_proxy='*'
export NO_PROXY='*'
B="http://127.0.0.1:8080"
J=/tmp/std_cookie.txt
MYSQL="docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N"
rm -f "$J"
for i in $(seq 1 30); do c=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$B/health" 2>/dev/null); [ "$c" = "200" ] && break; sleep 2; done
echo "服务就绪 HTTP $c"
curl -sS -c "$J" -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' >/dev/null

echo
echo "=== 1. 张伟初始薪资与标准 ==="
$MYSQL -e "select concat('emp.salary=',salary) from ssm_emp.emp where emp_id=1;" 2>/dev/null
$MYSQL -e "select concat('标准: ',effective_from,' -> ',base) from ssm_emp.salary_standard where emp_id=1 order by effective_from;" 2>/dev/null

echo "=== 2. 把张伟薪资调到 20000（生效日应为当月 1 日）==="
curl -sS -b "$J" -X PUT "$B/emps" -H 'Content-Type: application/json' \
  -d '{"empId":1,"empName":"张伟","gender":"男","dept":"研发部","post":"后端工程师","salary":20000.00,"hireDate":"2021-03-15","status":1}' | jq -c '{code,message}'
$MYSQL -e "select concat('标准: ',effective_from,' -> ',base) from ssm_emp.salary_standard where emp_id=1 order by effective_from;" 2>/dev/null

echo "=== 3. 补生成 2026-09（调薪之前，应使用 18500）==="
curl -sS -b "$J" -X POST "$B/salaries/generate" -G --data-urlencode "month=2026-09" >/dev/null
$MYSQL -e "select concat('2026-09 base=',base,' total=',total) from ssm_emp.salary_record where emp_id=1 and month='2026-09';" 2>/dev/null

echo "=== 4. 生成 2026-12（调薪之后，应使用 20000）==="
curl -sS -b "$J" -X POST "$B/salaries/generate" -G --data-urlencode "month=2026-12" >/dev/null
$MYSQL -e "select concat('2026-12 base=',base,' total=',total) from ssm_emp.salary_record where emp_id=1 and month='2026-12';" 2>/dev/null

echo "=== 5. 同月再次调薪（应覆盖当月标准，不留两条）==="
curl -sS -b "$J" -X PUT "$B/emps" -H 'Content-Type: application/json' \
  -d '{"empId":1,"empName":"张伟","gender":"男","dept":"研发部","post":"后端工程师","salary":21000.00,"hireDate":"2021-03-15","status":1}' | jq -c '{code}'
$MYSQL -e "select concat('标准条数=',count(*),' 当月值=',max(base)) from ssm_emp.salary_standard where emp_id=1 and effective_from='2026-11-01';" 2>/dev/null

echo "=== 6. 新增员工是否自动写入初始标准 ==="
curl -sS -b "$J" -X POST "$B/emps" -H 'Content-Type: application/json' \
  -d '{"empName":"标准测试员","gender":"女","dept":"研发部","post":"x","salary":8000.00,"hireDate":"2026-05-06","status":1}' | jq -c '{code}'
NID=$($MYSQL -e "select emp_id from ssm_emp.emp where emp_name='标准测试员' and deleted=0;" 2>/dev/null | tr -d ' ')
$MYSQL -e "select concat('新员工标准: ',effective_from,' -> ',base) from ssm_emp.salary_standard where emp_id=$NID;" 2>/dev/null
