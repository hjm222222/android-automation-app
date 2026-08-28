# 项目架构边界

本文档记录当前脚本自动化项目的职责边界和功能开发规则。目标不是一次性引入复杂架构，而是让每个新功能都有稳定的进入路径，避免继续把业务堆进 `FloatingWorkspaceService`。

## 当前分层

```text
UI / Service
    负责展示、收集输入、悬浮窗生命周期和依赖组装

Script model / runtime
    负责 ScriptAction、条件、运行期变量、工作区和统一执行规则

Action handler
    负责一种具体动作的参数解释和业务执行

Platform
    负责 Android 无障碍、手势、截图和系统能力

Data
    未来负责脚本保存、读取和导入导出
```

依赖方向应保持为：

```text
UI / Service -> Script runtime -> Handler -> Platform interface
```

脚本运行层不能直接依赖具体的 Android Service。平台能力通过接口或显式 provider 传入，便于测试和替换。

## 动作的完整闭环

新增一个动作前，必须同时考虑：

1. `ActionType`：动作身份和分类。
2. `ScriptActionHandler`：默认模型和执行逻辑。
3. `ActionEditorDefinition`：用户输入哪些参数。
4. 参数校验：非法输入必须在创建动作时被拒绝。
5. 执行测试：验证成功、失败和平台能力缺失时的行为。

未完成的动作不能出现在动作选择器中。不要只添加枚举值或空 Handler。

## 动作创建边界

动作应通过 `ActionFactory` 创建。调用方只提供 `ActionType`、编辑器字段和执行设置，不应直接调用 Handler 的 `createDefault()` 再手动拼接 `ScriptAction`。

当前 UI 和未来 AI 都应复用以下路径：

```text
ActionType + editorValues + settings
    ↓
ActionFactory
    ↓
ActionCreationResult
    ↓
ScriptWorkspaceController
```

这样参数转换、缺失字段校验和动作默认值只有一个实现位置。

## 对外动作 API

面向 UI、规则解析器和 AI 的简单接口位于 `script/api`。调用方应优先使用 `ScriptActionApi`，不要直接创建 `ScriptAction` 或修改工作区列表。

当前已提供：

```text
addWait(seconds, position)
addClick(x, y, position)
remove(actionId)
removeAt(index)
replaceWait(actionId, seconds)
listActions()
```

插入位置使用 `InsertPosition.End`、`InsertPosition.At(index)` 或 `InsertPosition.After(actionId)` 表达，不使用 `0`、`null` 等魔法值。面向 AI 的删除、替换和移动操作应使用稳定 `actionId`，只有简单 UI 兼容场景才使用 `removeAt`。

接口返回 `ActionApiResult`，失败时携带 `FailureCode` 和可展示消息。尚未实现的动作必须返回失败，不能返回伪造的成功结果。

示例：

```kotlin
val result = api.addWait(seconds = 2, position = InsertPosition.End)
```

这层的作用是让客户只需要理解“增加等待、删除动作、修改动作”，不需要理解 Handler、运行时参数或 Android 平台服务。

## AI 与动作列表边界

AI 不直接修改 `MutableList`，也不调用 Android Service。AI 的输出应先被解析为两类纯 Kotlin 输入：

```text
新建动作：ActionType + editorValues + ActionSettingsInput
修改列表：WorkspaceActionCommand
```

处理顺序固定为：

```text
AI 结构化输出
    ↓
ActionSettingsMapper / ActionFactory
    ↓
WorkspaceActionCommand
    ↓
ScriptWorkspaceController
```

`Replace`、`Remove` 和 `Move` 必须携带 `ScriptAction.id`，不能使用列表索引。AI 提交命令前应先读取 `snapshot()`，将用户可见的动作 ID 与意图一起提交；控制器会拒绝不存在的 ID 或无效位置。

AI 的自然语言解释、网络请求、重试和权限处理属于 AI 适配层，不应进入 `ScriptRunner`、Handler 或 `FloatingWorkspaceService`。

## 统一执行规则

所有动作都必须经过 `ScriptRunner` 的同一个递归入口：

```text
条件判断
    ↓ 不满足则跳过
运行前动作
    ↓
当前动作
    ↓
运行后动作
```

顶层动作和嵌套动作不能拥有不同的执行语义。新的动作组合能力必须复用现有递归入口，不能在 UI 或 Handler 中自行执行其他动作。

## 状态所有权

脚本列表由 `ScriptWorkspaceController` 持有。UI 只能通过 Controller 添加、删除、读取快照和运行，不能直接持有另一份脚本列表。

运行期变量由 `ScriptRuntime` 持有。变量只属于一次脚本运行，不应通过静态变量或 UI 字段保存。

