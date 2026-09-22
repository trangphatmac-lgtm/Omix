# DeepSeek Harness AI

Omix 的 AI 使用独立 Node.js 进程运行 DeepSeek Harness 0.1.6-alpha.1。AIScreen（默认句号键）打开游戏内 Harness 面板；`.ai` 或 `.ai open` 异步准备服务并在本地聊天发送可点击链接，供用户在系统浏览器打开同一个 Harness。模型设置、工作区、会话、权限和通用工具沿用上游界面。默认工作区自动选中，配置模型后即可开始任务；不读取旧 ai.json，旧文件保持原状。

## 运行与恢复

启动 AI 时自动创建当前 Minecraft 游戏根目录下的 `Workspace/`，与 `Omix/` 同级（使用客户端实际的 runDirectory，支持启动器版本隔离目录）。Omix 工作区插件将此目录注册到 Harness；游戏内与外部浏览器每次加载页面时默认选择它，不再要求手动指定文件夹。已有该工作区会话时保留当前会话，否则复用或创建该工作区的空白会话。页面加载后仍可手动切换其他工作区；已有文件、会话和其他工作区不被删除。

Harness 与音乐 WebUI 使用相同的居中圆角面板：常规窗口占画面宽度的 80%、高度的 82%，小窗口自适应为 92% × 90%，四周保留游戏背景并加暗。加载与失败提示也显示在面板内；调整游戏窗口大小时同步更新浏览器尺寸、位置与鼠标交互区域。

`.ai status` 显示状态，`.ai restart` 取消当前工作并重启服务；失败页面可按 R 重试。Esc 隐藏 AI 浏览器，任务仍可继续。游戏退出时终止 AI 进程及其子进程。AI、音乐和 ClickGUI 分别管理页面与服务，AI 故障不停止另外两个功能。

仅在 `home/profiles/omix` 不存在时使用上游 Web 模板初始化。后续启动、重试与升级直接加载已有 profile，保留模型设置、插件配置和会话；不要通过删除数据目录处理 `profile "omix" already exists`。此错误由旧启动脚本重复传入 `--from-default-profile` 引起，更新 JAR 并重启游戏即可使用修复后的脚本。

失败面板换行显示简短原因，最多显示 8 行；进程输出结束时将脱敏后的前 65,536 个字符保存到 `Omix/ai/harness.log`，供排查具体堆栈。诊断文本会对启动 token、API Key 和 bearer 凭据进行脱敏。

运行数据位于游戏目录的 `Omix/ai/home`；运行时按版本和平台解压至 `Omix/ai/runtime`。Node 24.18.0 继续复用 `Omix/music` 下的既有下载缓存，使用 SHA-256 校验；没有 Node 缓存时首次使用仍需要联网下载 Node。模型调用和联网工具按自身用途访问网络。

Harness 生产代码、Web 资源、pnpm 11.7.0 和所需原生依赖随 JAR 打包；不会在游戏启动时执行 npm install 或下载 Harness。JAR 内含 common.zip 和 Windows/macOS/Linux × x64/ARM64 的六份平台归档，每台电脑仅解压 common 与对应平台。Linux 使用与现有官方 Node 发行版一致的 glibc 环境。开发依赖、测试、源码映射和非目标架构被裁剪；保留终端、会话锁、沙箱、图像处理与搜索需要的组件。上游为开发预览版，更新必须重新锁定依赖并验证打包产物。

## 架构与协议

CEF 直接访问 Harness 独立的本机动态端口。启动 token 通过上游根页面交换为 HttpOnly cookie，日志不记录原始 token。AI 页面使用独立浏览器，不使用 ClickGUI 的路由、页面确认或关闭动画。

`.ai` 的浏览器链接采用同一认证流程：可见聊天文本只包含本机地址，完整认证 URL 放在链接点击事件中；命令不依赖 CEF 页面就绪，也不会自动打开浏览器。服务重启后重新执行 `.ai` 获取新链接。

