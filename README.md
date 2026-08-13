# FileAci — Zorv AI 设备文件管理受控端

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Version](https://img.shields.io/badge/version-1.4.1-blue)
![License](https://img.shields.io/badge/license-OpenSource-green)

FileAci 是一个 Android 设备文件管理应用，同时作为 **Zorv AI** 的 ACI 受控端（Agent-Controlled Interface），可被 Zorv AI 主程序在端侧自动发现并读写设备文件。它提供三后端文件访问（应用工作区 / 设备存储 / SAF 目录）、完整文件操作与交互式文件管理器。

## ✨ 特性

- **三后端文件访问**：
  - **应用工作区（local）**：App 外部文件目录为根，`java.io.File` 直读直写，无需授权。
  - **设备存储（storage）**：通过 `MANAGE_EXTERNAL_STORAGE` 全量授权（跳系统设置 `Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`），授权后根指向 `/storage/emulated/0`，可读写 Download / DCIM / Documents / 用户目录。
  - **外加目录（saf）**：「＋ 添加目录」走 SAF 授权 SD 卡 / 特定文件夹树，作为补充根。
- **完整文件操作**：列目录、读文件（文本 / 二进制 base64）、写文件（覆盖 / 追加）、新建目录、重命名、删除（递归）、移动、**复制**。
- **交互式文件管理器 UI**：根切换、面包屑导航、目录/文件图标列表、新建文件夹、重命名、删除确认、文本查看器。
- **多选批量**：勾选多个条目进行复制 / 移动 / 删除，底部出现粘贴栏。
- **排序与搜索**：按名称↑↓ / 大小↓ / 修改时间↓ 排序；在指定目录递归按文件名关键字搜索。
- **属性与解压**：属性对话框（含目录大小 `sizeOf`）；**ZIP 解压**按钮（解压到同名目录）。
- **ACI 受控端**：**12 项文件能力**（含 `file_copy` / `file_unzip`），可被 Zorv AI 后台静默读写/检索/管理。

## 🎨 图标（ZorvAI 风格自适应启动图标）

- **底色**：深空线性渐变 `#0B2A3A` → `#06121F`，叠加青色对角点缀 `#16C9C9`（ZorvAI 品牌视觉）。
- **主体**：白色文件夹 + 粉红标签与节点（`#F472B6`），呼应「文件」语义。
- **自适应图标（Adaptive Icon）**：Android 8.0+ 自动适配设备形状与主题。

## 🔐 权限

| 权限 | 用途 | 类型 |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | 「设备管理存储权限」：授权后读写整块主共享存储（`tools:ignore="ScopedStorage"`） | 危险 |
| `ai.aci.permission.CALL` | ACI 调用（受控端 ↔ 控制端） | ACI 自定义 |
| `ai.aci.permission.DISCOVER` | ACI 发现 | ACI 自定义 |
| `ai.aci.permission.CALL_DANGEROUS` | ACI 危险能力调用 | ACI 自定义 |

> `MANAGE_EXTERNAL_STORAGE` 需在系统设置中手动授予（App 内「授权设备存储」按钮会跳转对应设置页）；授予后自动出现「设备存储」根。该权限为 Android 危险权限，仅当需要访问整块共享存储时才申请。

## 🧩 ACI 能力清单

| 能力 | 说明 |
| --- | --- |
| `file_roots` | 列出已挂载根（应用工作区 + 设备存储 + 已授权 SAF 目录） |
| `file_list` | 列目录（区分目录/文件，含大小/修改时间） |
| `file_read` | 读文件（文本 UTF-8 / 二进制 base64，可限字节） |
| `file_write` | 写文件（text 或 base64，支持追加） |
| `file_mkdir` | 新建目录 |
| `file_rename` | 重命名 |
| `file_delete` | 删除（目录递归） |
| `file_move` | 移动到另一目录 |
| `file_copy` | 复制文件 / 目录到目标路径 |
| `file_unzip` | 解压 ZIP 到同名目录（逐级建目录） |
| `file_info` | 条目详情（含目录大小） |
| `file_search` | 按文件名递归搜索 |

所有能力均为 `BACKGROUND + NO_UI`，适合由 AI 在后台静默调用。

## 🖥️ 操控台（Console）

本 App 作为受控端，向控制端暴露 `console_ui` / `console_action` 双通道（遵循《ACI 开发者手册》§14）：

- **`console_ui`**：返回文件管理器的 SDUI 快照（`snapshot` / `title`），标记 `FLAG_BACKGROUND | FLAG_NO_UI`。
- **`console_action`**：入参 `action` / `payload`，在后台线程驱动文件引擎（列/读/写/复制/解压/搜索）。

SDUI 词汇：`heading` / `text` / `card` / `button` / `divider` / `spacer` / `listitem` / `input`。**受控端不内置自测调试台**，UI 由控制端统一渲染。

## ⚠️ 已知限制

- **设备存储需手动授权**：`MANAGE_EXTERNAL_STORAGE` 由用户在系统设置授予；未授权时仅能访问应用工作区与已 SAF 授权的目录。
- **SAF 目录受系统约束**：SD 卡等经 SAF 授权的目录，写入受系统对树 URI 的权限约束。
- **长任务 14s 上限**：大目录递归搜索 / 复制受单步 ACI 调用 14s 预算约束，超大操作建议分片。

## 🧱 技术栈

- Kotlin + Jetpack Compose（Material 3）
- ACI 框架：`aidl-aci-core`（AIDL + LocalSocket 抽象命名空间双通道）
- 文件访问：`java.io.File`（应用工作区 / 设备存储）+ `DocumentsContract` / SAF（外加目录），无 GMS 依赖

## 📦 安装

- 从 [GitHub Releases](https://github.com/Quor-a/file-aci/releases) 下载最新 APK，允许「未知来源」后安装。
- 或开发机自行构建（见下）。

## 🛠️ 构建（开发机）

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 🔌 接入 Zorv AI

作为第三方受控端，遵循《ACI 开发者手册》§16：剥离 AAR 内 `ai.aci.permission.*` 定义节点（`tools:node="remove"`），仅引用 Zorv AI 主程序已定义的权限，避免异签名安装冲突。

## 📄 许可

开源许可（见仓库 LICENSE）。
