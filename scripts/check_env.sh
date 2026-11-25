#!/bin/bash
echo "================ 1. APT 手动安装包 ================"
apt-mark showmanual

echo -e "\n================ 2. Snap 软件包 ================"
snap list 2>/dev/null || echo "无 Snap 软件"

echo -e "\n================ 3. SDKMAN (Java/Maven/Gradle/Kotlin等) ================"
if [ -d "$HOME/.sdkman/candidates" ]; then
  ls -d $HOME/.sdkman/candidates/*/* 2>/dev/null | grep -v 'current' | sed "s|$HOME/.sdkman/candidates/||"
else
  echo "未找到 SDKMAN 目录"
fi

echo -e "\n================ 4. Node.js 全局安装包 ================"
npm list -g --depth=0 2>/dev/null || echo "未安装 npm 或无全局包"

echo -e "\n================ 5. Python 库 (全局/用户级) ================"
python3 -m pip list 2>/dev/null || echo "无 pip 包"

echo -e "\n================ 6. Cargo (Rust) & Go 二进制工具 ================"
echo "Cargo bin: $(ls ~/.cargo/bin 2>/dev/null | tr '\n' ' ')"
echo "Go bin: $(ls ~/go/bin 2>/dev/null | tr '\n' ' ')"

echo -e "\n================ 7. 本地自定义可执行工具 (~/.local/bin) ================"
ls -1 ~/.local/bin 2>/dev/null || echo "空"

echo -e "\n================ 8. Docker 镜像与容器 ================"
echo "-- 镜像列表 --"
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" 2>/dev/null || echo "Docker 未启动或未安装"
echo -e "\n-- 容器运行状态 --"
docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "无运行容器"
