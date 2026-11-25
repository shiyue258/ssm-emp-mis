#!/usr/bin/env bash
# 权限模型回归（第二批）：路径冲突、外键级联、角色 CRUD、403 审计。
#
# 本脚本只使用自己创建的临时账号与角色，跑完即清理，不触碰 admin/hr01/viewer01
# 与种子角色。需要完整重置数据时用 sql/init.sql，不要依赖本脚本。
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
echo "=== 0. 清理上次残留（脚本可重复执行）==="
$MYSQL -e "
delete from ssm_emp.user_role where user_id in (select user_id from ssm_emp.sys_user where username in ('qa_perm','qa_perm2'));
delete from ssm_emp.sys_user where username in ('qa_perm','qa_perm2');
delete from ssm_emp.user_role where role_id in (select role_id from ssm_emp.role where role_code='QA_ROLE');
delete from ssm_emp.role_permission where role_id in (select role_id from ssm_emp.role where role_code='QA_ROLE');
delete from ssm_emp.role where role_code='QA_ROLE';
select concat('临时账号残留=',count(*)) from ssm_emp.sys_user where username in ('qa_perm','qa_perm2');
" 2>/dev/null

echo
echo "=== A. 路径冲突：/roles/permissions 会不会被当成 {id} ==="
printf "  GET /roles/permissions : "; curl -sS -b "$J" "$B/roles/permissions" | jq -c '{code,n:(.data|length)}'
printf "  GET /roles/1           : "; curl -sS -b "$J" "$B/roles/1" | jq -c '{code,code2:.data.roleCode}'

echo
echo "=== B. 删除带角色的账号（外键风险，用临时账号）==="
printf "  新增临时账号 qa_perm   : "; curl -sS -b "$J" -X POST "$B/users" -H 'Content-Type: application/json' \
  -d '{"username":"qa_perm","password":"qa123456","displayName":"权限验收临时账号","status":1}' | jq -c '{code}'
QAID=$($MYSQL -e "select user_id from ssm_emp.sys_user where username='qa_perm';" 2>/dev/null | tr -d ' ')
printf "  绑定 HR 角色（id=2）   : "; curl -sS -b "$J" -X PUT "$B/users/$QAID/roles" -H 'Content-Type: application/json' -d '[2]' | jq -c '{code}'
printf "  user_role 当前行数     : "; $MYSQL -e "select count(*) from ssm_emp.user_role where user_id=$QAID;" 2>/dev/null
printf "  DELETE /users/$QAID     : "; curl -sS -b "$J" -X DELETE "$B/users/$QAID" -w " HTTP %{http_code}" | head -c 200; echo
printf "  关联是否清干净         : "; $MYSQL -e "select concat('user_role=',count(*)) from ssm_emp.user_role where user_id=$QAID;" 2>/dev/null

echo
echo "=== C. 角色 CRUD 全流程（自建自删）==="
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
echo "=== F. 删除仍被账号使用的角色（临时角色 + 临时账号）==="
printf "  新增临时角色 QA_ROLE  : "; curl -sS -b "$J" -X POST "$B/roles" -H 'Content-Type: application/json' \
  -d '{"roleCode":"QA_ROLE","roleName":"权限验收临时角色","remark":"脚本自建自删"}' | jq -c '{code}'
QRID=$($MYSQL -e "select role_id from ssm_emp.role where role_code='QA_ROLE';" 2>/dev/null | tr -d ' ')
printf "  新增临时账号 qa_perm2 : "; curl -sS -b "$J" -X POST "$B/users" -H 'Content-Type: application/json' \
  -d '{"username":"qa_perm2","password":"qa123456","displayName":"权限验收临时账号2","status":1}' | jq -c '{code}'
Q2ID=$($MYSQL -e "select user_id from ssm_emp.sys_user where username='qa_perm2';" 2>/dev/null | tr -d ' ')
printf "  绑定该角色            : "; curl -sS -b "$J" -X PUT "$B/users/$Q2ID/roles" -H 'Content-Type: application/json' -d "[$QRID]" | jq -c '{code}'
printf "  删除在用角色          : "; curl -sS -b "$J" -X DELETE "$B/roles/$QRID" | jq -c '{code,message}'
printf "  先删账号（级联）      : "; curl -sS -b "$J" -X DELETE "$B/users/$Q2ID" | jq -c '{code}'
printf "  再删角色              : "; curl -sS -b "$J" -X DELETE "$B/roles/$QRID" | jq -c '{code,message}'

echo
echo "=== G. 收尾：种子数据未被改动 ==="
$MYSQL -e "select concat('user=',(select count(*) from ssm_emp.sys_user),
' role=',(select count(*) from ssm_emp.role),
' user_role=',(select count(*) from ssm_emp.user_role),
' role_perm=',(select count(*) from ssm_emp.role_permission));" 2>/dev/null
