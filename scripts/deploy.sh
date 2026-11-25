#!/usr/bin/env bash
set -o pipefail

TC=$(cat "$HOME/opt/tomcat9.path")
cd "$(cd "$(dirname "$0")/.." && pwd)" || exit 9

echo "=== 产物 ==="
ls -lh target/ssm-emp-mis.war

# 部署为 ROOT，使上下文路径为 "/"，接口地址与指导书的 /emps 形式一致
if [ -d "$TC/webapps/ROOT" ] && [ ! -d "$TC/webapps/ROOT.default" ]; then
  mv "$TC/webapps/ROOT" "$TC/webapps/ROOT.default"
  echo "默认 ROOT 应用已改名为 ROOT.default"
fi

rm -rf "$TC/webapps/ROOT.war" "$TC/webapps/ROOT"
cp target/ssm-emp-mis.war "$TC/webapps/ROOT.war"
echo "已部署: $TC/webapps/ROOT.war"

rm -f "$TC/logs/catalina.out"

export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.21-tem"
export CATALINA_PID="$TC/temp/tomcat.pid"
export CATALINA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED"
echo "JAVA_HOME=$JAVA_HOME"
"$TC/bin/version.sh" 2>&1 | grep -E "Server version|JVM Version"
