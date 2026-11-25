#!/usr/bin/env bash
TC=$(cat "$HOME/opt/tomcat9.path")
export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.21-tem"
cd "$TC" || exit 9
# catalina.sh run 的输出走 stdout；重定向到文件，便于事后 grep 切面日志。
# 不用启动脚本自带的 catalina.out，因为 run 模式不写那个文件。
exec bin/catalina.sh run > "$TC/logs/run.out" 2>&1
