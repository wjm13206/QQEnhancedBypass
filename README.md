# QQ Enhanced Bypass

# 本分支只针对9.3.50版本进行适配，如果使用其他版本，可以下载dexkit-dynamic-detection分支版本（可能会有部分bypass失效）
一个全面的 QQ 环境检测绕过模块，基于对 QQ 检测机制的深度分析开发。面向研究目标 QQ 9.3.50 (com.tencent.mobileqq)。

本项目经过多轮测试，已没有太大问题，如过账号还是频繁掉线，可能是账号风控，请尝试QQ会员解决（

## 项目特点

相比原始的 QQNTHookBypass 项目，本模块提供：

### 1. 更广泛的检测覆盖
- **网络上报拦截**：拦截多种上报命令，不仅限于 `trpc.o3.report.*`
- **Root 检测绕过**：覆盖 6 个独立的 Java 层 Root 检测实现
- **Xposed 检测缓解**：隐藏 Xposed/LSPosed 包名、过滤 maps 文件、清理堆栈跟踪
- **设备信息一致性保护**：确保多路径 API 返回一致值，对抗交叉验证
- **调试检测绕过**：伪造 TracerPid、系统属性、应用标志
- **Runtime 监控绕过**：禁用 Pandora 的命令执行监控

### 2. 模块化架构
每个检测类别独立实现，便于：
- 单独开关功能模块
- 针对性调试和优化
- 版本更新时快速定位失效模块

### 3. 配置系统
通过 `HookConfig` 类集中管理：
- 功能开关（可选择性启用/禁用模块）
- 设备信息伪造配置
- 详细日志开关

## 检测覆盖范围

### Java 层绕过（已实现）

#### 1. 网络上报拦截 (NetworkReportHook)
- `ChannelProxyExt.sendMessage/sendMessageInner`
- `MsfCore.sendSsoMsg`
- TuringFD 风控上报
- Wlogin 设备指纹上报

拦截命令：
- `trpc.o3.report.*`
- `trpc.o3.mobile_security.*`
- `OidbSvc.0xd79` (设备上报)
- `wtlogin.device_lock`
- `turing` / `riskCheckWup`

#### 2. Root 检测绕过 (RootDetectionHook)
- `oicq.wlogin_sdk.request.w.h()` - Wlogin SDK
- `com.tencent.gathererga.core.UserInfoImpl.isRooted()`
- `org.light.device.LightDeviceUtils.isRooted()`
- `com.tencent.bugly.proguard.cp` - Bugly
- `com.tenpay.charge.v2.util.ChargeV2Utils.isDeviceRooted()`
- `com.tencent.camerasdk.avreport.DeviceInfo`
- `File.exists()` - 隐藏 su 路径
- `Runtime.exec()` - 阻止命令检测
- `PackageManager` - 隐藏 Root 管理器

#### 3. Xposed 检测缓解 (XposedDetectionHook)
- `com.tencent.qqperf.monitor.crash` - 崩溃报告中的 Xposed 标记
- `PackageManager` - 隐藏 Xposed 相关包名
- `BufferedReader.readLine()` - 过滤 /proc/self/maps 中的 hook 库
- `File.exists()` - 隐藏 Xposed 工件
- `Throwable.getStackTrace()` - 清理堆栈跟踪
- `ClassLoader.toString()` - 清理类加载器信息

#### 4. 设备信息一致性保护 (DeviceInfoHook)
多路径 API 统一返回值，对抗交叉验证：
- `TelephonyManager.getImei/getDeviceId/getMeid`
- `TelephonyManager.getSimSerialNumber`
- `Build.getSerial()`
- `SystemProperties.get()` (ro.boot.serialno, gsm.serial)
- `Settings.Secure.getString()` (android_id)
- Pandora `DeviceInfoMonitor` 监控点
- Pandora `MonitorReporter` 异常上报

#### 5. 调试检测绕过 (DebugDetectionHook)
- `BufferedReader.readLine()` - 伪造 TracerPid 为 0
- TuringFD 系列 TracerPid 检查
- `SystemProperties` - ro.debuggable=0, ro.secure=1
- `ApplicationInfo.flags` - 移除 FLAG_DEBUGGABLE
- 模拟器检测 - ro.kernel.qemu, goldfish 等

#### 6. Runtime 监控绕过 (RuntimeMonitorHook)
- Pandora `RuntimeMonitor` 全部拦截
- `IPProcessor` / `PackageManagerProcessor` / `PropProcessor`
- `ProcessBuilder.start()` 监控

### Native 层检测（本模块无法处理）

以下检测在 Native 层实现，需要 native hook 方案：

#### libfekit.so
- `/proc/self/maps` 扫描（lsposed, libriru, zygisk, frida）
- Magisk/KernelSU 特征检测
- ART CheckJNI 函数表完整性校验
- Root 管理器包名黑名单（10+个）
- 命令执行：`cat /proc/mounts | grep magisk`

#### libturingxq.so
- ART 反射检测（ArtMethod.entry_point 完整性）
- ArtField 结构校验
- 传感器数据模拟器检测

