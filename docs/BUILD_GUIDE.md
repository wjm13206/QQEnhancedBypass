# QQ Enhanced Bypass - 编译打包完整指南

## 📦 打包流程

### 前置准备

#### 1. 开发环境

```bash
# 必需软件
✅ Android Studio (2023.1+)
✅ JDK 8 或 11
✅ Android SDK (API 34)
✅ Android NDK (r21+)
✅ Git

# 检查环境
java -version        # 应显示 JDK 版本
adb version          # 应显示 ADB 版本
```

#### 2. 集成 Dobby Hook 框架（重要！）

⚠️ **注意**：Dobby 官方仓库不提供预编译的 `.a` 文件（`git clone` 下来是没有 `libdobby.a` 的，那属于构建产物）。唯一可靠的方式是把 Dobby **源码**克隆进来，和本项目一起从源码编译。

**方式 A：源码编译（推荐，唯一稳定方案）**

```bash
cd E:\QQ_HOOK\QQEnhancedBypass\app\src\main\cpp\dobby

# 将 Dobby 完整源码克隆到 source/ 子目录
git clone https://github.com/jmpews/Dobby.git source

# 验证
dir source\CMakeLists.txt
```

之后直接编译即可，本项目的 `dobby/CMakeLists.txt` 会自动通过 `add_subdirectory()` 从源码构建出 `dobby` 目标，无需再手动配置头文件路径或库路径。

**方式 B：仅 Java 层（跳过 Native）**

如果暂时不需要 Native Hook（降级到 30% 覆盖）：

```java
// 修改 HookConfig.java
public static boolean ENABLE_NATIVE_HOOKS = false;
```

然后注释掉 `app/build.gradle` 中的 `externalNativeBuild` 配置。

---

## 🛠️ 编译步骤

### 方法 1：命令行编译（推荐）

```bash
# 1. 进入项目目录
cd E:\QQ_HOOK\QQEnhancedBypass

# 2. 清理旧构建
.\gradlew clean

# 3. 编译 Release 版本
.\gradlew assembleRelease

# 4. 查找生成的 APK
dir /S app-release.apk
# 通常在：app\build\outputs\apk\release\app-release.apk
```

**预期输出**：
```
BUILD SUCCESSFUL in 2m 35s
45 actionable tasks: 45 executed
```

### 方法 2：Android Studio 编译

```
1. 打开 Android Studio
2. File → Open → 选择 E:\QQ_HOOK\QQEnhancedBypass
3. 等待 Gradle 同步完成
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. 点击通知栏的 "locate" 查看生成的 APK
```

### 方法 3：仅 Java 层快速编译

如果 Native 编译失败，可以临时禁用：

```gradle
// 在 app/build.gradle 中注释掉
// externalNativeBuild {
//     cmake {
//         path "src/main/cpp/CMakeLists.txt"
//     }
// }
```

```java
// HookConfig.java
public static boolean ENABLE_NATIVE_HOOKS = false;
```

然后重新编译：
```bash
.\gradlew assembleRelease
```

---

## 🔍 编译问题排查

### 问题 1：Dobby 找不到

**错误**：
```
CMake Error: Cannot find Dobby library
```

**解决**：
```bash
# 检查 Dobby 是否存在
dir app\src\main\cpp\dobby\include\dobby.h
dir app\src\main\cpp\dobby\lib\arm64-v8a\libdobby.a

# 如果不存在，重新集成 Dobby（见前置准备）
```

### 问题 2：NDK 版本不匹配

**错误**：
```
NDK is not configured
```

**解决**：
```
1. Android Studio → Tools → SDK Manager
2. SDK Tools 标签
3. 勾选 "NDK (Side by side)" 和 "CMake"
4. 点击 Apply 安装
```

### 问题 3：内存不足

**错误**：
```
Out of memory: Java heap space
```

**解决**：
```properties
# 修改 gradle.properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

### 问题 4：签名错误

**错误**：
```
Failed to read key from keystore
```

**解决**：
Release 版本会自动生成 debug 签名，无需配置。
如果需要自定义签名，参考下面的签名配置。

---

## 🔐 签名配置（可选）

### 生成签名密钥

```bash
# 生成 keystore
keytool -genkey -v -keystore qq-bypass.keystore -alias qq-bypass -keyalg RSA -keysize 2048 -validity 10000