`cn.omix.util.ai` 管理运行时、独立 Java HTTP 桥接、游戏工具及容器状态。`cn.omix.util.node` 管理共享 Node 下载与平台识别。Node 插件位于 `src-web/src-ai-harness/plugin/omix.mjs`，通过 Cordis 注册原有 22 个游戏工具及 14 个脚本开发工具和每步游戏上下文；详见 [AI Tools](ai-tools.md)。`plugin/workspace/` 是独立的 Host/Client 双端插件，使用上游 workspaceRegistry 注册目录、uiWorkspace 选择工作区，不修改 Harness 核心。游戏工具在 Minecraft 主线程执行，模型与网络请求在进程/工作线程中运行。

桥接仅监听 127.0.0.1，要求启动时生成的 bearer token、精确 Host，拒绝浏览器 Origin，不提供 CORS。Harness 凭据通过子进程环境传入；外部 MCP 通过游戏目录中权限受限的 Omix/development/bridge.json 发现实例。桥由 Client 独立管理，随客户端启动/关闭，重启 Harness 不终止外部会话。

| 接口 | 内容 |
| --- | --- |
| POST /v2/sessions | 建立独立 Agent 租约，返回 agentId 与 leaseMillis |
| POST /v2/agents/{id}/heartbeat | 续租（60 秒）；过期清理独占与调用占用 |
| GET /v2/snapshot | protocolVersion=2、instanceId、worldEpoch、tools、gameContext、toolContext |
| GET /v2/reference | 与 JAR 内模块、命令、工具参考一致的 text |
| POST /v2/calls | id、agentId、worldEpoch、name、arguments；返回 ok/value 或 ok=false/error |
| DELETE /v2/calls/{id} | 取消执行或为尚未到达的调用保留取消记录 |
| POST /v2/agents/{id}/release | 取消未完成调用、失效容器快照、释放游戏工具占用 |

调用 ID 不重复执行；最近 256 次之外的已完成结果可过期，但执行标识继续保留。每会话最多 2048 个调用、总计最多 16384 个；关闭会话清理记录和占用，v2 不依赖重启 Harness 释放配额。v1 路由保留兼容测试/旧插件，新插件统一使用 v2 租约。单次 HTTP 工具等待 30 秒；插件网络超时也会发送取消请求。多会话可使用通用能力，但同时只能一个 Agent 回合调用游戏工具。世界/玩家实例变化后拒绝旧 worldEpoch。

## 专用插件开发

### PacketsLogger 管线

`AiPacketTools` 将 `configurepacketslogger`、`getpacketlogs`、`clearpacketlogs` 注册到现有 Java schema；Harness 插件通过 snapshot 自动发现，并沿用 POST /v2/calls 的鉴权、worldEpoch、调用去重、Agent 独占与主线程执行，不新增网络端口或工具传输协议。系统上下文提供工具使用说明，包正文仅在显式读取工具结果中返回，不自动注入每一步上下文。

捕获仍通过 PacketsLogger 的收发观察、方向过滤、名单和移动包精简；`PacketLogBuffer` 同时写入聊天队列与 `PacketLogHistory` 的 512 条环形历史。Chat Output 可关闭，聊天排队/消费不影响 AI 历史。每条只保留有界文本快照，不把 Packet、世界或 ByteBuf 对象留在历史中；日志模块的会话身份使用弱引用，停止采集不会强持有旧世界。

历史读取是非消费式分页，游标包含独立会话标识与序号，返回溢出及缺失计数；关闭模块保留历史，重启捕获、显式清空或世界/玩家/连接变化使历史和游标失效。模块关闭期间的会话变化在工具读取前复核。配置操作只应用通过完整校验的部分更新，立即发布过滤快照；工具回合结束保持用户模块设置，Agent 临时诊断需自行恢复本次更改。返回的包内容始终作为不可信游戏数据处理，不保证服务器已接受发送。

同一 Java schema、参数校验与后台编译服务同时提供给 Harness、Script Studio 和独立 stdio MCP。Node MCP 使用官方 SDK，离线文档可用，并自动发现已连接客户端的游戏工具；配置与 Agent 技能包见 [MCP 开发指南](script/mcp.md)。插件返回 canonical JSON value 或截图附件，供 Harness 工具卡片显示。安装其他插件应保持与锁定的 Harness 版本兼容。

运行时中的 `launch.mjs plugin <pnpm 参数>` 使用内置 pnpm 管理 `omix` profile。例如，以已缓存 Node 执行 `launch.mjs plugin add <插件包>`，并设置 `DSH_HOME` 为该游戏目录的 `Omix/ai/home`。安装插件需要网络时由 pnpm 访问网络；安装不改变内置 Harness 版本。Omix 核心插件由启动 overlay 挂载，用户插件保存在独立 profile 内。

