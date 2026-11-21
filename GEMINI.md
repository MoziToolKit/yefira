# GEMINI.md - Yefira (Live Sync Mod) 协作与开发规范

本文档是供所有 AI Coding Agent 以及核心开发者在 `yefira` 工作区阅读的开发规范与架构准则。

---

## 1. 项目定位与工作区边界

`Yefira` 是专为 **Minecraft** 与 **Blender ([MoziToolKit](../MoziToolKit))** 打造的高性能、低延迟实时双向同步模组（Fabric / Forge / NeoForge），作为三端协同体系中的服务端事实源。

### 多工作区协同拓扑 (Three-Pillar Architecture)
| 工作区路径 | 角色 | 职责边界 |
| :--- | :--- | :--- |
| **`../yefira`** | **实时协同服务端模组 (当前工作区)** | 负责 Minecraft 服务端权威 3D 体素扫描 (`CanonicalVoxelScanner`)、方块实体载荷序列化 (`BlockEntitySerializer`：饰纹陶罐、旗帜、头颅)、选区管理与游戏事件桥接；通过 `NativeSync` (JNI) 驱动基于句柄表生命周期保护的 Rust 原生 `LiveSyncServer`，完成 Protocol v4 (`0x04`) WebSocket 通信、xxHash64 SSOT 校验与基线/增量流式推送（支持 1.20.1 ~ 26.2 多版本）。 |
| **`../libmozitoolkit`** | **Rust 核心引擎 (`libmtk`)** | 通过 `mtk-jni` 与 `mtk-sync` 为 Yefira 提供原生服务端核心 (`LiveSyncServer`)、握手决策 (`decide_handshake`) 与 Protocol v4 (`0x04`) 编解码；为 MoziToolKit 提供增量网格化 (`DeltaMesher`) 与双通道图集重映射。 |
| **`../MoziToolKit`** | **DCC 插件前端 (Blender Addon)** | 提供 3D 视图交互面板、伴生点云网格装配、选区射线交互与操作符事件响应。 |

---

## 2. 核心规约与必须遵守的铁律 (Golden Rules)

### 规则 1：服务端权威原则 (Server-Authoritative Sync)
- 世界体素状态、选区边界（SelectionBox）有效性、区块加载状态与变化检测**以 Minecraft 服务端为唯一事实源**。
- 采用 `CanonicalVoxelScanner`、`BlockEntitySerializer` 与 `NativeSync`（调用 Rust 原生 `xxHash64`）进行 3D 空间扫描、方块实体 JSON 规范化与哈希指纹计算，确保客户端与服务端的体素及方块实体状态具备自愈校验能力。

### 规则 2：协议对齐原则 (Protocol v4 Compliance)
- 所有 WebSocket 帧调度、握手流程（`HELLO` / `WELCOME`）、二进制体素基线/增量与稀疏方块实体流（`0x11` `BaselineBlockEntities` / `0x12` `DeltaBlockEntity`）必须严格对齐 **Live Sync Protocol v4 (`0x04`)**（基于 [MTK 2.0 Live Sync 协议与架构设计规范](../MoziToolKit/docs/live_sync/live_sync_protocol.md)；连续视觉实体操作码 `0x13` / `0x14` 已从 2.0 主线移除）。
- `NativeSync.java` 的 JNI 方法签名、事件码（`EVENT_CLIENT_CONNECTED`..`EVENT_WARNING`）与 `WebSocketIntegrationTest.java` 的线协议常量（`PROTOCOL_VERSION = 0x04`）必须与 `../libmozitoolkit/bindings/mtk-jni/` 和 `../libmozitoolkit/crates/mtk-sync/` 保持严格同步；严禁在未同步更新协议规范及各端实现的情况下擅自变动数据包格式与操作码。

### 规则 3：跨版本解耦与架构分代隔离 (Multi-Version Separation)
- **纯核心层共享**：`common-core` 必须保持无具体版本依赖，所有通用通信管理（`network/`）、选区几何数学（`selection/`）、配置项（`config/`）、Native JNI 桥接与状态序列化（`encoder/`）均在此实现。
- **分代兼容隔离**：所有针对具体 Minecraft 版本的 API 适配（如 `LevelChunkMixin`、渲染接口、按键输入、方块实体兼容）必须严格收敛在对应 `versions/` 子工程中：
  - `versions/1.20.1` (Java 17, Fabric & Forge)
  - `versions/1.20.4` (Java 17, Fabric & NeoForge)
  - `versions/1.21.1` (Java 21, Fabric & NeoForge)
  - `versions/1.21.4` (Java 21, Fabric & NeoForge)
  - `versions/26.2` (Java 25, Fabric & NeoForge)
- 严禁在 `common-core` 中引入任何特定版本的混淆名（Mojang / Yarn / Intermediary）或硬依赖。

