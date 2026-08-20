# QQ Enhanced Bypass - 项目结构

## 📁 根目录文件

```
QQEnhancedBypass/
├── README.md              主说明文档（项目介绍、功能、使用说明）
├── QUICK_START.md         快速开始指南（5分钟上手）
├── build.bat              Windows 一键编译脚本
├── build.sh               Linux/Mac 一键编译脚本
├── build.gradle           项目级 Gradle 配置
├── settings.gradle        Gradle 设置
├── gradle.properties      Gradle 属性配置
└── local.properties       本地 SDK 路径配置
```

## 📁 主要目录

```
app/                       Android 应用模块
├── build.gradle           应用级构建配置
├── proguard-rules.pro     混淆规则
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/
    │   └── xposed_init    Xposed 模块入口声明
    ├── res/               Android 资源文件
    └── java/              Java 源码
        └── io/github/qqenhanced/bypass/
            ├── XposedEntry.java              主入口
            ├── NativeBypass.java             JNI 桥接
            ├── config/                       配置
            │   └── HookConfig.java           全局配置
            ├── hooks/                        Hook 模块
            │   ├── UnifiedHookCoordinator.java  统一协调器
            │   ├── NetworkReportHook.java    网络拦截
            │   ├── RootDetectionHook.java    Root 检测
            │   ├── XposedDetectionHook.java  Xposed 检测
            │   ├── DeviceInfoHook.java       设备信息
            │   ├── DebugDetectionHook.java   调试检测
            │   └── RuntimeMonitorHook.java   运行时监控
            └── utils/                        工具类
                └── HookUtils.java            Hook 工具
    └── cpp/               Native 源码（C++）
        ├── CMakeLists.txt                   主 CMake 配置
        ├── native-bypass.cpp                JNI 主入口
        ├── dobby/                           Dobby Hook 框架
        │   ├── source/                      ⚠️ 需手动克隆 Dobby 源码到这里
        │   ├── CMakeLists.txt               Dobby 包装配置
        │   └── README.md                    Dobby 集成说明
        ├── hooks/                           Native Hook 实现
        │   ├── hook_libc.cpp                文件 I/O 拦截
        │   ├── hook_libfekit.cpp            libfekit.so 绕过
        │   ├── hook_libturingxq.cpp         libturingxq.so 绕过
        │   └── hook_libmsf.cpp              libMSF 绕过
        └── utils/                           工具类
            ├── log.h/cpp                    日志工具
            └── elf_utils.h/cpp              ELF 解析工具

docs/                      详细文档目录
├── BUILD_GUIDE.md         编译打包完整指南
├── STEP_BY_STEP_GUIDE.md  从零开始的详细教程
├── INTEGRATION_GUIDE.md   Java-Native 整合架构
├── NATIVE_HOOK_GUIDE.md   Native Hook 详细说明
├── NATIVE_ANALYSIS.md     Native 检测分析
├── TECHNICAL.md           技术实现详解
├── PROJECT_SUMMARY.md     项目总结对比
├── FINAL_SUMMARY.md       完整项目总结
├── COMPLETION_REPORT.md   开发完成报告
└── CHANGELOG.md           版本更新日志
```

## 🎯 核心代码统计

| 类型 | 文件数 | 代码行数 |
|------|--------|---------|
| Java Hook | 9 个类 | ~1,500 行 |
| Native Hook | 10 个文件 | ~1,200 行 |
| 文档 | 11 份 | ~50,000 字 |
| **总计** | **30 文件** | **2,700+ 行** |

## 📚 文档阅读顺序

### 快速上手
1. `README.md` - 了解项目
2. `QUICK_START.md` - 5 分钟开始编译

### 深入学习
3. `docs/STEP_BY_STEP_GUIDE.md` - 完整的环境配置
4. `docs/BUILD_GUIDE.md` - 详细编译指南
5. `docs/INTEGRATION_GUIDE.md` - Java-Native 整合架构

### 技术研究
6. `docs/TECHNICAL.md` - 技术实现细节
7. `docs/NATIVE_HOOK_GUIDE.md` - Native Hook 原理
8. `docs/NATIVE_ANALYSIS.md` - 检测机制分析

### 项目总结
9. `docs/FINAL_SUMMARY.md` - 完整项目总结
10. `docs/PROJECT_SUMMARY.md` - 与原项目对比

## ⚠️ 重要文件

### 必须手动配置
- `app/src/main/cpp/dobby/source/` - 需克隆 Dobby 源码到这里

### 编译时自动生成
- `.gradle/` - Gradle 缓存（Android Studio 自动生成）
- `build/` - 编译输出目录
- `app/build/` - APK 输出目录

### 本地配置（不提交到 Git）
- `local.properties` - 本地 SDK 路径
- `.idea/` - Android Studio 配置

## 🚀 使用流程

```bash
1. 克隆 Dobby 源码
   cd app/src/main/cpp/dobby
   git clone https://github.com/jmpews/Dobby.git source

2. 用 Android Studio 打开项目
   File → Open → 选择 QQEnhancedBypass

3. 等待 Gradle 同步完成

4. 编译
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   
   或运行： build.bat

5. 安装
   adb install -r app/build/outputs/apk/release/app-release.apk
```

## 📦 编译产物

```
app/build/outputs/apk/release/
└── app-release.apk        打包好的 Xposed 模块（2-5 MB）
```

## 🔧 关键配置文件

| 文件 | 说明 |
|------|------|
| `HookConfig.java` | 全局功能开关 |
| `build.gradle` | 编译配置、依赖管理 |
| `AndroidManifest.xml` | 应用清单、Xposed 声明 |
| `proguard-rules.pro` | 代码混淆规则 |

---

**项目状态**：✅ v2.0 开发完成，结构已优化  
**下一步**：使用 Android Studio 打开项目，Gradle 会自动配置好一切
