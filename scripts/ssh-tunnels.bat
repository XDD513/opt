@echo off
setlocal
REM 强制 CMD 使用 UTF-8，减轻中文提示乱码
chcp 65001 > nul

REM SSH 本地转发 MySQL / Redis / Nacos（ECS 上仅监听 127.0.0.1 时使用）
REM 保活：减轻 NAT/防火墙空闲断线
REM
REM 使用前按需修改下面变量，或在调用前: set ECS_USER=root & set ECS_HOST=你的ECS_IP

if not defined ECS_USER set "ECS_USER=root"
if not defined ECS_HOST set "ECS_HOST=121.43.140.75"
if not defined L_MYSQL set "L_MYSQL=13306"
if not defined L_REDIS set "L_REDIS=16379"
if not defined L_NACOS set "L_NACOS=18848"

echo [%ECS_USER%@%ECS_HOST%] 转发: localhost:%L_MYSQL%^>3306, %L_REDIS%^>6379, %L_NACOS%^>8848
echo Nacos 控制台: http://127.0.0.1:%L_NACOS%/nacos/
echo 按 Ctrl+C 可结束隧道。
echo.

ssh -N ^
  -o ServerAliveInterval=60 ^
  -o ServerAliveCountMax=3 ^
  -o TCPKeepAlive=yes ^
  -o ExitOnForwardFailure=yes ^
  -o StrictHostKeyChecking=accept-new ^
  -L "%L_MYSQL%:127.0.0.1:3306" ^
  -L "%L_REDIS%:127.0.0.1:6379" ^
  -L "%L_NACOS%:127.0.0.1:8848" ^
  "%ECS_USER%@%ECS_HOST%"

endlocal
