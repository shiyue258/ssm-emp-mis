#!/usr/bin/env bash
export no_proxy='*'
export NO_PROXY='*'
B="http://127.0.0.1:8080"
J=/tmp/p3.txt
MYSQL="docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N"
rm -f "$J"
for i in $(seq 1 20); do c=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$B/health" 2>/dev/null); [ "$c" = "200" ] && break; sleep 2; done
curl -sS -c "$J" -X POST "$B/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' >/dev/null

echo "=== 1. 分配不存在的角色 ==="
curl -sS -b "$J" -X PUT "$B/users/2/roles" -H 'Content-Type: application/json' -d '[999]' | jq -c '{code,message}'

echo "=== 2. 新建角色 → 授权 → 删除，检查关联残留 ==="
curl -sS -b "$J" -X POST "$B/roles" -H 'Content-Type: application/json' -d '{"roleCode":"TMP","roleName":"临时"}' >/dev/null
TID=$($MYSQL -e "select role_id from ssm_emp.role where role_code='TMP';" 2>/dev/null | tr -d ' ')
echo "  新角色 ID: $TID"
curl -sS -b "$J" -X PUT "$B/roles/$TID/permissions" -H 'Content-Type: application/json' -d '[1,2,3]' >/dev/null
echo -n "  删除前 role_permission 行数: "
$MYSQL -e "select count(*) from ssm_emp.role_permission where role_id=$TID;" 2>/dev/null
curl -sS -b "$J" -X DELETE "$B/roles/$TID" | jq -c '{code}'
echo -n "  删除后 role_permission 行数: "
$MYSQL -e "select count(*) from ssm_emp.role_permission where role_id=$TID;" 2>/dev/null

echo "=== 3. 未登录访问角色接口 ==="
echo -n "  GET /roles 无 Cookie: HTTP "
curl -sS -o /dev/null -w '%{http_code}\n' "$B/roles"

echo "=== 4. 审计是否记录角色操作 ==="
curl -sS -b "$J" "$B/op-logs?size=4" | jq -r '.data.records[] | "  \(.operator) \(.action) \(.target) -> \(.result)"'
