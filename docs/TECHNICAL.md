# QQ Enhanced Bypass 技术实现详解

## 架构设计

### 1. 模块化设计原则

```
XposedEntry (主入口)
    ├── HookConfig (配置管理)
    ├── HookUtils (工具类)
    └── Hooks (钩子模块)
        ├── NetworkReportHook (网络拦截)
        ├── RootDetectionHook (Root检测)
        ├── XposedDetectionHook (Xposed检测)
        ├── DeviceInfoHook (设备信息)
        ├── DebugDetectionHook (调试检测)
        └── RuntimeMonitorHook (运行时监控)
```

### 2. 初始化流程

```java
handleLoadPackage()
  ↓
检查目标包名 (com.tencent.mobileqq)
  ↓
初始化 HookConfig
  ↓
按优先级加载 Hook 模块
  ↓
Layer 1: NetworkReportHook (最高优先级 - 阻止上报)
Layer 2: RootDetectionHook (篡改检测结果)
Layer 3: XposedDetectionHook (隐藏框架)
Layer 4: DeviceInfoHook (一致性保护)
Layer 5: DebugDetectionHook (反调试)
Layer 6: RuntimeMonitorHook (监控绕过)
```

### 3. Hook 策略

#### 防御深度优先级
1. **网络层拦截**（最重要）：即使检测执行也阻止上报
2. **检测点篡改**：让检测返回安全结果
3. **监控禁用**：减少检测触发频率

#### 容错设计
- 所有 hook 操作包裹在 try-catch 中
- 单个模块失败不影响其他模块
- 可通过配置单独禁用问题模块

## 核心技术细节

### 1. 网络上报拦截 (NetworkReportHook)

#### 拦截点 A: ChannelProxyExt
```java
// QQ 使用 ChannelProxyExt 发送 protobuf 消息到服务器
// 签名：sendMessage(String command, byte[] data, long sequence)

beforeHookedMethod() {
    String command = param.args[0];
    
    if (command.contains("trpc.o3.report")) {
        // 1. 阻止原始发送
        param.setResult(null);
        
        // 2. 注入伪造响应（可选）
        ChannelManager.getInstance()
            .onNativeReceive(command, new byte[0], 0L);
    }
}
```

#### 拦截点 B: MsfCore
```java
// MSF (Mobile Service Framework) 是底层通信框架
// ToServiceMsg 封装了服务命令

beforeHookedMethod() {
    ToServiceMsg msg = param.args[0];
    String command = msg.getServiceCmd();
    
    if (shouldBlock(command)) {
        // 返回序列号模拟成功
        int seq = msg.getRequestSsoSeq();
        param.setResult(seq);
    }
}
```

#### 拦截点 C: TuringFD 风控
```java
// TuringFD 是腾讯天御风控 SDK
// 关键方法通常命名为单字母（混淆后）

hookAllMethods("com.tencent.turingfd.sdk.xq.Pomegranate", "a", 
    new XC_MethodHook() {
        beforeHookedMethod() {
            // 阻止所有 TuringFD 操作
            param.setResult(null);
        }
    });
```

### 2. Root 检测绕过 (RootDetectionHook)

#### 检测方法 1: 文件路径检查
```java
// QQ 检查常见 su 路径
String[] suPaths = {
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/data/local/su",
    ...
};

// 绕过方法：Hook File.exists()
File.exists() {
    if (isSuspiciousPath(this.getAbsolutePath())) {
        return false;  // 隐藏 su 文件
    }
}
```

#### 检测方法 2: 命令执行
```java
// QQ 执行命令查找 su
Runtime.exec("which su")
Runtime.exec("cat /proc/mounts | grep magisk")

// 绕过方法：Hook Runtime.exec()
Runtime.exec(String command) {
    if (command.contains("su") || command.contains("magisk")) {
        // 返回空结果进程
        return new ProcessBuilder("echo").start();
    }
}
```

