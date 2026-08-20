# 如何编译 QQ Enhanced Bypass

## 方式 1：使用 Android Studio（推荐）

```
1. 确保已安装 Android Studio + NDK + CMake
2. 克隆 Dobby 源码：
   cd app/src/main/cpp/dobby
   git clone https://github.com/jmpews/Dobby.git source

3. 用 Android Studio 打开项目
   File → Open → 选择 E:\QQ_HOOK\QQEnhancedBypass

4. 等待 Gradle 自动同步（首次需下载依赖）

5. 编译：
   Build → Build Bundle(s) / APK(s) → Build APK(s)

6. APK 位置：
   app\build\outputs\apk\release\app-release.apk
```

## 方式 2：命令行编译（需先用 Android Studio 同步一次）

```bash
# Windows
cd E:\QQ_HOOK\QQEnhancedBypass
.\build.bat

# Linux/Mac  
cd ~/QQ_HOOK/QQEnhancedBypass
./build.sh
```

## 详细文档

- 完整教程：`docs/STEP_BY_STEP_GUIDE.md`
- 快速开始：`QUICK_START.md`
- 编译指南：`docs/BUILD_GUIDE.md`
