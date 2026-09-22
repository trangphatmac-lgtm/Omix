# 验证记录

## 2026-09-21 实施验收

环境：macOS 15.6 / Apple Silicon，Java 21.0.5，Minecraft 1.21.11，Fabric Loader 0.19.3，项目锁定的 Yarn 1.21.11+build.6。所有实机测试使用独立的临时游戏目录，没有修改用户存档或连接服务器。

| 检查 | 结果与范围 |
| --- | --- |
| Gradle `check` | 通过：256 个 Java 测试，其中 21 个脚本专项测试；Svelte/TypeScript 检查、WebUI 构建、Harness 8 项、MCP 6 项、音乐 sidecar 测试及最终 JAR 资源校验 |
| 源码编译 | 10 个随附示例使用真实客户端和 Minecraft 类路径编译通过；Java 21 record、switch、文本块；原始行列诊断；禁用注解处理 |
| 映射回归 | 跨依赖继承、Lambda、内部类编译/重映射/执行；排除启动器原始混淆 JAR及 ECJ 默认 SOURCE_PATH，避免 `cn` 混淆类遮蔽 `cn.omix` 包 |
| named 开发客户端 | `tools/run_script_smoke.py` 全部通过：多模块、命令调用、状态保留、三类失败恢复、模式切换/卸载、原生 Minecraft/API访问、截图 |
| 最终 remap JAR / intermediary | 使用正常 Fabric KnotClient 加载发布 JAR，运行相同 smoke 全部通过；运行时使用内置 ECJ，不调用启动器 javac |
| 热替换与恢复 | key=82、NumberValue=7、开关与两个同包装类实例保持；编译错误、Aura 名称冲突、onEnable 的 AssertionError 均保持/恢复旧运行代次；错误记录映射原始源码行 |
| 整模块模式 | Speed 主 Mode，NoFog 的 Behavior 双向切换，Teams 同步查询，PathFinder 路径提供者；当前模式卸载恢复内置选择，关闭可关闭模块，保留常驻提供者 |
| 所有权单测 | 事件实例隔离、同步优先级/取消、注销后不再回调、代次失效与逆序清理；两个数据包 owner 的独立释放及旧 owner 迟到清理 |
| MCP 协议 | 官方 SDK 真实 stdio 客户端：初始化、离线 docs/resources、schema、工具发现、会话隔离、实例重连、取消经 stdio 到 DELETE、符号链接启动路径；真实导出包连接游戏发现 38 个工具并返回 PNG image content |
| Harness 回归 | 保留原有游戏工具协议；v2 并发调用只创建一个租约，截图转换为附件，回合结束释放占用；已有 Workspace 测试通过 |
| Script Studio / Web ClickGUI | 连接实际游戏 HTTP 服务的浏览器测试：模板、中文源码输入/保存、check/load、显示运行代次、错误行点击聚焦编辑器；Web ClickGUI 显示新模块，卸载后无需手动刷新即移除 |
| 游戏内 CEF | 实际打开 Scripts 页面并通过原生帧缓冲截图验证渲染；MCEF 初始化及页面 HTTP 200 |
| Agent skill | skill-creator 的 quick_validate.py 通过；API、mode-host、tools schema、示例与离线 HTML 从当前源文件生成 |

## 可重复执行

仓库自动检查：`gradle check`。构建包含 `generateScriptReference`、`checkWebUi`、`testAiHarness`、`testScriptMcp`、`remapJar` 和 `verifyWebUiRuntimeBundle`。Gradle 使用项目配置的 Node 运行时。

在已启动的隔离测试实例上执行：

```sh
python3 tools/run_script_smoke.py --game-dir /path/to/isolated/game
```

此测试创建随机 ID 的脚本，实际加载/重载并调用客户端。结束后注销并删除自己创建的源码，恢复 Speed/Teams/NoFog/PathFinder 的原设置。分别在 `runClient` 和装有发布 JAR 的 Fabric 实例中运行。运行日志可保存在 `build/reports/script-validation/`。

## 尚未覆盖的真实场景

以上通过项不代表完整跨平台或联网验收。以下尚未实测，发布前须在目标环境继续检查：

- Windows/Linux 启动器与游戏内 CEF 中文输入法候选/组合输入。
- 原生 ClickGUI 的实际鼠标交互，以及聊天界面 HUD 拖动。
- 联网服务器上的包取消/替换、世界切换期间迟到任务、旋转与移动修正效果，以及 FisProxy 的真实服务连接。
- 所有 94 个模式宿主的逐个游戏行为；当前实测只覆盖上表列出的宿主和直接查询。未提供 hook 的原生消费者恢复原版/不执行内置行为。
- 多个真实外部 Agent 同时进行游戏操作、长时间空闲租约到期、实际模型端到端自主编写/修复（会话争用与释放目前有协议/Java 回归）。

编译通过、协议模拟或主菜单测试不能替代这些结果。任意原生 Java 线程、I/O 与已提交网络操作仍按可信脚本模型处理，不承诺事务撤销。

## 2026-09-22 界面与会话修复回归

