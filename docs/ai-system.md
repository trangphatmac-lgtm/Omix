# DeepSeek Harness AI

Omix 的 AI 使用独立 Node.js 进程运行 DeepSeek Harness 0.1.6-alpha.1。AIScreen（默认句号键）与 `.ai open` 打开完整 Harness WebUI，模型设置、工作区、会话、权限和通用工具沿用上游界面。先选择工作区并配置模型，再开始任务；不读取旧 ai.json，旧文件保持原状。

## 运行与恢复

`.ai status` 显示状态，`.ai restart` 取消当前工作并重启服务；失败页面可按 R 重试。Esc 隐藏 AI 浏览器，任务仍可继续。游戏退出时终止 AI 进程及其子进程。AI、音乐和 ClickGUI 分别管理页面与服务，AI 故障不停止另外两个功能。

运行数据位于游戏目录的 `Omix/ai/home`；运行时按版本和平台解压至 `Omix/ai/runtime`。Node 24.18.0 继续复用 `Omix/music` 下的既有下载缓存，使用 SHA-256 校验；没有 Node 缓存时首次使用仍需要联网下载 Node。模型调用和联网工具按自身用途访问网络。

Harness 生产代码、Web 资源、pnpm 11.7.0 和所需原生依赖随 JAR 打包；不会在游戏启动时执行 npm install 或下载 Harness。JAR 内含 common.zip 和 Windows/macOS/Linux × x64/ARM64 的六份平台归档，每台电脑仅解压 common 与对应平台。Linux 使用与现有官方 Node 发行版一致的 glibc 环境。开发依赖、测试、源码映射和非目标架构被裁剪；保留终端、会话锁、沙箱、图像处理与搜索需要的组件。上游为开发预览版，更新必须重新锁定依赖并验证打包产物。

## 架构与协议

CEF 直接访问 Harness 独立的本机动态端口。启动 token 通过上游根页面交换为 HttpOnly cookie，日志不记录原始 token。AI 页面使用独立浏览器，不使用 ClickGUI 的路由、页面确认或关闭动画。

`cn.omix.util.ai` 管理运行时、独立 Java HTTP 桥接、游戏工具及容器状态。`cn.omix.util.node` 管理共享 Node 下载与平台识别。Node 插件位于 `src/main/java/im/src-ai-harness/plugin/omix.mjs`，通过 Cordis 注册现有 19 个工具和每步游戏上下文；详见 [AI Tools](ai-tools.md)。游戏工具在 Minecraft 主线程执行，模型与网络请求在进程/工作线程中运行。

桥接仅监听 127.0.0.1，要求启动时生成的 bearer token、精确 Host，拒绝浏览器 Origin，不提供 CORS。凭据仅通过子进程环境传入插件。

| 接口 | 内容 |
| --- | --- |
| GET /v1/snapshot | protocolVersion=1、worldEpoch、tools、gameContext、toolContext |
| GET /v1/reference | 与 JAR 内模块、命令、工具参考一致的 text |
| POST /v1/calls | id、agentId、worldEpoch、name、arguments；返回 ok/value 或 ok=false/error |
| DELETE /v1/calls/{id} | 取消执行或为尚未到达的调用保留取消记录 |
| POST /v1/agents/{id}/release | 取消未完成调用、失效容器快照、释放游戏工具占用 |

调用 ID 不重复执行；最近 256 次之外的已完成结果可过期，但执行标识继续保留。单次服务生命周期最多 4096 个调用，达到后需 `.ai restart`；这是防止重复提交与无界内存增长的边界。单次 HTTP 工具等待 30 秒；插件网络超时也会发送取消请求。多会话可使用通用能力，但同时只能一个 Agent 回合调用游戏工具。世界/玩家实例变化后拒绝旧 worldEpoch。

## 专用插件开发

第一版直接使用 Harness 插件体系，没有另建 MCP 服务。Java 提供游戏 schema 与参数校验；插件返回 canonical JSON value，让工具显示为 Harness 通用卡片。未来可以增加游戏服务封装、独立工具包或 Client 插件卡片。安装其他插件应保持与锁定的 Harness 版本兼容。

运行时中的 `launch.mjs plugin <pnpm 参数>` 使用内置 pnpm 管理 `omix` profile。例如，以已缓存 Node 执行 `launch.mjs plugin add <插件包>`，并设置 `DSH_HOME` 为该游戏目录的 `Omix/ai/home`。安装插件需要网络时由 pnpm 访问网络；安装不改变内置 Harness 版本。Omix 核心插件由启动 overlay 挂载，用户插件保存在独立 profile 内。

## 构建和验收

Gradle 的 installAiHarness、bundleAiHarness、testAiHarness 接入构建流程。打包脚本以 package-lock.json 的 URL 和 integrity 获取其他目标平台的 optional dependencies，仅发生在构建时，并输出可复现 ZIP 与 SHA-256 manifest。无 Node 二进制的约束在 JAR 内容检查中覆盖所有 Harness 归档。

使用 `node src/main/java/im/src-ai-harness/scripts/smoke.mjs <解压运行时路径>` 检查隔离 profile 启动、插件激活、Web 页面与认证；使用 `npm --prefix src/main/java/im/src-ai-harness test` 运行插件协议测试。Java 测试覆盖参数、容器快照、串行化、争用、世界切换、重复调用、取消与桥接鉴权。

跨平台归档完整性不能代替在目标系统实际运行；各系统的原生组件启动、CEF 中文输入、文件选择、缩放、流式输出、权限弹窗以及音乐/ClickGUI 回归需要对应环境验收。

自动化跨平台检查由 `.github/workflows/harness-runtime.yml` 提供：同一组归档在六种原生 runner 上分别解压，并运行会话持久化、PTY 终端、图像处理和 Web 启动认证检查。运行器标签依据 [GitHub 官方文档](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)；新增工作流不代表已在这些平台执行成功。

### 本次实现的验收记录（2026-09-16）

- `gradle check` 通过：146 项 Java 测试、2 项插件测试（包含重复派发标识和运行中取消）、音乐服务测试、前端构建及 JAR 资源检查；另行执行的 4 项 WebUI 导航测试通过。
- macOS ARM64 从最终归档解压后，实际完成 Harness 启动、token/cookie 认证、未认证 API 拒绝、会话写入后重新打开、PTY 输出和图像处理检查。
- 独立浏览器实际显示首次引导、模型配置入口、工作区、设置和插件列表；Omix 插件显示为已启用。测试使用隔离 profile，没有使用真实模型密钥。
- 六平台 CI 尚未执行。开发游戏客户端已启动，但 CEF 依赖准备未完成，尚未验收游戏内中文输入、流式输出、工具卡片、权限弹窗、文件选择、缩放、Esc/隐藏恢复，以及 ClickGUI/音乐的游戏内交互回归。这些仍是发布验收项。