# 输入密码和信息
# 记住密码：StorePassword 和 KeyPassword
```

### 配置签名

```gradle
// 在 app/build.gradle 中添加
android {
    signingConfigs {
        release {
            storeFile file("../qq-bypass.keystore")
            storePassword "your_store_password"
            keyAlias "qq-bypass"
            keyPassword "your_key_password"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

**注意**：QQ 会检测签名，使用自定义签名可能导致启动失败。
建议使用 debug 签名进行测试。

---

## 📱 安装部署

### 1. 安装到设备

```bash
# 通过 ADB 安装
adb install app\build\outputs\apk\release\app-release.apk

# 如果已安装旧版本
adb install -r app\build\outputs\apk\release\app-release.apk

# 强制覆盖安装
adb install -r -d app\build\outputs\apk\release\app-release.apk
```

### 2. LSPosed 激活

```
1. 打开 LSPosed 管理器
2. 模块 → 勾选 "QQ Enhanced Bypass"
3. 作用域 → 勾选 "QQ (com.tencent.mobileqq)"
4. 重启 QQ 应用（或系统）
```

### 3. 验证安装

```bash
# 查看日志
adb logcat -s QQEnhancedBypass QQNativeBypass

# 预期输出
I/QQEnhancedBypass: === QQ Enhanced Bypass v2.0 ===
I/QQEnhancedBypass: [Coordinator] Initializing...
I/QQNativeBypass: === Native Bypass Library Loaded ===
I/QQEnhancedBypass: Protection Level: Full (80%)
```

### 4. 测试功能

```bash
# 测试 Root 隐藏
adb shell su -c "ls /system/bin/su"
# QQ 应该检测不到

# 测试日志
adb logcat | grep "Blocked"
# 应该看到拦截日志
```

---

## 📦 打包不同版本

### Debug 版本（开发调试）

```bash
# 包含调试符号，体积较大
.\gradlew assembleDebug

# 生成位置
app\build\outputs\apk\debug\app-debug.apk
```

**特点**：
- ✅ 可调试
- ✅ 详细日志
- ❌ 体积大
- ❌ 性能略差

### Release 版本（正式发布）

```bash
# 优化混淆，体积小
.\gradlew assembleRelease

# 生成位置
app\build\outputs\apk\release\app-release.apk
```

**特点**：
- ✅ 体积小
- ✅ 性能好
- ✅ 代码混淆
- ❌ 难以调试

### Java-Only 版本（无 Native）

```bash
# 1. 禁用 Native
# HookConfig.ENABLE_NATIVE_HOOKS = false
# 注释掉 app/build.gradle 中的 externalNativeBuild

# 2. 编译
.\gradlew assembleRelease

# 特点：30% 覆盖，但编译快
```

### Full 版本（Java + Native）

```bash
# 确保 Dobby 已集成
# ENABLE_NATIVE_HOOKS = true

# 编译
.\gradlew assembleRelease

# 特点：80% 覆盖，完整功能
```

---

## 📊 APK 分析

### 查看 APK 信息

```bash
# APK 大小
dir app\build\outputs\apk\release\app-release.apk

# APK 内容
7z l app\build\outputs\apk\release\app-release.apk

# 预期结构
lib/
  arm64-v8a/
    libnative-bypass.so   # Native 模块（约 500KB）
  armeabi-v7a/
    libnative-bypass.so
classes.dex              # Java 代码（约 200KB）
```

### 检查 Native 库

```bash
# 提取 APK
7z x app-release.apk -oextracted

# 查看 so 文件
dir extracted\lib\arm64-v8a\*.so

# 应该看到
libnative-bypass.so
```

### 验证混淆

```bash
# 查看混淆映射
type app\build\outputs\mapping\release\mapping.txt

# Java 类名应该被混淆成 a, b, c 等
```

---

## 🚀 一键打包脚本

### build.bat (Windows)

```batch
@echo off
echo ================================
echo  QQ Enhanced Bypass Builder
echo ================================

echo [1/4] Cleaning...
call gradlew.bat clean

echo [2/4] Checking Dobby...
if not exist "app\src\main\cpp\dobby\include\dobby.h" (
    echo [WARNING] Dobby not found, will build Java-only version
    echo Set ENABLE_NATIVE_HOOKS = false in HookConfig.java
    pause
)

echo [3/4] Building Release APK...
call gradlew.bat assembleRelease

echo [4/4] Done!
echo.
echo APK location:
dir /B app\build\outputs\apk\release\app-release.apk

echo.
echo Install with:
echo adb install -r app\build\outputs\apk\release\app-release.apk

pause
```

### build.sh (Linux/Mac)

```bash
#!/bin/bash
echo "================================"
echo " QQ Enhanced Bypass Builder"
echo "================================"

echo "[1/4] Cleaning..."
./gradlew clean

echo "[2/4] Checking Dobby..."
if [ ! -f "app/src/main/cpp/dobby/include/dobby.h" ]; then
    echo "[WARNING] Dobby not found, will build Java-only version"
    echo "Set ENABLE_NATIVE_HOOKS = false in HookConfig.java"
    read -p "Press Enter to continue..."
fi

echo "[3/4] Building Release APK..."
./gradlew assembleRelease

echo "[4/4] Done!"
echo
echo "APK location:"
find app/build/outputs/apk/release -name "*.apk"

echo
echo "Install with:"
echo "adb install -r app/build/outputs/apk/release/app-release.apk"
```

---

## 📋 打包清单

### 打包前检查

- [ ] Dobby 已集成（如需 Native）
- [ ] HookConfig 配置正确
- [ ] build.gradle 配置正确
- [ ] 已执行 gradlew clean
- [ ] 磁盘空间充足（> 2GB）

### 打包后验证

- [ ] APK 文件存在
- [ ] APK 大小合理（2-5MB）
- [ ] 包含 libnative-bypass.so（如启用 Native）
- [ ] 可以正常安装
- [ ] LSPosed 可以识别
- [ ] 日志显示正常

---

## 🎯 快速打包指令

### 最简单方式（推荐）

```bash
# 一条命令完成
cd E:\QQ_HOOK\QQEnhancedBypass && .\gradlew clean assembleRelease
```

### 完整流程

```bash
# 1. 进入目录
cd E:\QQ_HOOK\QQEnhancedBypass

# 2. 清理
.\gradlew clean

# 3. 编译
.\gradlew assembleRelease

# 4. 安装
adb install -r app\build\outputs\apk\release\app-release.apk

# 5. 重启 QQ
adb shell am force-stop com.tencent.mobileqq
adb shell am start -n com.tencent.mobileqq/.activity.SplashActivity

# 6. 查看日志
adb logcat -s QQEnhancedBypass
```

---

## 💡 提示

### 首次编译

首次编译会下载依赖，耗时较长（5-10 分钟），请耐心等待。

### 增量编译

修改代码后，直接 `gradlew assembleRelease` 即可，无需 clean。

### 测试建议

1. 先编译 Debug 版本测试
2. 确认功能正常后编译 Release
3. 在测试账号上验证
4. 不要在主力账号使用

### 备份

打包前建议备份项目：
```bash
xcopy /E /I QQEnhancedBypass QQEnhancedBypass_backup
```

---

## ❓ 常见问题

**Q: 编译很慢？**  
A: 首次编译需下载依赖，后续会快很多。

**Q: 找不到 APK？**  
A: 检查 `app\build\outputs\apk\release\` 目录。

**Q: Native 编译失败？**  
A: 临时禁用 Native（ENABLE_NATIVE_HOOKS = false）。

**Q: 安装失败？**  
A: 检查签名冲突，卸载旧版本后重试。

**Q: LSPosed 不识别？**  
A: 检查 AndroidManifest.xml 的 xposedmodule 配置。

---

## 📞 支持

遇到问题？

1. 查看编译日志：`.\gradlew assembleRelease --stacktrace`
2. 检查 logcat：`adb logcat *:E`
3. 参考文档：`TECHNICAL.md`、`INTEGRATION_GUIDE.md`

---

**打包完成后的 APK 即可直接使用！**

记得在 LSPosed 中启用模块并重启 QQ。