#### 检测方法 3: 包管理器查询
```java
// QQ 查询 Root 管理器 APK
PackageManager.getPackageInfo("com.topjohnwu.magisk")
PackageManager.getPackageInfo("eu.chainfire.supersu")

// 绕过方法：Hook PackageManager
getPackageInfo(String packageName) {
    if (isRootManagerPackage(packageName)) {
        throw new NameNotFoundException();
    }
}
```

### 3. Xposed 检测缓解 (XposedDetectionHook)

#### 检测方法 1: 包名检查
```java
// QQPerf 崩溃监控检查 Xposed Installer
PackageManager.getPackageInfo("de.robv.android.xposed.installer")

// 绕过：抛出 NameNotFoundException
```

#### 检测方法 2: /proc/self/maps 扫描
```bash
# Native 层读取 maps 查找特征库
cat /proc/self/maps | grep -E "lsposed|xposed|riru|zygisk"
```

```java
// Java 层绕过（有限）：Hook BufferedReader.readLine()
BufferedReader.readLine() {
    String line = originalReadLine();
    
    if (line.contains("lsposed") || line.contains("xposed")) {
        // 跳过这一行
        return readLine();  // 递归读下一行
    }
    return line;
}
```

**注意**：Native 层直接 `fopen()` 无法被 Java hook 拦截！

#### 检测方法 3: 堆栈跟踪分析
```java
// QQ 分析异常堆栈查找 Xposed 痕迹
Throwable.getStackTrace() {
    StackTraceElement[] stack = originalGetStackTrace();
    
    // 过滤掉包含 "xposed" 的帧
    return Arrays.stream(stack)
        .filter(e -> !e.getClassName().contains("xposed"))
        .toArray(StackTraceElement[]::new);
}
```

### 4. 设备信息一致性保护 (DeviceInfoHook)

#### Pandora 交叉验证机制

```java
// Pandora 用多种方法获取 IMEI
String imei1 = TelephonyManager.getImei();
String imei2 = TelephonyManager.getDeviceId();
String imei3 = SystemProperties.get("gsm.imei");
String imei4 = reflectInternalField();

// 比对一致性
if (imei1 != imei2 || imei2 != imei3) {
    // 检测到 Hook！上报异常
    MonitorReporter.report("device_inconsistency");
}
```

#### 对抗策略：统一返回值

```java
// 使用缓存保证一致性
private static String cachedImei = null;

// 首次调用时获取真实值（或使用配置的伪造值）
private static String getConsistentImei() {
    if (cachedImei == null) {
        cachedImei = HookConfig.FAKE_IMEI != null 
            ? HookConfig.FAKE_IMEI 
            : getRealImei();
    }
    return cachedImei;
}

// Hook 所有获取路径
TelephonyManager.getImei() -> getConsistentImei()
TelephonyManager.getDeviceId() -> getConsistentImei()
SystemProperties.get("gsm.imei") -> getConsistentImei()
```

#### 阻止异常上报

```java
// 即使检测到不一致，也阻止上报
MonitorReporter.report() {
    // 直接返回，不上报
    return;
}

MonitorReporter.getStrategyAndReport() {
    // 返回"不检测"策略
    return 0;
}
```

### 5. 调试检测绕过 (DebugDetectionHook)

#### TracerPid 检测

```bash
# /proc/self/status 包含调试信息
$ cat /proc/self/status
Name:   com.tencent.mobileqq
TracerPid:  1234  # 非0表示被调试
```

```java
// 5+ 个类独立检测 TracerPid
com.tencent.turingfd.sdk.xq.Pomegranate.b()
com.tencent.turingcam.oqKCa.b()
com.tencent.tfd.sdk.wxa.Blueberry.b()

// 绕过：伪造文件内容
BufferedReader.readLine() {
    String line = originalReadLine();
    
    if (line.startsWith("TracerPid:")) {
        return "TracerPid:\t0";  // 总是返回0
    }
    return line;
}
```

#### 系统属性检测

