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

## 当前明确限制

OCR、识图、找色、控件操作、系统导航和应用控制仍属于未完成能力。它们可以保留类型和 Handler，但必须保持不可用状态，不能让 UI 创建出无法执行的脚本。

后续接入这些能力时，应先定义平台接口和测试替身，再实现 Handler，最后接入 UI。
