<div align="center">

# Box ReApp

**面向 Box / box_for_magisk 模块的 Android 控制台**

基于 Jetpack Compose · miuix-kmp · hyperx-compose 构建，提供订阅管理、节点测速、模块控制、日志查看与系统级 Root Shell 调度的一体化体验。

[![License](https://img.shields.io/github/license/MiChongs/box.app?style=flat-square&color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/MiChongs/box.app?style=flat-square&logo=github&color=yellow)](https://github.com/MiChongs/box.app/stargazers)
[![Forks](https://img.shields.io/github/forks/MiChongs/box.app?style=flat-square&logo=github&color=blueviolet)](https://github.com/MiChongs/box.app/network/members)
[![Issues](https://img.shields.io/github/issues/MiChongs/box.app?style=flat-square&logo=github&color=red)](https://github.com/MiChongs/box.app/issues)
[![Last Commit](https://img.shields.io/github/last-commit/MiChongs/box.app?style=flat-square&logo=github)](https://github.com/MiChongs/box.app/commits/main)
[![Repo Size](https://img.shields.io/github/repo-size/MiChongs/box.app?style=flat-square&logo=github)](https://github.com/MiChongs/box.app)

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-28-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions/pie)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose%20BOM-2026.04-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.2.0-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/build)

[![Root](https://img.shields.io/badge/Root-Required-D32F2F?style=flat-square&logo=linuxfoundation&logoColor=white)](#)
[![libsu](https://img.shields.io/badge/libsu-6.0.0-455A64?style=flat-square&logo=android&logoColor=white)](https://github.com/topjohnwu/libsu)
[![miuix-kmp](https://img.shields.io/badge/miuix--kmp-0.9.0-FF6F00?style=flat-square&logo=jetpackcompose&logoColor=white)](https://github.com/miuix-kotlin-multiplatform/miuix)
[![hyperx-compose](https://img.shields.io/badge/hyperx--compose-0.1.4-7E57C2?style=flat-square&logo=jetpackcompose&logoColor=white)](https://github.com/MiChongs/hyperx-compose)

</div>

---

## 项目简介

Box ReApp 是为 [Box](https://github.com/CHIZI-0618/box) 与 box_for_magisk（BFR）生态打造的 Android 客户端，目标是把"模块管理 / 配置编辑 / 状态监控 / 节点测速 / 日志排错"几个原本散落在终端命令、配置文件、Web 面板里的工作，全部收进一个符合 HyperOS 视觉语言的原生 App。

应用本身**不是代理实现**，而是 Box 系列 Magisk/KernelSU 模块的**手机控制端**：所有底层网络、转发、路由仍由 Box 内核完成，App 只负责把它操作得舒服。

---

## 功能矩阵

### 主面板

- 模块运行状态（服务/内核/连接数）实时呈现
- 节点延迟卡片，多目标并行 ping 与 RTT 直方图
- 内核 / Box 模块版本、更新通道与构建信息聚合
- 一键启停、重启、模式切换（Tun / Mixed / Redirect 等）

### 网络与代理

- 订阅源管理：增删改查 / 自动转换 / 强制刷新
- 基础代理配置：监听端口、混合代理、TUN、绕过列表
- **其他代理配置**（settings.ini 全键覆盖）：DNS 劫持、代理能力、资源限制、性能模式
- 应用分流（Per-app proxy）：黑/白名单、批量编辑、按用户空间隔离

### 工具箱

- **Root Shell**：分池设计 — 全局 Persistent Shell 保持会话，独立 Logs Shell 走读链路，模块重启不互相阻塞
- **日志查看器**：跨进程并行抓取 / 文件切换零阻塞 / 自动尾随
- 应用管理：批量授权、查询 UID、显示三方应用代理走向
- 网络诊断：DNS 解析、规则命中追踪、抓包入口

### 系统集成

- 双风味构建：`box`（CHIZI/Box）与 `bfr`（box_for_magisk）共享 UI 与功能
- 自动更新：模块（`module.prop`）+ App（GitHub Releases）双通道
- 双层自适应图标渲染（前景 + 背景，跟随系统主题动态着色）
- HyperX 导航：横向页面级转场，主屏 ↔ 子页全局滑动联动

### UI / UX

- 全量 Jetpack Compose，单 Activity 架构
- miuix-kmp 设计系统：原生 textureBlur、HyperOS 同源 Color/Typography
- hyperx-compose 布局引擎：自动响应折叠 / 大屏 / 双列分栏
- Material You 动态取色 + 自研主题持久化
- IBM Plex Mono 数据字体，强调代理面板的可读性

---

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.3.21 / JVM 21 |
| UI 框架 | Jetpack Compose BOM 2026.04 + Material 1.14 |
| 设计系统 | miuix-kmp 0.9.0（HyperOS 视觉对齐） |
| 布局引擎 | hyperx-compose 0.1.4（自研，提供 HyperX 导航 / 自适应分栏） |
| 模糊/背板 | kyant.backdrop 2.0.0-alpha03 + miuix textureBlur |
| 图像加载 | Coil 3.4.0 |
| Root 通道 | libsu 6.0.0（Persistent + Logs 双 Shell 池） |
| 权限管理 | XXPermissions 28.2 |
| 反编译工具链 | dexlib2 2.5.2（应用清单解析） |
| 关于库 | aboutLibraries 14.0.1 |
| 构建系统 | AGP 9.2.0 / Gradle 9.3.1 / R8 Full Mode |
| Compose 编译器 | Strong Skipping + Intrinsic Remember + 稳定性配置 |

---

## 架构概览

```
app
├── data
│   ├── backend          # Root Shell 池、命令封装（Persistent + Logs 双通道）
│   └── repo             # ConfigRepository / HomeRepository / LogsRepository
├── ui
│   ├── components       # AppHyperXLayout / AppScaffold / 通用卡片
│   ├── screens
│   │   ├── Settings     # 主题 / 关于 / 开源协议 / 二改信息
│   │   ├── tools        # Logs / Root / Apps
│   │   └── *Config      # Base / Other 代理配置
│   └── theme            # AppTheme / AppFonts / ThemeManager
└── utils                # 主题持久化 / 资源工具
```

**Root Shell 双池设计**：

```
PersistentRootShell  ─┐                           ┌─ updateSetting
  (全局会话, 缓存命令) ├─ ShellExecutor.execute ──┤── startService
                      │                           └─ bin 切换 / 重启
                      │
LogsRootShell         ─┐                          ┌─ list log files
  (独立 Builder, 读链)  ├─ LogsRootShell.execute ──┤── tail / rm
                       │                          └─ 模块重启即重建
```

写命令与日志读命令在不同 Shell 上执行，模块重启时日志页不再"加载中"卡死，全局会话也不丢。

---

## 构建

### 环境要求

- JDK 21（推荐 JetBrains Runtime 或 Eclipse Temurin）
- Android Studio Iguana 或更新（AGP 9.2 兼容版本）
- Gradle 由 wrapper 自动下载（9.3.1）
- 已 Root 的测试设备 / 模拟器（minSdk 28 / Android 9.0+）

### Clone

```bash
git clone --recurse-submodules https://github.com/MiChongs/box.app.git
cd box.app
```

> hyperx-compose 作为 Git 子模块包含在 `libs/hyperx-compose`，首次克隆务必加 `--recurse-submodules`。

### 配置 jitpack 私有 Token（可选）

部分依赖（getActivity、libsu 镜像）走私有 jitpack 通道。在项目根目录或 `~/.gradle/gradle.properties` 添加：

```properties
authToken=jp_xxxxxxxxxxxxxxxx
```

无 Token 仍可构建，仅可能命中限流。

### 构建 Debug APK

```powershell
# Windows
.\gradlew.bat :app:assembleBoxDebug

# macOS / Linux
./gradlew :app:assembleBoxDebug
```

Box 风味产物：`app/build/outputs/apk/box/debug/app-<timestamp>-box-debug.apk`
BFR 风味产物：`app/build/outputs/apk/bfr/debug/app-<timestamp>-bfr-debug.apk`

### 构建 Release

需要在 `app/keystore.properties` 中配置签名信息：

```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

然后：

```powershell
.\gradlew.bat :app:assembleRelease
```

---

## 风味（Flavors）

| 风味 | applicationId | 目标模块 | 更新源 |
| --- | --- | --- | --- |
| `box` | `com.box.app` | [CHIZI-0618/box](https://github.com/CHIZI-0618/box) | Box 官方 Release |
| `bfr` | `com.bfr.app` | box_for_magisk | BFR 镜像通道 |

两个风味共享全部 UI 与逻辑，仅在更新检测、资源命名、产物 ID 上分流。

---

## 设备要求

- Android 9.0+（API 28）
- Magisk 24.0+ 或 KernelSU 0.9+ 已安装并授权
- Box 或 box_for_magisk 模块已安装并启用
- 系统空闲内存 ≥ 256 MB（Compose + Root Shell 持续驻留）

---

## 已知约束

- GitHub Releases API 限流：未登录场景下更新检测每小时 60 次
- box / bfr 资源命名必须严格匹配，否则下载校验失败
- 部分定制 ROM 对 `Shell.Builder.create()` 创建的独立 Shell 有 SELinux 拦截，可在 OOBE 中切换为单池模式

---

## 致谢

- [topjohnwu/libsu](https://github.com/topjohnwu/libsu) — Root Shell 通道
- [miuix-kotlin-multiplatform/miuix](https://github.com/miuix-kotlin-multiplatform/miuix) — HyperOS 视觉对齐
- [kyant0/AndroidBackdrop](https://github.com/Kyant0/AndroidBackdrop) — 高性能 Backdrop 实现
- [getActivity/XXPermissions](https://github.com/getActivity/XXPermissions) — 运行时权限封装
- [coil-kt/coil](https://github.com/coil-kt/coil) — 异步图像加载
- 原始项目：[boxproxy/box.app](https://github.com/boxproxy/box.app)
- 二改维护：[MiChongs](https://github.com/MiChongs)

---

## 协议

本项目遵循上游 [boxproxy/box.app](https://github.com/boxproxy/box.app) 的开源协议。请在 Fork / 分发前阅读对应 LICENSE。

---

<div align="center">

**Box ReApp**

[GitHub](https://github.com/MiChongs/box.app) · [Issues](https://github.com/MiChongs/box.app/issues) · [Telegram](https://t.me/cryingbox) · [二改维护者](https://github.com/MiChongs)

</div>
