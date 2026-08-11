@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo   hi Agent - 本地一键启动
echo   (PostgreSQL + RabbitMQ 用 Docker, 后端+前端跑在 Windows)
echo ============================================
echo.

rem ---------- 后端环境变量 ----------
set "DB_HOST=localhost"
set "DB_PORT=5432"
set "DB_NAME=agentx"
set "DB_USER=agentx_user"
set "DB_PASSWORD=agentx_pass"
set "RABBITMQ_HOST=localhost"
set "RABBITMQ_PORT=5672"
set "SPRING_PROFILES_ACTIVE=dev"
set "JPA_DDL_AUTO=update"
set "AGENTX_CONTAINER_DOCKER_HOST=npipe:////./pipe/docker_engine"

echo [0/4] 清理可能占用端口的旧发布镜像容器...
docker rm -f agentx >nul 2>&1

echo [1/4] 启动 PostgreSQL + RabbitMQ (Docker)...
cd /d "%~dp0deploy"
docker compose --profile local --profile dev up -d postgres rabbitmq

echo [2/4] 启动后端 (http://localhost:8088/api)...
start "hi Agent Backend" cmd /k "cd /d %~dp0AgentX && call mvnw.cmd -o spring-boot:run -Dspring-boot.run.profiles=dev"

echo [3/4] 启动前端 (http://localhost:3000)...
start "hi Agent Frontend" cmd /k "cd /d %~dp0agentx-frontend-plus && call npm run dev"

echo.
echo ============================================
echo   完成!浏览器打开: http://localhost:3000
echo   登录账号: admin@agentx.ai   密码: admin123
echo ============================================
pause