- `.script open` 使用客户端队列，聊天框提交后的关闭不会覆盖新界面。WebUI 切换继承原非 WebUI 父界面；ESC 不再返回已经隐藏浏览器的旧 ClickGUI；过期的关闭动画不能关闭新面板。
- `tools/run_webui_smoke.py --game-dir <隔离游戏目录>` 在实际 named 客户端执行 5 项检查全部通过：模拟聊天提交关闭顺序、Script Studio ESC、ClickGUI → Scripts ESC、旧关闭动画与新面板竞争、关闭后浏览器不可见。使用实际 CEF 纹理就绪作为渲染前提；未进入服务器，本次退出目标为 Omix 主菜单。
- Harness 新增 5 项激活并发测试，直接提取锁定依赖中实际控制器代码，使用可控工厂屏障复现 `SessionAlreadyOwnedError`；验证创建／恢复两个方向、不同会话并行、失败后重试和卸载。全部 13 项 Harness 测试通过。
- 独立 Harness profile 实际完成启动、重启与历史会话恢复，页面显示原测试历史，无写锁报错；没有使用真实模型密钥或进行模型生成验收。
- Gradle `check remapJar` 通过：256 项 Java 测试、Svelte 检查、Harness、MCP 6 项及最终资源检查。发布 JAR 的 `common.zip` 含会话激活修复插件与对应启动配置。

发布 remap JAR（intermediary 命名空间）通过正常 Fabric KnotClient 启动后，同样 5 项 WebUI 回归全部通过；日志位于 `build/reports/webui-regression/development.log` 与 `production.log`。

## 2026-09-22 外部 MCP 游戏工具对齐

- `gradle check remapJar` 通过，Java 共 257 项测试；新增离线游戏目录与实际 `MinecraftCommandToolExecutor.buildSnapshot` 一致性检查。后续 MCP 错误透传与版本更新再次通过 `testScriptMcp remapJar verifyWebUiRuntimeBundle`。
- 10 项真实 stdio MCP 协议测试通过，包括离线首次发现全部 22 个游戏工具、逐工具参数／名称转发、实时参数和描述更新及通知、移除运行时扩展、离线后重连、取消、独立租约和 Java 拒绝原因透传。
- 使用最终 remap JAR 和客户端实际导出的 `.agent/mcp/server.mjs`，真实 stdio 客户端首次发现 39 个工具；成功读取实时上下文、`getallconfig`、`getpacketlogs` 与同一 Harness 参考文档。测试停留在主菜单，`getcommandsuggestion` 按 Java 原规则返回未进入世界的具体错误；没有向服务器发送聊天／命令，也没有执行容器点击或 Baritone 行为。
- 可复现：`node src/main/java/im/src-script-mcp/scripts/smoke.mjs --game-dir <已启动的隔离游戏目录>`。协议全覆盖测试使用受控 HTTP 工具桥，不能代替所有工具的游戏内行为验收。

## 2026-09-22 脚本自定义 AI Tools

- Gradle `check remapJar`：263 项 Java 测试、14 项 Harness 测试、12 项真实 stdio MCP 协议测试、前端检查与最终 JAR 资源检查通过。脚本工具专项测试覆盖准备态不可见、冲突拒绝、部分提交清理、失败恢复旧代次、卸载后旧句柄失效、参数错误不误停用、回调故障隔离、输入输出副本、嵌套参数与 UTF-8 输出上限。
- Harness 使用锁定依赖中的真实 `SystemPrompt` 和 `ToolRuntime`，验证同一会话的新工具发现、schema 替换、实际工具执行管线和卸载；注册器变化后重组提示词，Agent scope 的工具过滤仍有效。未使用模型密钥，没有声称真实模型自主编写验收。
- 最终 remap JAR 通过正常 Fabric KnotClient 在隔离目录启动，使用客户端导出的 MCP 服务与官方 stdio 客户端。完整 CustomTools 模板编译通过；同一 MCP 会话完成源码写入、check/load、立即发现与调用、世界要求拒绝、错误源码行、工具故障隔离、跨脚本名称冲突、提交失败回滚、schema/回调热替换、编译失败保留旧代次及卸载。运行时工具成功访问原生 Minecraft 窗口和客户端模块列表。日志：`build/reports/script-custom-tools/production.log`。
- 测试位于主菜单，未连接服务器；附近实体示例已编译，但未验收联网实体查询、跨世界时序或自定义工具发送网络操作。
- 可复现：`node src/main/java/im/src-script-mcp/scripts/custom-tools-smoke.mjs --game-dir <已启动的隔离游戏目录>`。测试会创建并清理随机 QA 脚本，结束后释放 MCP 租约。MCP 还通过受控旧快照延迟，验证加载完成后的发现不会复用提交前快照。
- Agent 技能包的 `quick_validate.py` 通过，自定义工具指南、示例、API 索引和离线 HTML 随客户端分发。

## 2026-09-22 数据包与模块控制 API

- Gradle `check remapJar` 通过：268 项 Java 测试，14 项 Harness、12 项 MCP 测试，前端与 JAR 资源检查。新增设置读写测试覆盖原生模式回调、无效模式拒绝、数值范围/非有限值、全部 Value 类型、部分多选更新的预验证及快照隔离；取消的数据包不会进入 Blink/Delay 队列。
- 最终 remap JAR 在隔离主菜单客户端验证通过：PacketControl/ModuleControl 模板编译；经真实 MCP 工具调用模块开关/按键和七类设置；原生 Feature 生命周期；onSend/onReceive 方向过滤、feature 开关与注销；网络线程上同步取消；断线 send 拒绝。
- 使用没有 socket 的 ClientConnection 触发真实 send Mixin，验证取消和替换不递归触发监听器，没有向外部服务器发送数据。合成收包事件用于验证同步线程语义，不代替真实 NetworkThreadUtils 收包链验收。
- 尚未验证服务器上的序列号交互效果、真实网络收包或跨世界发包。序列号接口复用现有 PendingUpdateManager 路径，不能将编译与主菜单测试表述为联网验收。
- 复现：`node src/main/java/im/src-script-mcp/scripts/control-api-smoke.mjs --game-dir <已启动的隔离主菜单客户端>`。脚本会清理自己创建的 QA 源码、模块和工具；日志在 `build/reports/script-controls/production.log`。