## 构建和验收

Gradle 的 installAiHarness、bundleAiHarness、testAiHarness 接入构建流程。打包脚本以 package-lock.json 的 URL 和 integrity 获取其他目标平台的 optional dependencies，仅发生在构建时，并输出可复现 ZIP 与 SHA-256 manifest。无 Node 二进制的约束在 JAR 内容检查中覆盖所有 Harness 归档。

使用 `node src-web/src-ai-harness/scripts/smoke.mjs <解压运行时路径>` 检查隔离 profile 首次启动、同一数据目录重启、用户覆盖配置保留、插件激活、Web 页面与认证；使用 `npm --prefix src-web/src-ai-harness test` 运行插件协议测试。Java 测试覆盖参数、容器快照、串行化、争用、世界切换、重复调用、取消、桥接鉴权和启动诊断脱敏。

跨平台归档完整性不能代替在目标系统实际运行；各系统的原生组件启动、CEF 中文输入、文件选择、缩放、流式输出、权限弹窗以及音乐/ClickGUI 回归需要对应环境验收。

自动化跨平台检查由 `.github/workflows/harness-runtime.yml` 提供：同一组归档在六种原生 runner 上分别解压，并运行会话持久化、PTY 终端、图像处理和 Web 启动认证检查。运行器标签依据 [GitHub 官方文档](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)；新增工作流不代表已在这些平台执行成功。

### 本次实现的验收记录（2026-09-16）

- `gradle check` 通过：146 项 Java 测试、2 项插件测试（包含重复派发标识和运行中取消）、音乐服务测试、前端构建及 JAR 资源检查；另行执行的 4 项 WebUI 导航测试通过。
- macOS ARM64 从最终归档解压后，实际完成 Harness 启动、token/cookie 认证、未认证 API 拒绝、会话写入后重新打开、PTY 输出和图像处理检查。
- 独立浏览器实际显示首次引导、模型配置入口、工作区、设置和插件列表；Omix 插件显示为已启用。测试使用隔离 profile，没有使用真实模型密钥。
- 六平台 CI 尚未执行。开发游戏客户端已启动，但 CEF 依赖准备未完成，尚未验收游戏内中文输入、流式输出、工具卡片、权限弹窗、文件选择、缩放、Esc/隐藏恢复，以及 ClickGUI/音乐的游戏内交互回归。这些仍是发布验收项。

### 会话激活并发修复（2026-09-22）

锁定的 Harness 0.1.6-alpha.1 对创建／接管会话与历史读取触发的恢复使用独立的去重表，两条路径并发时可能发生 `SessionAlreadyOwnedError`。Omix 的 `plugin/session-activation.mjs` 在 sessionController 内按会话 ID 串行执行激活，保留原来的工作区、预设和子代理校验；不同会话和已经开始的模型运行仍可并发。失败不堵塞后续重试，插件卸载时拒绝尚未开始的激活并恢复原方法。此适配器依赖锁定版本的内部接口，升级 Harness 必须运行 `session-activation.test.mjs` 并复核接口；不删除历史记录或绕过持久化写锁。

### 外部 MCP 游戏能力对齐

外部 MCP 与 Harness 共用 `MinecraftGameBridge` 和 `MinecraftCommandToolExecutor`，22 个游戏工具在 MCP 初始化时即可发现，另有 14 个脚本工具和 3 个连接／参考辅助工具。静态游戏 schema 由 Java 声明自动导出，连接后同步实时描述和参数并发出列表更新通知；`omix_status` 返回游戏内 AI 相同的上下文，`omix_reference` 返回相同文档。参见 [MCP 完整能力](script/mcp.md)。

脚本通过 `tools.register` 扩展 AI 能力，所有运行时 schema 由同一桥接快照提供。Harness 每次组装发现变化后更新真实工具注册器并重新组装，保留 scope 过滤、排序和 PTC 呈现；MCP 在加载任务轮询和后台发现中同步工具列表。回调执行在客户端线程，绑定脚本代次；参见 [自定义工具](script/custom-tools.md)。