```java
// 检测 debug 属性
SystemProperties.get("ro.debuggable")  // 应为 "0"
SystemProperties.get("ro.secure")      // 应为 "1"

// 绕过：Hook SystemProperties
SystemProperties.get(String key) {
    if ("ro.debuggable".equals(key)) return "0";
    if ("ro.secure".equals(key)) return "1";
    return originalGet(key);
}
```

#### ApplicationInfo 标志

```java
// 检测应用 DEBUG 标志
ApplicationInfo info = getApplicationInfo();
boolean isDebug = (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

// 绕过：移除标志
getApplicationInfo() {
    ApplicationInfo info = originalGetApplicationInfo();
    info.flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
    return info;
}
```

### 6. Runtime 监控绕过 (RuntimeMonitorHook)

#### Pandora RuntimeMonitor 机制

```java
// Pandora 监控所有 Runtime.exec() 调用
Runtime.exec(String command) {
    // 原始执行
    Process p = originalExec(command);
    
    // 监控上报
    if (RuntimeMonitor.shouldMonitor(command)) {
        String output = readProcessOutput(p);
        CommandProcessor processor = getProcessor(command);
        processor.process(command, output);
        RuntimeMonitor.report(command, output);
    }
    
    return p;
}
```

#### 绕过策略

```java
// 方法 1：禁用监控判断
RuntimeMonitor.shouldMonitor() {
    return false;  // 总是不监控
}

// 方法 2：禁用处理器
IPProcessor.process() { return; }
PackageManagerProcessor.process() { return; }
PropProcessor.process() { return; }

// 方法 3：阻止上报
RuntimeMonitor.report() { return; }
```

## Native 层检测（本模块无法处理）

### libfekit.so 检测机制

#### 1. Maps 扫描（C 实现）

```c
// libfekit.so 内部实现
void check_maps() {
    FILE *fp = fopen("/proc/self/maps", "r");
    char line[512];
    
    const char* blacklist[] = {
        "lsposed", "xposed", "libriru", "zygisk",
        "frida", "libsubstrate", "libDalvikLoader.cy.so"
    };
    
    while (fgets(line, sizeof(line), fp)) {
        for (int i = 0; i < sizeof(blacklist); i++) {
            if (strstr(line, blacklist[i])) {
                // 检测到 Hook 框架！
                report_risk("hook_detected", blacklist[i]);
            }
        }
    }
}
```

**Java Hook 无法拦截 C 层的 `fopen()`！**

#### 2. ART 完整性校验

```c
// 检查 ArtMethod.entry_point
void check_art_method(ArtMethod* method) {
    void* entry_point = method->entry_point_from_quick_compiled_code_;
    
    // entry_point 应该指向 OAT 编译代码或解释器
    // 如果指向第三方 .so 则被 Hook
    if (is_third_party_library(entry_point)) {
        report_risk("method_hooked", method->name);
    }
}
```

Xposed/LSPosed 的 Hook 本质就是修改 `entry_point`！

#### 3. JNI 函数表校验

```c
// libfekit.so 内置标准 JNI 函数地址
const void* standard_jni_table[] = {
    &_ZN3art8CheckJNI19FromReflectedMethod...,
    &_ZN3art3JNIILb1EE18SetStaticCharField...,
    // ... 200+ 个符号
};

void check_jni_integrity() {
    for (int i = 0; i < jni_count; i++) {
        void* current = get_jni_function(i);
        if (current != standard_jni_table[i]) {
            // JNI 表被篡改！
            report_risk("jni_hooked", i);
        }
    }
}
```

### 需要 Native Hook 方案

要绕过 native 检测，需要：

#### 方案 A: Frida/Dobby Hook

```javascript
// Frida 脚本示例
Interceptor.attach(Module.findExportByName("libc.so", "fopen"), {
    onEnter: function(args) {
        var path = Memory.readUtf8String(args[0]);
        if (path === "/proc/self/maps") {
            // 重定向到过滤后的文件
            args[0] = Memory.allocUtf8String("/data/local/tmp/fake_maps");
        }
    }
});
```

