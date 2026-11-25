#!/usr/bin/env bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.21-tem"
TC=$(ls -d "$HOME"/opt/apache-tomcat-9* 2>/dev/null | head -1)
if [ -z "$TC" ]; then echo "未找到 Tomcat"; exit 8; fi
echo "TC=$TC"
"$TC/bin/shutdown.sh" 2>&1 | tail -3
sleep 6
echo "=== 8080 状态 ==="
if ss -ltn 2>/dev/null | grep -q ":8080"; then
  echo "仍在监听，强制结束"
  pkill -f 'catalina.base='"$TC" 2>/dev/null
  sleep 3
  ss -ltn 2>/dev/null | grep ":8080" || echo "已释放"
else
  echo "已释放"
fi
