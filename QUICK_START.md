# QQ Enhanced Bypass - 快速开始指南

## 🚀 5 分钟上手

### 第一步：克隆 Dobby 源码

```bash
# 进入 dobby 目录
cd E:\QQ_HOOK\QQEnhancedBypass\app\src\main\cpp\dobby

# 克隆 Dobby 源码（⚠️ 重要：官方仓库不提供预编译的 .a 文件）
git clone https://github.com/jmpews/Dobby.git source

# 验证
dir source\CMakeLists.txt
```

**为什么要这样做？**
- Dobby 官方仓库的 `git clone` **不包含** `libdobby.a`（预编译库）
- 那些是构建产物，被 `.gitignore` 排除了
- 唯一可靠的方式是把源码克隆进来，和项目一起编译

### 第二步：编译打包

```bash
# 回到项目根目录
cd E:\QQ_HOOK\QQEnhancedBypass

# 运行一键脚本（推荐）
.\build.bat

# 或手动编译
.\gradlew assembleRelease
```

### 第三步：安装

```bash
# 安装到设备
adb install -r app\build\outputs\apk\release\app-release.apk

# 在 LSPosed 中启用模块
# 作用域选择：com.tencent.mobileqq

# 重启 QQ
adb shell am force-stop com.tencent.mobileqq
adb shell am start -n com.tencent.mobileqq/.activity.SplashActivity
```

### 第四步：验证

```bash
# 查看日志
adb logcat -s QQEnhancedBypass QQNativeBypass

# 预期输出：
# I/QQEnhancedBypass: === QQ Enhanced Bypass v2.0 ===
# I/QQNativeBypass: === Native Bypass Library Loaded ===
# I/QQEnhancedBypass: Protection Level: Full (80%)
```

---

## 🔧 前置条件

如果是第一次编译 Android 项目，需要：

1. **JDK 11** - [下载](https://adoptium.net/)
2. **Android Studio** - [下载](https://developer.android.com/studio)
3. **NDK 和 CMake** - 在 Android Studio 的 SDK Manager 中安装
4. **Git** - [下载](https://git-scm.com/)

详细步骤参见：`STEP_BY_STEP_GUIDE.md`

---

## ❓ 常见问题

### Q: 为什么 git clone 下来的 Dobby 没有 libdobby.a？

**A**: 这是正常的！预编译库属于构建产物，不在 git 仓库里。

**正确做法**：
```bash
# ✅ 正确：克隆到 source/ 子目录
cd app/src/main/cpp/dobby
git clone https://github.com/jmpews/Dobby.git source

# ❌ 错误：寻找不存在的 prebuilt/ 目录
```

### Q: 编译时提示 Dobby not found？

**A**: 检查目录结构：
```bash
dir app\src\main\cpp\dobby\source\CMakeLists.txt
# 应该能找到文件
```

如果没有，说明 Dobby 没克隆对位置，重新执行第一步。

### Q: 暂时不想编译 Native，能只用 Java 层吗？

**A**: 可以！修改配置：
```java
// HookConfig.java
public static boolean ENABLE_NATIVE_HOOKS = false;
```

这样会降级到 Java-only（30% 覆盖），但能正常编译运行。

### Q: 编译很慢？

**A**: 首次编译 Dobby 源码需要 1-3 分钟，之后会有缓存。可以优化：
```gradle
// app/build.gradle
android {
    ndk {
        abiFilters "arm64-v8a"  // 只编译 64 位，加快速度
    }
}
```

---

## 📚 完整文档

- **从零开始教程**：`STEP_BY_STEP_GUIDE.md`（包含环境配置）
- **编译打包指南**：`BUILD_GUIDE.md`
- **Java-Native 整合**：`INTEGRATION_GUIDE.md`
- **Native Hook 详解**：`NATIVE_HOOK_GUIDE.md`
- **技术实现**：`TECHNICAL.md`

---

## 🎯 目录结构（正确的 Dobby 集成）

```
QQEnhancedBypass/
├── build.bat                    ← 一键编译脚本
└── app/src/main/cpp/
    └── dobby/
        ├── source/              ← Dobby 完整源码（git clone 到这里）
        │   ├── CMakeLists.txt
        │   ├── include/dobby.h
        │   └── ...
        ├── CMakeLists.txt       ← 包装脚本（已配置好）
        └── README.md
```

---

## ✅ 完成标志

当看到以下输出时，说明一切正常：

```
BUILD SUCCESSFUL in 3m 15s
Protection Level: Full (80%)
```

---

**现在可以开始了！只需两步：克隆 Dobby + 运行 build.bat**