#### libMSFKernel.so / libmsfbootV2.so
- APK 签名 MD5 校验（阻断型）

## 安装与使用

### 前置条件
- Root 设备（Magisk / KernelSU）
- LSPosed 或 EdXposed 框架
- Android 8.0+ (API 26+)
- 目标：QQ (com.tencent.mobileqq)

### 编译
```bash
./gradlew assembleRelease
```

生成的 APK：`app/build/outputs/apk/release/app-release.apk`

### 安装
1. 安装生成的 APK
2. 在 LSPosed 管理器中启用模块
3. 勾选作用域：`com.tencent.mobileqq`
4. 重启 QQ

### 配置

编辑 `HookConfig.java` 自定义行为：

```java
// 功能开关
public static boolean ENABLE_ROOT_BYPASS = true;
public static boolean ENABLE_XPOSED_BYPASS = true;
public static boolean ENABLE_DEBUG_BYPASS = true;
public static boolean ENABLE_DEVICE_SPOOF = true;
public static boolean ENABLE_NETWORK_INTERCEPT = true;
public static boolean ENABLE_RUNTIME_BYPASS = true;

// 详细日志
public static boolean VERBOSE_LOGGING = true;

// 设备信息伪造（null = 使用真实值）
public static String FAKE_IMEI = null;
public static String FAKE_ANDROID_ID = null;
public static String FAKE_SERIAL = null;
```

## 局限性

### 1. Native 层检测无法绕过
本模块仅处理 Java 层检测。Native 库（libfekit.so, libturingxq.so）的检测需要：
- Frida/Dobby 等 native hook 框架
- 内存补丁
- 二进制修改

### 2. 服务端风控决策
客户端绕过只是第一步，腾讯服务端会综合判断：
- 历史行为模式
- 设备指纹变化
- 多维度风险评分
- IP/网络环境

### 3. 版本兼容性
QQ 频繁更新，类名/方法名可能变化。本模块基于以下分析开发：
- 分析时间：2026-08-07
- 目标版本：QQNT

新版本可能需要更新 hook 目标。

### 4. LSPosed 自身检测
本模块试图隐藏 LSPosed，但 native 层检测（maps 扫描、ArtMethod 校验）仍然有效。最佳实践：
- 使用 Zygisk 模式（相对隐蔽）
- 配合 Shamiko 等隐藏模块
- 考虑使用专门的 native hook 方案

## 技术细节

### 多层防御策略

#### 第一层：网络拦截（最高优先级）
即使检测执行，也阻止结果上报到服务器

#### 第二至六层：检测结果篡改
让检测返回"正常"结果

#### 第七层：监控框架禁用
禁用 Pandora 等监控框架，减少检测触发

### 交叉验证对抗

Pandora 的 `DeviceInfoMonitor` 使用 6+ 种方法获取同一设备信息：
```
getImei() → 方法A
getDeviceId() → 方法B
getSerial() → 方法C
SystemProperties → 方法D
Subscription → 方法E
反射内部字段 → 方法F
```

本模块 hook 所有路径，确保返回一致的值，防止交叉验证发现矛盾。

### Maps 文件过滤

Native 检测通过读取 `/proc/self/maps` 查找 hook 库特征：
```
lsposed, libriru, zygisk, frida, libsubstrate...
```

本模块 hook `BufferedReader.readLine()` 尝试过滤这些行，但：
- 有效性依赖于 Java 层读取方式
- Native 层直接 `fopen()` 无法拦截

## 进阶方案

要实现更完整的绕过，需要组合使用：

### 1. Native Hook 层
使用 Frida/Dobby hook native 函数：
- `fopen/fgets` - 过滤 maps/status 读取
- `dlopen/dlsym` - 隐藏注入库
- libfekit 的检测函数 - 直接返回安全值
- libturingxq 的风控函数 - 阻止上报

### 2. 内核层隐藏
- Magisk Zygisk：进程隔离
- KernelSU：内核级权限管理
- Shamiko：隐藏 Magisk 自身

### 3. 虚拟化方案
- VirtualXposed（已过时）
- 太极/无极（兼容性问题）
- Patch 版 QQ（风险高）

## 开发路线图

- [x] Java 层多模块 hook 实现
- [ ] 添加 native hook 支持（JNI 层）
- [ ] 设备指纹一致性增强
- [ ] 签名校验绕过（实验性）
- [ ] 自动化测试框架
- [ ] 版本适配检测工具
- [ ] 配置文件支持（无需重新编译）

## 免责声明

本项目仅供安全研究和学习使用。使用本模块可能：
- 违反 QQ 服务条款
- 导致账号被封禁
- 触发额外的安全审查

**请在测试环境中使用，风险自负。**

## 参考资料

本项目基于对 QQ 检测机制的深度分析，详见：
- `E:\QQ_HOOK\1.txt` - QQ 检测机制全景分析报告
- `E:\QQ_HOOK\项目总结.txt` - 原项目能力边界分析

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request。

特别需要：
- 新版本 QQ 的类名/方法名更新
- Native hook 实现方案
- 效果测试反馈
