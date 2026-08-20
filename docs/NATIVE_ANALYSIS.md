# QQ 检测机制分层分析（Native vs Java）

基于 1.txt 的深度分析，重新评估检测分布

## 一、检测分布统计

### 1. Root 检测
```
Java 层：6 个独立实现（弱）
  ├── 简单文件路径检查
  ├── PackageManager 查询
  └── 结果仅供上报，易绕过

Native 层：libfekit.so（强）⭐
  ├── Magisk/KernelSU 字符串检测
  ├── 命令执行：cat /proc/mounts | grep magisk
  ├── Root 管理器黑名单（10+个）
  ├── /proc/self/mountinfo 和 maps 扫描
  └── 直接系统调用，Java Hook 无法拦截
```
**结论**：Java 层只是辅助采集，Native 层才是核心

### 2. Xposed/Hook 检测
```
Java 层：1 个弱检测
  └── 包名查询（easily bypassed）

Native 层：libfekit.so + libturingxq.so（强）⭐⭐⭐
  ├── /proc/self/maps 扫描特征库
  │   └── lsposed, libriru, zygisk, frida, substrate
  ├── ART 完整性校验（libturingxq.so）
  │   ├── ArtMethod.entry_point 检测
  │   ├── ArtField 结构检测
  │   └── Xposed Hook 本质就是修改 entry_point
  └── CheckJNI 函数表完整性（libfekit.so）
      └── 200+ JNI 符号地址比对
```
**结论**：这是 **Native 层检测的重中之重**，Java Hook 几乎无效

### 3. 调试检测
```
Java 层：5+ 个实现（中等）
  └── 读取 /proc/self/status 的 TracerPid

Native 层：libfekit.so（强）⭐
  ├── 直接检查 ro.debuggable
  ├── ART isJavaDebuggable / isNativeDebuggable
  └── 反调试保护
```
**结论**：Java 层可以拦截文件读取，但 Native 层直接系统调用

### 4. Frida 检测
```
Java 层：无

Native 层：libfekit.so + libturingxq.so（强）⭐⭐
  ├── "frida" 字符串扫描
  ├── 端口扫描（27042 等）
  ├── /proc/self/maps 扫描
  └── riskCheckWup 上报
```
**结论**：100% Native 层检测

### 5. 签名校验
```
Java 层：弱检测
  └── oicq.wlogin_sdk.tools.util.getPkgSigFromApkName()
      └── 计算 MD5 用于指纹，非阻断型

Native 层：libMSFKernel.so + libmsfbootV2.so（强）⭐⭐⭐
  ├── IsSignatureValid（阻断型）
  ├── DoAppSignatureCheck
  ├── HandleAppSignature
  └── "Illegal APP. appid = %d with signature[%s]"
      启动直接拒绝
```
**结论**：Java 只采集，Native 才校验，**阻断型检测**

### 6. 设备指纹采集
```
Java 层：Pandora 框架（中等）
  ├── DeviceInfoMonitor 多路径交叉验证
  ├── RuntimeMonitor 命令监控
  └── 可以被 Java Hook 处理

Native 层：libfekit.so（强）⭐
  ├── parse_libart.cpp 解析 ART 内部结构
  ├── client_detect_report.h 采集上报
  └── verify_timer.h 定时验证
```
**结论**：Java 层是前端采集，Native 层是后端验证

### 7. 风控上报
```
Java 层：接口包装
  ├── TuringFD Java API
  └── 数据组装

Native 层：核心逻辑（强）⭐⭐
  ├── libturingxq.so（仅 1 个导出符号 JNI_OnLoad）
  ├── Obfuscator-LLVM 混淆
  ├── riskCheckWup 协议
  └── DeviceTokenV3 生成
```
**结论**：Java 只是壳，Native 才是核心

## 二、Native 加固现状

```
libfekit.so      8.9MB  ⭐⭐⭐
  ├── 符号剥离
  ├── JNI_OnLoad 98KB 动态注册
  ├── 包含完整检测逻辑
  └── Root/Xposed/Frida/Debug 全覆盖

libturingxq.so   未知大小  ⭐⭐⭐
  ├── 仅导出 JNI_OnLoad（1 个符号）
  ├── Obfuscator-LLVM 混淆
  ├── ART 完整性校验核心
  └── 风控上报协议

libQSec.so       29KB   ⭐⭐
  ├── 混淆加载器
  ├── dlopen/dlsym 动态加载
  └── timer/signal 反调试

libMSFKernel.so  8.4MB  ⭐⭐⭐
  ├── OpenSSL 静态链入
  ├── 签名校验（阻断型）
  └── 网络通信核心

libmsfbootV2.so  未知   ⭐⭐
  ├── MSF 引导
  └── 签名 MD5 校验
```

## 三、检测威胁等级评估

### 按威胁级别排序

**严重威胁（阻断型，Java Hook 无效）**：
1. ⭐⭐⭐ 签名校验（libMSFKernel.so）
   - 启动即校验，失败直接退出
   - 重打包必然失败
   
2. ⭐⭐⭐ ART 完整性检测（libturingxq.so）
   - Xposed 本质修改 entry_point
   - 检测到即上报

3. ⭐⭐⭐ /proc/self/maps 扫描（libfekit.so）
   - 直接 fopen() 读取
   - Java Hook 无法拦截

**高威胁（上报型，可部分缓解）**：
4. ⭐⭐ CheckJNI 完整性（libfekit.so）
   - 200+ 符号地址比对
   - PLT/GOT Hook 必被发现