### 规则 4：增量分片与协同并发安全 (Chunked Deltas & Thread Safety)
- 针对大规模选区更新，必须采用分片批处理（Batch Chunking），杜绝单帧网络包体积膨胀导致 Netty 缓冲区溢出。
- 体素扫描与 WebSocket 帧发送必须遵循协作式取消（Cooperative Cancellation）与有界线程池设计，严禁阻塞 Minecraft 主服务线程（Server Tick Thread）。

### 规则 5：安全与鉴权规约 (Security & Local Priority)
- WebSocket 通信支持 Auth Token 认证机制。
- **Loopback 本地优先**：默认配置下，对于来自本机的 Loopback 连接（127.0.0.1 / localhost），允许根据配置安全免密直连（当配置开启 `allowLoopbackWithoutToken` 时），降低本地创作流程摩擦；外部网络连接与非回环绑定必须强制核验 Token。
- 选区存储必须保证线程原子性（Atomic Selection Storage），具备防越界与选区软上限警告机制。

### 规则 6：构建、多 JDK 与测试门禁 (Build, Multi-JDK & Test Gates)
- **版本与 JDK 对应矩阵**：
  - `1.20.1`、`1.20.4` 使用 **JDK 17**（Gradle 8.10）；
  - `1.21.1`、`1.21.4` 使用 **JDK 21**（Gradle 8.10）；
  - `26.2` 使用 **JDK 25**（Gradle 9.5.1）。
- **本地多 JDK 自动发现与宿主高版本 JDK 注意事项**：
  - 根目录 `build.gradle` 按 `JAVA_{17,21,25}_HOME` → `~/.gradle/jdks/` → `/usr/libexec/java_home` → PrismLauncher 运行时的顺序自动定位对应版本 JDK。
  - 若宿主系统默认 `java` 高于 Gradle 支持上限（例如 OpenJDK 27），执行根命令或脚本前必须显式指定已缓存的用户态 `JAVA_HOME`（例如 `JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.12.1+1/Contents/Home"`）。
- **Gradle 缓存与 Native 动态库重测规约**：
  - `NativeSync` 在本地开发态会直接加载 `../libmozitoolkit/target/release/libmtk_jni.*`（不属于 Gradle 任务输入跟踪范围）。每当在 `../libmozitoolkit` 重新编译 `mtk-jni` 后，必须对各版本执行 `:common:test --rerun-tasks` 强制复测，严禁直接采信 `UP-TO-DATE`。
- **提交前完整门禁**：
  ```bash
  # 运行全版本单元测试与集成测试
  ./gradlew test

  # 编译并打包全部 10 个加载器产物至 build/dist/
  ./gradlew buildAll

  # 运行静态不变量、单元测试与产物完整性全套校验
  python3 scripts/verify_all.py
  ```
- 涉及网络包格式或 JNI 接口调整时，必须同步在 `common-core/src/test/java/` 中增加或更新对应的编解码、句柄生命周期与集成测试。

---

## 3. 项目架构概览

```
yefira/
├── common-core/                        # 跨版本共享纯逻辑核心
│   ├── command/                        # 跨版本统一选区与服务端管理指令
│   ├── network/                        # WebSocketServerManager 通信管理与 Native 句柄生命周期
│   ├── selection/                      # SelectionBox 选区数学几何与原子状态
│   ├── config/                         # YefiraConfig 统一配置与 Token 鉴权
│   ├── encoder/                        # NativeSync JNI 桥接、BlockStateSerializer、CanonicalVoxelScanner 与 EntityEntry / BlockChangeEntry
│   └── compat/                         # 跨版本通用抽象契约 (ILevelCompat / VersionCompat) 与 blockentity/BlockEntitySerializer
├── versions/                           # 针对具体 MC 版本的加载器绑定
│   ├── 1.20.1/                         # Fabric & Forge (JDK 17)
│   ├── 1.20.4/                         # Fabric & NeoForge (JDK 17)
│   ├── 1.21.1/                         # Fabric & NeoForge (JDK 21)
│   ├── 1.21.4/                         # Fabric & NeoForge (JDK 21)
│   └── 26.2/                           # Fabric & NeoForge (JDK 25)
├── scripts/verify_all.py               # 跨版本静态不变量、测试与 Jar 产物校验脚本
├── .github/workflows/build.yml         # CI 跨平台 mtk-jni + 5 版本并行矩阵工作流
└── build.gradle                        # 根工程聚合构建
```

---

## 4. 相关文档与协议索引

- **协议权威规范**：[`../MoziToolKit/docs/live_sync/live_sync_protocol.md`](../MoziToolKit/docs/live_sync/live_sync_protocol.md)
- **Rust 核心与服务端实现**：[`../libmozitoolkit/crates/mtk-sync/`](../libmozitoolkit/crates/mtk-sync/)
- **JNI 模组原生绑定**：[`../libmozitoolkit/bindings/mtk-jni/`](../libmozitoolkit/bindings/mtk-jni/)
- **Blender 操作符**：[`../MoziToolKit/operators/sync/`](../MoziToolKit/operators/sync/)
