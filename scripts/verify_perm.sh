#!/usr/bin/env bash
export no_proxy='*'
export NO_PROXY='*'
B="http://127.0.0.1:8080"
ADM=/tmp/p_adm.txt
HR=/tmp/p_hr.txt
VW=/tmp/p_vw.txt
rm -f "$ADM" "$HR" "$VW"
for i in $(seq 1 30); do c=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$B/health" 2>/dev/null); [ "$c" = "200" ] && break; sleep 2; done
echo "服务就绪 HTTP $c"

login() { curl -sS -c "$2" -X POST "$B/login" -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"123456\"}" >/dev/null; }
login admin "$ADM"; login hr01 "$HR"; login viewer01 "$VW"

echo
echo "=== 1. /me 返回权限集合 ==="
for u in "$ADM:admin" "$HR:hr01" "$VW:viewer01"; do
  f="${u%%:*}"; n="${u##*:}"
  echo -n "  $n: "
  curl -sS -b "$f" "$B/me" | jq -c '{code, perms:(.data.permissions|length), sample:(.data.permissions|sort|.[0:3])}'
done

echo
echo "=== 2. 员工查询（都需要 emp:read）==="
for u in "$ADM:admin" "$HR:hr01" "$VW:viewer01"; do
  f="${u%%:*}"; n="${u##*:}"
  echo -n "  $n GET /emps: HTTP "
  curl -sS -b "$f" -o /dev/null -w "%{http_code}\n" "$B/emps?page=1&size=1"
done

echo
echo "=== 3. 员工新增（需要 emp:write）==="
body='{"empName":"权限测试","gender":"男","dept":"研发部","post":"x","salary":1,"hireDate":"2026-01-01","status":1}'
for u in "$ADM:admin" "$HR:hr01" "$VW:viewer01"; do
  f="${u%%:*}"; n="${u##*:}"
  echo -n "  $n POST /emps: "
  curl -sS -b "$f" -o /dev/null -w "HTTP %{http_code}\n" -X POST "$B/emps" -H 'Content-Type: application/json' -d "$body"
done

echo
echo "=== 4. 角色接口（需要 role:write，仅 ADMIN）==="
for u in "$ADM:admin" "$HR:hr01"; do
  f="${u%%:*}"; n="${u##*:}"
  echo -n "  $n GET /roles: "
  curl -sS -b "$f" -o /dev/null -w "HTTP %{http_code}\n" "$B/roles"
done

echo
echo "=== 5. 403 响应体示例（viewer 写员工）==="
curl -sS -b "$VW" -X POST "$B/emps" -H 'Content-Type: application/json' -d "$body" | jq -c '{code,message}'

echo
echo "=== 6. 管理员改 hr01 的权限后是否立即生效 ==="
HRID=$(docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select user_id from ssm_emp.sys_user where username='hr01';" 2>/dev/null | tr -d ' ')
RID=$(docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select role_id from ssm_emp.role where role_code='HR';" 2>/dev/null | tr -d ' ')
echo "  hr01 userId=$HRID roleId=$RID"
echo -n "  清空 HR 角色权限后 hr01 GET /emps: "
curl -sS -b "$ADM" -X PUT "$B/roles/$RID/permissions" -H 'Content-Type: application/json' -d '[]' >/dev/null
curl -sS -b "$HR" -o /dev/null -w "HTTP %{http_code}\n" "$B/emps?page=1&size=1"
echo -n "  恢复 HR 权限（8 个）后 hr01 GET /emps: "
PERMS=$(docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select perm_id from ssm_emp.permission where perm_code in ('emp:read','emp:write','dept:read','dept:write','att:read','att:write','sal:read','sal:write') order by perm_id;" 2>/dev/null | tr '\n' ',' | sed 's/,$//')
curl -sS -b "$ADM" -X PUT "$B/roles/$RID/permissions" -H 'Content-Type: application/json' -d "[$PERMS]" >/dev/null
curl -sS -b "$HR" -o /dev/null -w "HTTP %{http_code}\n" "$B/emps?page=1&size=1"

echo
echo "=== 7. 最后一个管理员不能被降级 ==="
AID=$(docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select user_id from ssm_emp.sys_user where username='admin';" 2>/dev/null | tr -d ' ')
echo -n "  把 admin 改成 VIEWER: "
VID=$(docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select role_id from ssm_emp.role where role_code='VIEWER';" 2>/dev/null | tr -d ' ')
curl -sS -b "$ADM" -X PUT "$B/users/$AID/roles" -H 'Content-Type: application/json' -d "[$VID]" | jq -c '{code,message}'

echo
echo "=== 8. 审计是否记录了 403 之外的写操作 ==="
docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -e "select concat('审计条数=',count(*),' 失败=',sum(result=0)) from ssm_emp.op_log;" 2>/dev/null