同一份状态只能有一个明确所有者。如果发现 UI、Service 和 Controller 同时保存同一状态，应先停止加功能并重新确定所有权。

## FloatingWorkspaceService 约束

`FloatingWorkspaceService` 可以负责：

- Service 生命周期；
- 悬浮窗创建、移动和销毁；
- 页面显示和用户事件转发；
- 组装运行时依赖。

它不应长期负责：

- 直接实现动作执行；
- 维护第二份脚本状态；
- 解析平台手势细节；
- 在多个页面中重复创建 `ScriptAction`；
- 直接决定运行时条件语义。

当该类继续增长时，优先把“动作创建”和“设置表单转换”移到独立组件或工厂中，而不是继续增加 `when` 分支。

## 功能开发检查表

每次添加功能前先回答：

- 这个功能属于 UI、脚本运行、Handler、平台还是数据层？
- 状态由谁持有？
- 是否绕过了 `ScriptRunner` 或 `ScriptWorkspaceController`？
- 是否需要新的接口，而不是读取全局变量？
- 是否有单元测试覆盖成功、失败和边界行为？
- CI 是否会执行这个测试？

如果这些问题没有明确答案，先暂停实现，补齐边界后再继续。

## 分阶段实施计划

本项目采用“分阶段重构、每阶段可运行”的方式。目标是逐步降低耦合，不进行一次性推倒重来。每个阶段完成后，必须先通过编译和测试，再进入下一阶段。

### 阶段 0：基线和保护

目标：确保后续重构有可比较的基线。

实施内容：

- 保证 Debug 构建可以完成。
- 记录当前单元测试结果；既有失败必须单独登记。
- 保留当前可运行的点击、悬浮窗、脚本保存和脚本运行流程。
- 每轮修改只处理一个明确主题，不混入无关重构。

完成标准：

- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 通过。
- 单元测试结果没有新增失败。
- 真机可以启动应用并运行一个最简单的点击脚本。

### 阶段 1：视觉能力基础

目标：统一截图和视觉结果，解决卡死、超时和资源释放问题。

实施顺序：

1. 修复 `ScreenCaptureSession` 的关闭、取消、超时和系统停止竞态。
2. 引入 `VisionResult`，区分 `Success`、`NotFound`、`Timeout`、`PermissionDenied` 和 `Failed`。
3. 接入 `captureResult()`。
4. 接入 `matchResult()`。
5. 迁移 `VisionActionHandlers`，让图像等待只对 `NotFound` 重试。
6. 迁移 OCR 和找色结果。
7. 迁移视觉条件判断。
8. 统一编辑阶段的截图入口和 Bitmap 所有权。
9. 在真机验证授权失效、截图超时、退出运行和重复运行。

本阶段不做：

- 不修改脚本 JSON 格式。
- 不拆多模块。
- 不引入 Hilt。
- 不重写悬浮窗界面。
- 不同时重构非视觉动作。

完成标准：

- 视觉失败不再统一退化为 `null`。
- 取消不会被显示为普通失败。
- `WaitImage` 有明确的未找到、超时和权限失败行为。
- 截图 Bitmap、ImageReader、VirtualDisplay 和 MediaProjection 有明确释放路径。
- OCR、识图、找色在真机上可以得到可解释结果。

当前进度：截图会话、`VisionResult`、`captureResult()`、`matchResult()`、视觉动作处理器、OCR、找色和条件判断已完成；编辑阶段截图入口统一和真机验收仍待完成。

### 阶段 2：统一动作执行规范

目标：让点击、滑动、长按、双击、输入、控件操作和视觉动作遵循相同的执行规则。

实施内容：

- 统一动作开始、成功、失败和取消回调。
- 统一动作超时和取消语义。
- 统一动作日志字段：动作 ID、动作类型、开始时间、结束状态和错误原因。
- 平台能力缺失时返回明确失败，不静默跳过。
- 保持所有动作仍由 `ScriptRunner` 的统一入口执行。
- 为每类动作补充成功、失败、取消和边界测试。

完成标准：

- 每个动作都能在运行悬浮窗中显示当前状态。
- 用户取消不会弹出错误提示。
- 动作失败可以定位到动作类型和动作 ID。
- 不同动作不会各自实现一套独立的取消逻辑。

### 阶段 3：运行控制与状态管理

目标：把运行控制从悬浮窗 UI 中分离出来。

新增或整理的状态：

```text
Idle
Running
Paused
Cancelling
Completed
Failed
```

实施内容：

- 抽出运行控制器，负责开始、暂停、继续和取消。
- `ScriptRunner` 负责脚本执行，不负责创建或更新悬浮窗。
- 运行悬浮窗只观察运行状态并发送用户操作。
- 统一处理服务销毁、窗口关闭和协程取消。
- 运行结束后只产生一个明确的终态。

