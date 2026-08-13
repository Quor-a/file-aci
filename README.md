# FileAci — 基于 ACI 框架的设备文件管理器受控端（对标 文件管理器 / 资源管理器）

FileAci 是一个 Android 设备文件管理应用，同时作为 **Zorv AI** 的 ACI 受控端（Agent-Controlled Interface），可被 Zorv AI 主程序在端侧自动发现并读写设备文件。

## 功能（v1.0.0）

- **双后端文件访问**（Android 正规途径，无需申请危险存储权限）：
  - **应用工作区（local）**：App 外部文件目录为根，java.io.File 直读直写，无需授权。
  - **设备存储（saf）**：用户通过系统「授权设备存储」(SAF `openDocumentTree`) 授权 Download / DCIM / SD 卡等目录树，经 `DocumentsContract` 读写；授权持久化，重启仍有效。
- **完整文件操作**：列目录、读文件（文本 / 二进制 base64）、写文件（覆盖 / 追加）、新建目录、重命名、删除（递归）、移动。
- **交互式文件管理器 UI**：根切换、面包屑导航（进入/返回上级）、目录/文件图标列表、新建文件夹、重命名、删除确认、文本文件查看器。
- **文件名搜索**：在指定目录递归按文件名关键字查找。
- **ACI 受控端**：10 项文件能力，可被 Zorv AI 后台静默读写/检索/管理。
- **调试操控台**：内置面板自绑定 ACI Service，可视化双通道状态与能力列表，手动填参调 `call()`。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- ACI 框架：`aidl-aci-core`（AIDL + LocalSocket 抽象命名空间双通道）
- 文件访问：`java.io.File`（应用工作区）+ `DocumentsContract` / SAF（设备存储），无存储权限、无 GMS 依赖

## 构建

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## ACI 能力清单

| 能力 | 说明 |
| --- | --- |
| `file_roots` | 列出已挂载根（应用工作区 + 已授权设备目录） |
| `file_list` | 列目录（区分目录/文件，含大小/修改时间） |
| `file_read` | 读文件（文本 UTF-8 / 二进制 base64，可限字节） |
| `file_write` | 写文件（text 或 base64，支持追加） |
| `file_mkdir` | 新建目录 |
| `file_rename` | 重命名 |
| `file_delete` | 删除（目录递归） |
| `file_move` | 移动到另一目录 |
| `file_info` | 条目详情 |
| `file_search` | 按文件名递归搜索 |

所有能力均为 `BACKGROUND + NO_UI`，适合由 AI 在后台静默调用。

## 接入 Zorv AI

作为第三方受控端，遵循《ACI 开发者手册》§16：剥离 AAR 内 `ai.aci.permission.*` 定义节点（`tools:node="remove"`），仅引用 Zorv AI 主程序已定义的权限，避免异签名安装冲突。

## 已知限制

- **应用工作区（local）根**仅限 App 私有外部目录，AI 默认可管理此工作区；要访问 Download/DCIM/SD 等需用户先在 App 内点「授权设备存储」。
- SAF 不提供 `MANAGE_EXTERNAL_STORAGE` 那样的全局无感访问，且部分厂商文件管理器路径存在差异；写入受系统对树 URI 的权限约束。
- 长任务（大目录递归搜索）受 14s 硬上限约束。

## 许可

开源许可（见仓库 LICENSE）。
