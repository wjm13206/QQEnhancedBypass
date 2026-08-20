# QQ Enhanced Bypass - 从零到打包完整教程

## 📋 目录

1. [环境准备](#环境准备)
2. [Dobby 集成](#dobby-集成)
3. [项目配置](#项目配置)
4. [编译打包](#编译打包)
5. [安装部署](#安装部署)
6. [故障排除](#故障排除)

---

## 🔧 环境准备

### 第一步：安装 JDK

#### Windows

```bash
# 1. 下载 JDK
# 访问：https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html
# 或使用 OpenJDK：https://adoptium.net/

# 2. 安装 JDK
# 双击安装包，默认安装到 C:\Program Files\Java\jdk-11

# 3. 配置环境变量
# 我的电脑 → 属性 → 高级系统设置 → 环境变量
# 
# 新建系统变量：
#   变量名：JAVA_HOME
#   变量值：C:\Program Files\Java\jdk-11
#
# 编辑 Path 变量，添加：
#   %JAVA_HOME%\bin

# 4. 验证安装
java -version
# 应该显示：java version "11.0.x"
```

#### Linux/Mac

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-11-jdk

# macOS (使用 Homebrew)
brew install openjdk@11

# 验证
java -version
```

### 第二步：安装 Android Studio

#### 下载安装

```
1. 访问：https://developer.android.com/studio
2. 下载 Android Studio（约 1GB）
3. 安装到默认位置（C:\Program Files\Android\Android Studio）
```

#### 首次启动配置

```
1. 启动 Android Studio
2. 选择 "Standard" 安装类型
3. 等待下载 SDK、模拟器等（约 3GB，需要科学上网）
4. 安装完成
```

#### 安装 NDK 和 CMake

```
1. Android Studio → Tools → SDK Manager
2. 切换到 "SDK Tools" 标签
3. 勾选以下项目：
   ☑ Android SDK Build-Tools
   ☑ NDK (Side by side)         ← 重要！
   ☑ CMake                       ← 重要！
   ☑ Android SDK Command-line Tools
4. 点击 "Apply" 开始下载（约 1-2GB）
5. 等待安装完成
```

**验证 NDK 安装**：

```bash
# Windows
dir "C:\Users\你的用户名\AppData\Local\Android\Sdk\ndk"
# 应该看到版本号文件夹，例如：25.2.9519653

# Linux/Mac
ls ~/Android/Sdk/ndk
```

### 第三步：安装 Git

#### Windows

```
1. 下载：https://git-scm.com/download/win
2. 安装时选择：
   - Use Git from Git Bash only（推荐）
   - Checkout Windows-style, commit Unix-style
3. 验证：
   git --version
```

#### Linux

```bash
sudo apt install git
```

#### Mac

```bash
# 通常已预装，如果没有：
xcode-select --install
```

### 第四步：配置 Android SDK 环境变量

#### Windows

```bash
# 设置环境变量
# 我的电脑 → 属性 → 高级系统设置 → 环境变量

# 新建系统变量：
# ANDROID_HOME
# C:\Users\你的用户名\AppData\Local\Android\Sdk

# 编辑 Path，添加：
# %ANDROID_HOME%\platform-tools
# %ANDROID_HOME%\tools
# %ANDROID_HOME%\tools\bin

# 验证
adb --version
# 应该显示 Android Debug Bridge version x.x.x
```

#### Linux/Mac

```bash
# 编辑 ~/.bashrc 或 ~/.zshrc
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools

# 重新加载
source ~/.bashrc

# 验证
adb --version
```

---

## 🔨 Dobby 集成（关键步骤）

### 方案 A：使用预编译库（推荐，最简单）

#### 1. 下载 Dobby

```bash
# 打开命令行，进入任意临时目录
cd C:\Temp

# 克隆 Dobby 仓库
git clone https://github.com/jmpews/Dobby.git

# 或者直接下载 ZIP
# https://github.com/jmpews/Dobby/archive/refs/heads/master.zip
```

#### 2. 复制文件到项目

**Windows (PowerShell 或 CMD)**：

```powershell
# 进入项目目录
cd E:\QQ_HOOK\QQEnhancedBypass

# 创建目录结构
mkdir app\src\main\cpp\dobby\include
mkdir app\src\main\cpp\dobby\lib\arm64-v8a
mkdir app\src\main\cpp\dobby\lib\armeabi-v7a

# 复制头文件
xcopy /E /I C:\Temp\Dobby\include\* app\src\main\cpp\dobby\include\

# 复制预编译库（arm64）
copy C:\Temp\Dobby\prebuilt\android\arm64-v8a\libdobby.a app\src\main\cpp\dobby\lib\arm64-v8a\

# 复制预编译库（arm32）
copy C:\Temp\Dobby\prebuilt\android\armeabi-v7a\libdobby.a app\src\main\cpp\dobby\lib\armeabi-v7a\
```

**Linux/Mac (Bash)**：

```bash
cd ~/QQ_HOOK/QQEnhancedBypass

# 创建目录
mkdir -p app/src/main/cpp/dobby/include
mkdir -p app/src/main/cpp/dobby/lib/arm64-v8a
mkdir -p app/src/main/cpp/dobby/lib/armeabi-v7a

# 复制文件
cp -r ~/Temp/Dobby/include/* app/src/main/cpp/dobby/include/
cp ~/Temp/Dobby/prebuilt/android/arm64-v8a/libdobby.a app/src/main/cpp/dobby/lib/arm64-v8a/
cp ~/Temp/Dobby/prebuilt/android/armeabi-v7a/libdobby.a app/src/main/cpp/dobby/lib/armeabi-v7a/
```

#### 3. 验证文件结构

```bash
# 检查文件是否存在
dir app\src\main\cpp\dobby\include\dobby.h
dir app\src\main\cpp\dobby\lib\arm64-v8a\libdobby.a

# 应该看到文件，不是 "找不到文件"
```

**正确的目录结构**：

```
app/src/main/cpp/dobby/
├── include/
│   ├── dobby.h            ← 必需
│   └── ... (其他头文件)
├── lib/
│   ├── arm64-v8a/
│   │   └── libdobby.a     ← 必需
│   └── armeabi-v7a/
│       └── libdobby.a     ← 必需
└── CMakeLists.txt         ← 已有
```

#### 4. 更新 Dobby CMakeLists.txt

打开 `app/src/main/cpp/dobby/CMakeLists.txt`，替换为：

```cmake
cmake_minimum_required(VERSION 3.18.1)
project(dobby)

# 使用预编译库
add_library(dobby STATIC IMPORTED)
set_target_properties(dobby PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/lib/${ANDROID_ABI}/libdobby.a
)

target_include_directories(dobby INTERFACE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

message(STATUS "Using Dobby prebuilt library: ${ANDROID_ABI}")
```

### 方案 B：如果 Dobby 不可用（降级方案）

如果无法获取 Dobby，可以临时禁用 Native Hook：

```java
// 打开 app/src/main/java/.../config/HookConfig.java
// 修改这一行：
public static boolean ENABLE_NATIVE_HOOKS = false;  // 改为 false
```

然后注释掉 Native 编译配置：

```gradle
// 打开 app/build.gradle
// 注释掉这部分：
/*
externalNativeBuild {
    cmake {
        path "src/main/cpp/CMakeLists.txt"
    }
}
*/
```

这样可以编译 Java-only 版本（30% 覆盖）。

---

## ⚙️ 项目配置

### 第一步：打开项目

```
1. 启动 Android Studio
2. File → Open
3. 选择：E:\QQ_HOOK\QQEnhancedBypass
4. 点击 "OK"
5. 等待 Gradle 同步（首次需要下载依赖，5-10分钟）
```

**Gradle 同步状态**：

- 左下角显示 "Gradle sync in progress..."
- 等待完成，显示 "Gradle sync finished"
- 如果失败，查看 "Build" 窗口的错误信息

### 第二步：检查 SDK 配置

```
1. File → Project Structure
2. SDK Location 标签
3. 确认以下路径正确：
   - Android SDK location: C:\Users\你的用户名\AppData\Local\Android\Sdk
   - Android NDK location: 自动检测
4. 点击 "OK"
```

### 第三步：配置 Gradle（可选优化）

打开 `gradle.properties`，确保有以下配置：

```properties
# 增加堆内存（避免编译时内存不足）
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8

# 启用并行编译
org.gradle.parallel=true

# 启用缓存
org.gradle.caching=true

# AndroidX
android.useAndroidX=true
android.enableJetifier=false
```

### 第四步：验证配置

在 Android Studio 底部打开 "Terminal"，运行：

```bash
# Windows
.\gradlew tasks

# Linux/Mac
./gradlew tasks

# 应该看到可用的任务列表，包括：
# assembleDebug - 编译 Debug 版本
# assembleRelease - 编译 Release 版本
```

---

## 🏗️ 编译打包

### 方法 1：使用一键脚本（推荐）

#### Windows

```bash
# 在项目根目录双击运行
build.bat

# 或在命令行运行
cd E:\QQ_HOOK\QQEnhancedBypass
.\build.bat
```

**脚本会自动**：
1. 检查 Dobby 是否存在
2. 清理旧构建
3. 编译 Release APK
4. 提示安装选项
5. 可选自动安装到设备

#### Linux/Mac

```bash
cd ~/QQ_HOOK/QQEnhancedBypass
chmod +x build.sh
./build.sh
```

### 方法 2：使用 Android Studio

```
1. Build → Clean Project（清理）
2. Build → Rebuild Project（重新构建）
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. 等待编译完成（首次约 3-5 分钟）
5. 点击通知栏的 "locate" 查看 APK
```

### 方法 3：使用命令行

```bash
# 进入项目目录
cd E:\QQ_HOOK\QQEnhancedBypass

# 清理旧构建（可选）
.\gradlew clean

# 编译 Release 版本
.\gradlew assembleRelease

# 等待完成，查找 APK
dir /S app-release.apk
```

**编译输出**：

```
> Task :app:assembleRelease
BUILD SUCCESSFUL in 2m 45s
89 actionable tasks: 89 executed
```

**APK 位置**：

```
app\build\outputs\apk\release\app-release.apk
```

### 编译时间参考

| 编译类型 | 首次 | 增量 |
|---------|------|------|
| Debug | 3-5分钟 | 30-60秒 |
| Release | 4-6分钟 | 1-2分钟 |
| Clean + Release | 5-8分钟 | - |

### 查看编译产物

```bash
# 查看 APK 信息
cd app\build\outputs\apk\release
dir

# 应该看到：
# app-release.apk（约 2-5 MB）

# 查看 APK 内容
7z l app-release.apk

# 或者
aapt dump badging app-release.apk
```

**检查 Native 库**：

```bash
# 解压 APK
7z x app-release.apk -oextracted

# 查看 so 文件
dir extracted\lib\arm64-v8a\*.so

# 应该看到：
# libnative-bypass.so（如果启用了 Native Hook）
```

---

## 📱 安装部署

### 准备设备

#### 1. 启用开发者选项

```
1. 设置 → 关于手机
2. 连续点击 "版本号" 7次
3. 返回设置，看到 "开发者选项"
```

#### 2. 启用 USB 调试

```
1. 设置 → 开发者选项
2. 启用 "USB 调试"
3. 启用 "USB 安装"（部分手机）
```

#### 3. 连接设备

```bash
# 连接 USB 线
# 手机上允许 USB 调试

# 验证连接
adb devices

# 应该看到：
# List of devices attached
# XXXXXXXX    device
```

### 安装 APK

#### 方法 1：ADB 安装（推荐）

```bash
# 安装
adb install -r app\build\outputs\apk\release\app-release.apk

# -r 参数：如果已安装则覆盖
# -d 参数：允许降级安装

# 成功输出：
# Success
```

#### 方法 2：手动安装

```bash
# 1. 推送到设备
adb push app-release.apk /sdcard/Download/

# 2. 在手机上打开文件管理器
# 3. 找到 Download/app-release.apk
# 4. 点击安装
```

#### 方法 3：Android Studio 安装

```
1. Run → Select Device
2. 选择你的设备
3. Run → Run 'app'
```

### 配置 LSPosed

#### 1. 启用模块

```
1. 打开 LSPosed 管理器
2. 点击 "模块" 标签
3. 找到 "QQ Enhanced Bypass"
4. 启用开关（变绿）
```

#### 2. 配置作用域

```
1. 点击 "QQ Enhanced Bypass" 进入详情
2. 点击 "应用作用域"
3. 搜索 "QQ"
4. 勾选 "QQ (com.tencent.mobileqq)"
5. 返回
```

#### 3. 重启 QQ

```bash
# 方法 A：使用 ADB
adb shell am force-stop com.tencent.mobileqq
adb shell am start -n com.tencent.mobileqq/.activity.SplashActivity

# 方法 B：手动重启
# 在手机上强制停止 QQ，然后重新打开
```

### 验证安装

#### 查看日志

```bash
# 实时监控日志
adb logcat -s QQEnhancedBypass QQNativeBypass

# 预期输出：
I/QQEnhancedBypass: === QQ Enhanced Bypass v2.0 ===
I/QQEnhancedBypass: Package: com.tencent.mobileqq
I/QQEnhancedBypass: [Coordinator] Initializing...
I/QQNativeBypass: === Native Bypass Library Loaded ===
I/QQNativeBypass: Installing libc hooks...
I/QQNativeBypass: Hooked fopen
I/QQNativeBypass: Hooked fgets
I/QQEnhancedBypass: Protection Level: Full (80%)
I/QQEnhancedBypass: === Initialization Complete ===
```

#### 检查模块状态

```bash
# 在 LSPosed 中查看
# 模块 → QQ Enhanced Bypass → 日志
# 应该看到最近的加载记录
```

---

## 🔧 故障排除

### 问题 1：Gradle 同步失败

**症状**：

```
Could not resolve all dependencies
Could not download xxx
```

**解决方案**：

```
1. 检查网络连接（可能需要科学上网）
2. File → Settings → Build, Execution, Deployment → Gradle
3. Gradle JDK 选择版本 11
4. 点击 "Sync Project with Gradle Files" 重试
5. 或编辑 build.gradle，添加国内镜像：

repositories {
    maven { url 'https://maven.aliyun.com/repository/google' }
    maven { url 'https://maven.aliyun.com/repository/central' }
    google()
    mavenCentral()
}
```

### 问题 2：NDK 找不到

**症状**：

```
NDK is not configured
```

**解决方案**：

```
1. Tools → SDK Manager → SDK Tools
2. 勾选 "NDK (Side by side)"
3. 点击 "Apply" 安装
4. 重启 Android Studio
```

### 问题 3：Dobby 编译错误

**症状**：

```
Could not find libdobby.a
CMake Error at dobby/CMakeLists.txt
```

**解决方案**：

```bash
# 检查文件是否存在
dir app\src\main\cpp\dobby\lib\arm64-v8a\libdobby.a

# 如果不存在，重新复制 Dobby
# 参考 "Dobby 集成" 章节

# 或临时禁用 Native Hook
# HookConfig.ENABLE_NATIVE_HOOKS = false
```

### 问题 4：内存不足

**症状**：

```
Out of memory: Java heap space
Expiring Daemon because JVM heap space is exhausted
```

**解决方案**：

```properties
# 编辑 gradle.properties
org.gradle.jvmargs=-Xmx6144m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8

# 或关闭其他应用释放内存
# 或重启电脑
```

### 问题 5：签名冲突

**症状**：

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE
Signatures do not match
```

**解决方案**：

```bash
# 先卸载旧版本
adb uninstall io.github.qqenhanced.bypass

# 然后重新安装
adb install app-release.apk
```

### 问题 6：ADB 连接失败

**症状**：

```
adb devices
List of devices attached
(空的)
```

**解决方案**：

```bash
# 1. 重启 ADB
adb kill-server
adb start-server

# 2. 检查驱动（Windows）
# 设备管理器 → 查看是否有感叹号
# 更新 USB 驱动

# 3. 更换 USB 线或 USB 口
# 4. 手机重新授权 USB 调试
```

### 问题 7：LSPosed 不生效

**症状**：

```
日志中没有 QQEnhancedBypass 的输出
```

**解决方案**：

```
1. 确认 LSPosed 正确安装
2. 在 LSPosed 中启用模块
3. 配置作用域为 QQ
4. 完全重启 QQ（不是切后台）
5. 查看 LSPosed 日志是否有错误
6. 尝试重启手机
```

### 问题 8：编译速度慢

**优化方案**：

```properties
# gradle.properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true

# 增加内存
org.gradle.jvmargs=-Xmx6144m
```

```gradle
// build.gradle
android {
    // 只编译需要的 ABI
    ndk {
        abiFilters "arm64-v8a"  // 只编译 64 位
    }
}
```

---

## 📊 编译检查清单

打包前请确认：

- [ ] JDK 已安装（java -version 正常）
- [ ] Android Studio 已安装
- [ ] NDK 和 CMake 已安装
- [ ] Dobby 已集成（或禁用 Native Hook）
- [ ] Gradle 同步成功
- [ ] 磁盘空间充足（> 5GB）
- [ ] 网络连接正常

编译后请验证：

- [ ] APK 文件存在
- [ ] APK 大小合理（2-5MB）
- [ ] 包含 libnative-bypass.so（如启用 Native）
- [ ] 可以正常安装到设备
- [ ] LSPosed 可以识别
- [ ] 日志输出正常

---

## 🎯 快速参考

### 一键命令

```bash
# Windows 完整流程
cd E:\QQ_HOOK\QQEnhancedBypass
.\gradlew clean assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk

# Linux/Mac
cd ~/QQ_HOOK/QQEnhancedBypass
./gradlew clean assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 关键路径

| 项目 | Windows 路径 |
|------|-------------|
| 项目目录 | E:\QQ_HOOK\QQEnhancedBypass |
| APK 输出 | app\build\outputs\apk\release\app-release.apk |
| Dobby 位置 | app\src\main\cpp\dobby |
| Android SDK | C:\Users\你的用户名\AppData\Local\Android\Sdk |
| Gradle 缓存 | C:\Users\你的用户名\.gradle |

### 常用命令

```bash
# 清理
.\gradlew clean

# 编译 Debug
.\gradlew assembleDebug

# 编译 Release
.\gradlew assembleRelease

# 查看任务
.\gradlew tasks

# 查看依赖
.\gradlew dependencies

# 安装
adb install -r app-release.apk

# 卸载
adb uninstall io.github.qqenhanced.bypass

# 日志
adb logcat -s QQEnhancedBypass
```

---

## ✅ 成功标志

当看到以下输出时，说明一切正常：

```
BUILD SUCCESSFUL in 2m 45s
89 actionable tasks: 89 executed

APK 位置: app\build\outputs\apk\release\app-release.apk
大小: 3.2 MB

安装成功: Success

日志输出:
I/QQEnhancedBypass: === QQ Enhanced Bypass v2.0 ===
I/QQEnhancedBypass: Protection Level: Full (80%)
```

---

**现在您可以开始编译了！推荐使用 `build.bat` 一键完成。**

有任何问题请参考 "故障排除" 章节。
