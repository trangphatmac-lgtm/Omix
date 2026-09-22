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
