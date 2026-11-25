#!/usr/bin/env bash
export no_proxy='*'
export NO_PROXY='*'
B="http://127.0.0.1:8080"
J=/tmp/perf_cookie.txt
rm -f "$J"
for i in $(seq 1 20); do c=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$B/health" 2>/dev/null); [ "$c" = "200" ] && break; sleep 1; done

echo "=== 静态页面加载 ==="
for i in 1 2 3; do
  curl -sS -o /dev/null -w "  第 $i 次: 耗时 %{time_total}s  大小 %{size_download}B  连接 %{time_connect}s\n" "$B/"
done

curl -sS -c "$J" -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' >/dev/null

echo
echo "=== 主要查询接口耗时（各 3 次）==="
for ep in "/emps?page=1&size=10" "/depts" "/attendance?month=2026-11&size=10" "/attendance/summary?month=2026-11" "/salaries?month=2026-10&size=10" "/users?page=1&size=50" "/op-logs?size=50"; do
  ts=""
  for i in 1 2 3; do
    t=$(curl -sS -b "$J" -o /dev/null -w '%{time_total}' "$B$ep")
    ts="$ts $t"
  done
  echo "  $(printf '%-42s' "$ep") 用时:$ts 秒"
done

echo
echo "=== 写操作耗时 ==="
t=$(curl -sS -b "$J" -o /dev/null -w '%{time_total}' -X POST "$B/attendance" -H 'Content-Type: application/json' -d '{"empId":7,"workDate":"2026-11-18","checkIn":"09:00:00","checkOut":"18:00:00"}')
echo "  POST /attendance            $t 秒"
AID=$(docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select att_id from ssm_emp.attendance where emp_id=7 and work_date='2026-11-18';" 2>/dev/null | tr -d ' ')
t=$(curl -sS -b "$J" -o /dev/null -w '%{time_total}' -X DELETE "$B/attendance/$AID")
echo "  DELETE /attendance/{id}     $t 秒"
t=$(curl -sS -b "$J" -o /dev/null -w '%{time_total}' -X POST "$B/salaries/generate?month=2026-12")
echo "  POST /salaries/generate     $t 秒（10 条插入）"
t=$(curl -sS -b "$J" -o /dev/null -w '%{time_total}' -X POST "$B/salaries/recalculate?month=2026-12")
echo "  POST /salaries/recalculate  $t 秒"

echo
echo "=== 连续 60 次查询（稳定性）==="
fail=0; total=0
for i in $(seq 1 60); do
  code=$(curl -sS -b "$J" -o /dev/null -w '%{http_code}' "$B/emps?page=1&size=5")
  total=$((total+1))
  [ "$code" != "200" ] && fail=$((fail+1))
done
echo "  请求 $total 次，非 200 次数：$fail"

echo
echo "=== 并发 10 个请求（连接池 20）==="
for i in $(seq 1 10); do
  curl -sS -b "$J" -o /dev/null -w "%{http_code}\n" "$B/emps?page=1&size=5" &
done
wait
echo "（以上为状态码，应全为 200）"