5. ⭐⭐ Root 检测（libfekit.so）
   - 命令执行 + 文件扫描
   - Magisk/KernelSU 特征检测

6. ⭐⭐ 风控上报（libturingxq.so）
   - 高度混淆
   - 协议加密

**中威胁（Java 层可绕过）**：
7. ⭐ Pandora 监控（Java）
8. ⭐ 调试检测（Java 部分）
9. ⭐ 包名查询（Java）

## 四、本项目的实际效果重估

### 能有效绕过的（约 30%）
✅ Java 层 Root 检测（6个实现）
✅ Java 层调试检测（5个实现）
✅ Pandora 监控框架
✅ 网络上报拦截（Java 层）
✅ 包名查询
✅ 设备信息交叉验证（Java 层）

### 无法绕过的（约 70%）⚠️
❌ libfekit.so 的 maps 扫描（核心）
❌ libturingxq.so 的 ART 检测（核心）
❌ libfekit.so 的 CheckJNI 检测
❌ libMSFKernel.so 签名校验（阻断）
❌ Native 命令执行检测
❌ libfekit.so 的 Root 管理器黑名单
❌ 风控协议上报

### 实际防护效果

```
场景 A：纯 LSPosed + 本项目
  └── 效果：⚠️ 约 30% 防护
      ├── Java 层检测被绕过
      ├── 但 Native 检测到 LSPosed
      └── 服务端收到风险报告

场景 B：本项目 + Shamiko（隐藏 Magisk）
  └── 效果：⚠️ 约 40% 防护
      ├── Magisk 特征被隐藏
      ├── 但 LSPosed maps 特征仍存在
      └── libfekit.so 仍能检测到

场景 C：本项目 + Native Hook（Frida/Dobby）
  └── 效果：⚠️ 约 60-70% 防护
      ├── Hook libfekit.so 的 fopen/fgets
      ├── Hook libturingxq.so 的风控函数
      ├── 但 Frida 自身也会被检测
      └── 需要深度对抗

场景 D：完整方案（所有技术组合）
  └── 效果：⚠️ 约 80% 防护（理论上限）
      ├── Java Hook + Native Hook + 内存 Patch
      ├── 签名校验仍然是障碍
      ├── 服务端行为分析无法绕过
      └── 需持续对抗版本更新
```

## 五、正确的技术路线

### 阶段 1：Java 层绕过（本项目，已完成）
- 覆盖 30% 的 Java 层检测
- 为 Native Hook 打基础
- 拦截部分网络上报

### 阶段 2：Native Hook 核心突破（必需）⭐⭐⭐
```c
// 使用 Dobby/Substrate Hook 关键函数

// 1. Hook fopen 重定向 maps 文件
void* fake_fopen(const char* path, const char* mode) {
    if (strstr(path, "/proc/self/maps")) {
        return fopen("/data/local/tmp/fake_maps", mode);
    }
    return real_fopen(path, mode);
}

// 2. Hook libfekit 检测函数
void fake_check_hook() {
    return; // 直接返回，不检测
}

// 3. Hook libturingxq ART 检测
void fake_check_art_method() {
    return; // 跳过完整性检查
}

// 4. Hook 签名校验（高风险）
int fake_signature_check() {
    return 1; // 总是返回通过
}
```

### 阶段 3：深度对抗（可选）
- 内存 Patch 修改检测逻辑
- VirtualApp 级别的虚拟化
- 修改 QQ 本体（法律风险）

## 六、修正后的项目定位

### 原定位（过于乐观）
"全面环境检测绕过模块"

### 实际定位（准确）
"QQ Java 层检测绕过基础框架 + Native Hook 前置准备"

### 真实能力
- ✅ 完整覆盖 Java 层检测（30%）
- ✅ 网络上报拦截（一定效果）
- ✅ 模块化架构便于扩展
- ⚠️ 无法处理 Native 核心检测（70%）
- ⚠️ 需配合 Native Hook 方案
- ⚠️ 对签名校验无效

## 七、给用户的建议

### 如果只使用本项目
**预期效果**：⚠️ 低
- Java 层检测被绕过
- Native 层仍会检测到 LSPosed
- 账号仍有封禁风险

### 推荐配置
```
最低配置：本项目 + Shamiko
  └── 效果：⚠️ 中低（40%）

推荐配置：本项目 + Shamiko + Native Hook
  └── 效果：⚠️ 中等（60-70%）

完整方案：上述 + 虚拟化 + 测试账号
  └── 效果：⚠️ 中高（80%）
```

### 不要期望
❌ 完全绕过所有检测
❌ 主力账号 100% 安全
❌ 对抗服务端行为分析
❌ 永久有效（QQ 持续更新）

### 适合场景
✅ 安全研究和学习
✅ 技术验证（测试账号）
✅ 作为 Native Hook 的前置模块
✅ 理解 QQ 检测机制

## 八、结论

1. **QQ 检测的核心在 Native 层（约 70%）**
2. **本项目只能处理 Java 层（约 30%）**
3. **必须配合 Native Hook 才有实战价值**
4. **签名校验是最大障碍（阻断型）**
5. **服务端风控无法客户端完全对抗**

本项目作为 **Java 层完整解决方案** 是成功的，但要实现真正的"全面绕过"，**Native Hook 不是可选而是必需**。

---

**修正后的项目价值**：
- 作为学习案例：⭐⭐⭐⭐⭐
- 作为 Java 层方案：⭐⭐⭐⭐⭐
- 作为独立使用方案：⭐⭐ (需配合其他技术)
- 作为 Native Hook 基础：⭐⭐⭐⭐⭐
