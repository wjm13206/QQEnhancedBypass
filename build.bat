@echo off
chcp 65001 >nul
echo ========================================
echo   QQ Enhanced Bypass - 一键打包工具
echo ========================================
echo.

cd /d "%~dp0"

:: 检查 Gradle
if not exist "gradlew.bat" (
    echo [错误] 找不到 gradlew.bat
    echo 请确保在项目根目录运行此脚本
    pause
    exit /b 1
)

:: 检查 Dobby
echo [1/5] 检查 Dobby Hook 框架...
if not exist "app\src\main\cpp\dobby\include\dobby.h" (
    echo.
    echo [警告] Dobby 框架未找到！
    echo.
    echo 您有两个选择：
    echo   1. 继续编译 Java-only 版本（30%% 覆盖率）
    echo   2. 退出并集成 Dobby（80%% 覆盖率）
    echo.
    choice /C 12 /M "请选择"

    if errorlevel 2 (
        echo.
        echo 请按照以下步骤集成 Dobby：
        echo   1. git clone https://github.com/jmpews/Dobby.git temp_dobby
        echo   2. xcopy /E /I temp_dobby\include app\src\main\cpp\dobby\include
        echo   3. mkdir app\src\main\cpp\dobby\lib\arm64-v8a
        echo   4. copy temp_dobby\prebuilt\android\arm64-v8a\libdobby.a ...
        echo.
        echo 详见 BUILD_GUIDE.md
        pause
        exit /b 0
    )

    echo.
    echo [提示] 将编译 Java-only 版本
    echo 请确保 HookConfig.java 中 ENABLE_NATIVE_HOOKS = false
    timeout /t 3 >nul
) else (
    echo [OK] Dobby 框架已就绪
)

:: 清理
echo.
echo [2/5] 清理旧构建...
call gradlew.bat clean >nul 2>&1
if errorlevel 1 (
    echo [警告] 清理失败，继续...
) else (
    echo [OK] 清理完成
)

:: 编译 Release
echo.
echo [3/5] 编译 Release APK...
echo 这可能需要几分钟，请耐心等待...
echo.
call gradlew.bat assembleRelease

if errorlevel 1 (
    echo.
    echo [错误] 编译失败！
    echo.
    echo 常见问题：
    echo   1. NDK 未安装 - 在 Android Studio 安装 NDK
    echo   2. Dobby 缺失 - 参考 BUILD_GUIDE.md 集成
    echo   3. 内存不足 - 增大 gradle.properties 中的堆内存
    echo.
    pause
    exit /b 1
)

:: 查找 APK
echo.
echo [4/5] 查找生成的 APK...
set APK_PATH=app\build\outputs\apk\release\app-release.apk

if not exist "%APK_PATH%" (
    echo [错误] 找不到 APK 文件
    echo 预期位置: %APK_PATH%
    pause
    exit /b 1
)

for %%A in ("%APK_PATH%") do set APK_SIZE=%%~zA
set /a APK_SIZE_MB=%APK_SIZE% / 1024 / 1024
echo [OK] APK 已生成
echo     位置: %APK_PATH%
echo     大小: %APK_SIZE_MB% MB

:: 安装提示
echo.
echo [5/5] 安装选项
echo.
choice /C YN /M "是否立即安装到设备"

if errorlevel 2 goto :manual
if errorlevel 1 goto :install

:install
echo.
echo 正在安装...
adb install -r "%APK_PATH%"

if errorlevel 1 (
    echo.
    echo [错误] 安装失败
    echo.
    echo 可能原因：
    echo   1. 设备未连接 - 检查 USB 调试
    echo   2. 签名冲突 - 先卸载旧版本
    echo   3. ADB 未安装 - 安装 Android SDK Platform Tools
    echo.
    goto :manual
)

echo.
echo [成功] 安装完成！
echo.
echo 下一步：
echo   1. 打开 LSPosed 管理器
echo   2. 模块 → 勾选 "QQ Enhanced Bypass"
echo   3. 作用域 → 勾选 "QQ"
echo   4. 重启 QQ
echo.
choice /C YN /M "是否重启 QQ"

if errorlevel 2 goto :end
if errorlevel 1 (
    echo.
    echo 正在重启 QQ...
    adb shell am force-stop com.tencent.mobileqq
    timeout /t 1 >nul
    adb shell am start -n com.tencent.mobileqq/.activity.SplashActivity
    echo.
    echo QQ 已重启
    echo.
    choice /C YN /M "是否查看日志"
    if errorlevel 1 (
        echo.
        echo 正在监控日志（Ctrl+C 退出）...
        adb logcat -s QQEnhancedBypass QQNativeBypass
    )
)
goto :end

:manual
echo.
echo ========================================
echo   手动安装步骤
echo ========================================
echo.
echo 1. 将 APK 复制到设备：
echo    adb push %APK_PATH% /sdcard/
echo.
echo 2. 或使用 ADB 安装：
echo    adb install -r %APK_PATH%
echo.
echo 3. 在 LSPosed 中启用模块
echo.
echo 4. 重启 QQ
echo.

:end
echo ========================================
echo   打包完成
echo ========================================
echo.
echo APK 位置: %APK_PATH%
echo.
echo 使用说明：
echo   - 首次使用建议在测试账号上验证
echo   - 查看日志: adb logcat -s QQEnhancedBypass
echo   - 详细文档: BUILD_GUIDE.md
echo.