#### 方案 B: Inline Hook

```c
// 使用 Dobby/Substrate Hook libfekit 函数
void* original_check_maps = NULL;

void fake_check_maps() {
    // 什么都不做，直接返回
    return;
}

// Hook
DobbyHook(
    dlsym(RTLD_DEFAULT, "check_maps_symbol"),
    fake_check_maps,
    &original_check_maps
);
```

#### 方案 C: 内存 Patch

```c
// 直接修改 libfekit.so 代码段
void* check_maps_addr = find_function("check_maps");
// 将函数开头改为 "ret" 指令
patch_memory(check_maps_addr, "\xC3", 1);  // x86: ret
```

## 测试与调试

### 1. 日志查看

```bash
# 实时查看模块日志
adb logcat -s QQEnhancedBypass

# 过滤特定操作
adb logcat | grep "Blocked\|Hooked\|Spoofed"
```

### 2. 功能验证

#### Root 检测测试
```bash
# 1. 安装 Root 检测 App（Root Checker）
# 2. 使用本模块
# 3. 检查是否隐藏成功
```

#### 网络拦截测试
```bash
# 抓包查看是否阻止了上报请求
adb shell tcpdump -i any -s 0 -w /sdcard/qq.pcap

# 分析 pcap，查找 trpc.o3.report 等命令
```

### 3. 性能监控

```java
// 添加性能计时
long startTime = System.currentTimeMillis();
// ... hook 操作
long duration = System.currentTimeMillis() - startTime;
if (duration > 100) {
    Log.w(TAG, "Hook took " + duration + "ms");
}
```

## 故障排除

### 问题 1: Hook 不生效

**症状**：日志中看不到 "Hooked" 消息

**排查**：
1. 检查 LSPosed 作用域是否包含 `com.tencent.mobileqq`
2. 确认 QQ 已重启
3. 查看 LSPosed 日志是否有错误
4. 验证类名是否正确（QQ 更新可能改变）

### 问题 2: 账号仍被检测

**可能原因**：
1. Native 层检测未绕过
2. 服务端根据历史行为判断
3. 设备指纹变化触发风控
4. 某些检测点未覆盖

**解决**：
- 使用测试账号
- 配合 native hook 方案
- 保持设备信息一致性
- 查看日志定位未覆盖的检测点

### 问题 3: QQ 崩溃

**排查**：
1. 禁用所有模块，逐个启用定位问题
2. 检查是否 Hook 了关键系统调用
3. 查看崩溃堆栈

## 未来改进方向

### 1. Native Hook 集成

集成 Frida/Dobby，在模块中直接处理 native 检测：

```java
public class NativeBypass {
    static {
        System.loadLibrary("native-bypass");
    }
    
    public native void hookLibfekit();
    public native void hookTuringxq();
}
```

### 2. 动态配置

支持运行时修改配置，无需重新编译：

```java
// 从配置文件读取
File configFile = new File("/data/local/tmp/qq_bypass.conf");
HookConfig.loadFromFile(configFile);
```

### 3. 版本适配检测

自动检测 QQ 版本并调整 Hook 策略：

```java
int qqVersion = getQQVersion();
if (qqVersion >= 8900) {
    // 使用新版本类名
    hookMethod("com.tencent.mobileqq.newclass", "method");
} else {
    // 使用旧版本类名
    hookMethod("com.tencent.mobileqq.oldclass", "method");
}
```

### 4. 自动化测试

构建 CI/CD 管道自动测试新版本：

```bash
# 自动化测试脚本
./gradlew assembleDebug
adb install app-debug.apk
./test_detection.sh
./analyze_logs.sh
```

## 参考资源

- [LSPosed 文档](https://github.com/LSPosed/LSPosed)
- [Xposed API 参考](https://api.xposed.info/)
- [Android Hook 技术原理](https://github.com/tiann/epic)
- [QQ 安全机制研究](https://github.com/cinit/QAuxiliary)
