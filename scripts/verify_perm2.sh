#!/usr/bin/env bash
export no_proxy='*'
export NO_PROXY='*'
B="http://127.0.0.1:8080"
J=/tmp/c2_adm.txt
MYSQL="docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N"
rm -f "$J"
for i in $(seq 1 20); do c=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$B/health" 2>/dev/null); [ "$c" = "200" ] && break; sleep 2; done
curl -sS -c "$J" -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' >/dev/null
echo "服务就绪"

echo
echo "=== A. 路径冲突：/roles/permissions 会不会被当成 {id} ==="
printf "  GET /roles/permissions : "; curl -sS -b "$J" "$B/roles/permissions" | jq -c '{code,n:(.data|length)}'
printf "  GET /roles/1           : "; curl -sS -b "$J" "$B/roles/1" | jq -c '{code,code2:.data.roleCode}'

echo
echo "=== B. 删除带角色的账号（外键风险）==="
printf "  hr01 当前角色数        : "; $MYSQL -e "select count(*) from ssm_emp.user_role ur join ssm_emp.sys_user u on u.user_id=ur.user_id where u.username='hr01';" 2>/dev/null
HRID=$($MYSQL -e "select user_id from ssm_emp.sys_user where username='hr01';" 2>/dev/null | tr -d ' ')
printf "  DELETE /users/$HRID      : "; curl -sS -b "$J" -X DELETE "$B/users/$HRID" -w " HTTP %{http_code}" | head -c 200; echo

echo
echo "=== C. 角色 CRUD 全流程 ==="
printf "  新增角色 FINANCE      : "; curl -sS -b "$J" -X POST "$B/roles" -H 'Content-Type: application/json' -d '{"roleCode":"FINANCE","roleName":"财务专员","remark":"只看薪资"}' | jq -c '{code,message}'
FID=$($MYSQL -e "select role_id from ssm_emp.role where role_code='FINANCE';" 2>/dev/null | tr -d ' ')
printf "  新角色 ID             : %s\n" "$FID"
printf "  分配 sal:read         : "
SID=$($MYSQL -e "select perm_id from ssm_emp.permission where perm_code='sal:read';" 2>/dev/null | tr -d ' ')
curl -sS -b "$J" -X PUT "$B/roles/$FID/permissions" -H 'Content-Type: application/json' -d "[$SID]" | jq -c '{code}'
printf "  回读权限              : "; curl -sS -b "$J" "$B/roles/$FID/permissions" | jq -c '{data}'
printf "  重名角色              : "; curl -sS -b "$J" -X POST "$B/roles" -H 'Content-Type: application/json' -d '{"roleCode":"FINANCE","roleName":"重复"}' | jq -c '{code,message}'
printf "  删除角色              : "; curl -sS -b "$J" -X DELETE "$B/roles/$FID" | jq -c '{code,message}'

echo
echo "=== D. 403 审计详情是否带权限名 ==="
curl -sS -c /tmp/c2_vw -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"viewer01","password":"123456"}' >/dev/null
curl -sS -b /tmp/c2_vw -o /dev/null -X POST "$B/emps" -H 'Content-Type: application/json' -d '{"empName":"x","gender":"男","dept":"研发部","salary":1,"hireDate":"2026-01-01","status":1}'
curl -sS -b "$J" "$B/op-logs?size=1" | jq -r '.data.records[0] | "  \(.operator) \(.action) \(.target) result=\(.result) detail=\(.detail)"'

echo
echo "=== E. /me 未登录 ==="
printf "  无 Cookie GET /me      : HTTP %s\n" "$(curl -sS -o /dev/null -w '%{http_code}' "$B/me")"

echo
echo "=== F. 删除仍被账号使用的角色 ==="
printf "  DELETE /roles/2（HR，被 hr01 使用）: "; curl -sS -b "$J" -X DELETE "$B/roles/2" | jq -c '{code,message}'