完成标准：

- 暂停、继续、取消可重复操作且不会产生竞态错误。
- 服务销毁后没有遗留运行协程或窗口。
- 完成、失败和取消在界面上可以区分。
- `JobCancellationException` 不会作为普通错误展示。

### 阶段 4：职责收拢和旧代码清理

目标：降低 `FloatingWorkspaceService` 的职责密度，删除已经没有调用方的旧实现。

实施顺序：

1. 把动作创建和设置表单转换收拢到 `ActionFactory` 或独立协调器。
2. 把截图、OCR、模板采集和取色的编辑流程收拢到视觉编辑组件。
3. 确认没有调用后再删除 `ScreenCaptureSnapshot`。
4. 统一 Overlay 的显示、关闭和异常处理。
5. 移除或隔离硬编码调试上报，保留必要的本地日志。
6. 检查同步文件读写和图像处理是否阻塞主线程。

完成标准：

- `FloatingWorkspaceService` 主要负责生命周期、窗口和事件转发。
- 平台细节不散落在多个 UI 回调中。
- 删除旧实现前有全局调用确认。
- 调试代码不会影响正常脚本运行。

### 阶段 5：动作分类和用户体验

目标：在执行核心稳定后，整理动作选择和编辑体验。

动作分类固定为：

```text
基础操作：点击、滑动、长按、双击、输入文字
控件操作：点击控件、输入控件、查找控件
图像识别：OCR、图像匹配、颜色识别
流程控制：等待、条件、循环、变量
系统操作：返回、主页、最近任务、启动应用
```

实施内容：

- 动作选择器按分类展示。
- 未完成或不可用的动作不能创建脚本。
- 设置页面显示必要的参数错误。
- 运行状态显示当前动作和失败原因。
- 不改变已有脚本格式，除非确认存在无法兼容的缺陷。

完成标准：

- 新用户可以创建、保存、重新打开并运行简单脚本。
- 动作参数错误在保存前可发现。
- 运行失败时用户知道失败动作和原因。
- 旧脚本仍能正常读取和执行。

## 每阶段交付流程

你负责按当前阶段实施，我负责检查。每个阶段都按以下格式交付：

```text
阶段名称：
修改文件：
完成内容：
未处理内容：
编译结果：
测试结果：
真机验证结果：
已知问题：
```

我检查时重点关注：

- 是否超出当前阶段范围。
- 是否破坏已有调用契约。
- 是否吞掉 `CancellationException`。
- 是否出现新的资源泄漏或重复释放。
- 是否把平台代码引入脚本模型和运行规则。
- 是否有测试证明行为没有回归。

出现以下情况时必须暂停，不继续加功能：

- 编译失败且原因未明确。
- 单元测试出现新增失败。
- 真机出现崩溃、卡死或权限状态无法恢复。
- 同一份状态出现两个以上持有者。
- 为了通过测试而静默吞异常。

## 当前下一步

当前处于阶段 1，视觉动作、OCR、找色、视觉条件和 OCR 区域参数闭环已经完成。下一项由你实施：统一编辑阶段的截图入口和 Bitmap 所有权。

实施范围固定为：

1. 盘点 `FloatingWorkspaceService`、OCR 采集、图像模板采集、颜色采集和区域截图的现有截图调用。
2. 所有编辑阶段截图统一通过 `VisionController` 或明确的编辑视觉接口进入，不再直接创建或使用旧截图实现。
3. 明确截图返回值和 Bitmap 的所有权：调用方负责使用结束后的释放，Overlay 不得擅自回收不属于自己的 Bitmap。
4. 保持现有模板选择、颜色选择和 OCR 采集的用户流程不变。
5. 不修改 `ScriptRunner`、普通动作、运行悬浮窗和脚本 JSON 格式。
6. 不删除 `ScreenCaptureSnapshot.kt`，先完成全局调用确认并单独报告。
7. 为截图成功、权限失败、超时、取消和 Bitmap 释放补充测试；取消必须继续传播。
8. 编译、运行完整单元测试，并报告是否有真机验证。

完成后按“修改文件、完成内容、未处理内容、编译结果、测试结果、真机验证结果、已知问题”格式交付，我负责检查代码质量和范围。

## 当前明确限制

OCR、识图、找色、控件操作、系统导航和应用控制仍属于未完成能力。它们可以保留类型和 Handler，但必须保持不可用状态，不能让 UI 创建出无法执行的脚本。

后续接入这些能力时，应先定义平台接口和测试替身，再实现 Handler，最后接入 UI。
