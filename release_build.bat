@echo off
chcp 65001 >nul
echo ============================================
echo   HypnoVibe Release APK 构建脚本
echo ============================================

:: 代理设置
set http_proxy=http://127.0.0.1:7890
set https_proxy=http://127.0.0.1:7890

:: JAVA_HOME（Android Studio 自带 JDK，支持 jlink）
set JAVA_HOME=E:\Android\Android Studio\jbr

:: 清理旧 APK
if exist "app\release\HypnoVibe.apk" (
    echo [INFO] 删除旧版本 HypnoVibe.apk
    del /q "app\release\HypnoVibe.apk"
)

:: 执行 release 构建
echo [INFO] 开始编译 release APK...
call .\gradlew assembleRelease --no-configuration-cache
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 构建失败！
    pause
    exit /b 1
)

:: 复制并重命名 APK
set SRC=app\build\outputs\apk\release\app-release.apk
set DST=app\release\HypnoVibe.apk

if not exist "%SRC%" (
    echo [ERROR] 找不到构建产物: %SRC%
    pause
    exit /b 1
)

:: 确保目标目录存在
if not exist "app\release" mkdir "app\release"

copy /y "%SRC%" "%DST%"
if %ERRORLEVEL% EQU 0 (
    echo [INFO] APK 已输出至: %DST%
) else (
    echo [ERROR] 复制失败！
    pause
    exit /b 1
)

echo ============================================
echo   构建完成！
echo ============================================
pause
