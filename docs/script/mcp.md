# 外部 Agent 与客户端 stdio MCP

客户端启动即提供独立的本地工具桥，重启 AI Harness 不会关闭桥。连接描述文件是实际游戏目录 Omix/development/bridge.json，包含 loopback endpoint、随机 token、protocolVersion 和 instanceId；仅连接 127.0.0.1，HTTP 服务校验 Host、Bearer 并拒绝跨站 Origin。不要把 token 写入提示词或提交版本控制。

首次启动导出 Omix/scripts/.agent，包含 mcp/server.mjs、依赖、离线文档和 skills/omix-script。运行 `.script mcp` 复用客户端 Node 准备流程，生成使用绝对路径的 mcp-config.json。外部 MCP 配置示例：

```json
{"mcpServers":{"omix":{"command":"/absolute/path/to/node","args":["/game/Omix/scripts/.agent/mcp/server.mjs","--game-dir","/game"]}}}
```

必须指定 --game-dir，不能凭当前目录猜测实例。关闭游戏仍可启动 MCP，script_reference/script_api/script_templates 和 docs resources 离线可用，omix_status 明确报告 offline。首次 `tools/list` 即包含全部 22 个游戏工具和 14 个脚本开发工具，不需要先调用 `omix_status` 或等待后台发现；另有 `omix_status`、`omix_reference`、`release_game_session`，当前共 39 个工具。游戏未启动时，调用游戏工具会明确返回离线错误。连接恢复后无需重启 MCP 即可调用，运行时新增工具会自动发现。每 10 秒或调用 `omix_status` 时同步 Java 工具桥的实时定义：新增、参数／描述更新、移除运行时扩展均通过 MCP 工具列表更新通知发布。

stdio 输出只用于协议，日志写 stderr。实现使用官方 @modelcontextprotocol/server SDK。Ctrl-C/断开/空闲会释放游戏工具会话。活动调用每20秒续租，Java租约60秒；每个MCP客户端独立agentId，多Agent读文档和源码不争用游戏独占，执行游戏操作遵循现有worldEpoch与owner规则。明确取消调用会请求取消，已发生的原生副作用无法撤回。源码写入使用 expectedHash 防止相互覆盖。

script_evaluate 接收方法体，需 return 对象，例如 `return mc.player == null ? "menu" : mc.player.getEntityPos();`，后台编译后在客户端线程短暂运行，不提供循环超时沙箱。不要提交阻塞/无限循环；注册持久功能应使用脚本文件。script_screenshot 返回PNG与保存路径；MCP将图片作为image content输出。

将导出的 skills/omix-script 目录复制到外部 Agent 的技能目录，或让 Agent 阅读 SKILL.md；不要复制单一 SKILL.md 而遗漏 references 与 examples。

`.agent/launch-mcp.sh`（macOS/Linux）或 `launch-mcp.cmd`（Windows）使用当前实例目录启动 stdio 服务。运行 `.script mcp` 后，启动器与配置使用客户端准备的 Node 绝对路径；下次启动客户端时保留仍有效的路径。也可手动执行 `node server.mjs --game-dir <实际游戏目录>`。

## 与游戏内 AI 共用游戏能力

外部 Agent 直接调用 `MinecraftCommandToolExecutor` 的原名称，沿用原 JSON 参数：

| 能力 | 工具 |
| --- | --- |
| Minecraft、客户端和寻路命令 | `run_minecraft_command`、`run_client_command`、`run_baritone_command`、`getcommandsuggestion` |
| 背包、世界与目标查询 | `getinventory`、`getnearbyblock`、`getlookingblock`、`getnearbyentity`、`getlookingentity`、`getspecificblock` |
| 客户端配置、计分板与聊天 | `getallconfig`、`getscoreboard`、`getchatmessage`、`sendchatmessage` |
| 容器操作 | `getnearbycontainer`、`opencontainer`、`getcontainer`、`clickcontainerslot`、`closecontainer` |
| 数据包观察 | `configurepacketslogger`、`getpacketlogs`、`clearpacketlogs` |

例如直接调用 `getinventory`（`{}`）、`run_client_command`（`{"command":".toggle Sprint"}`）、`getpacketlogs`（`{"limit":20,"direction":"SENT"}`）。全部参数见 [游戏工具说明](game-tools.md) 与生成的 `game-tools.json`。该 JSON 由实际 Java `buildSnapshot` 导出，Java 测试验证与执行器一致，不维护另一份手写参数定义。

开始任务先调用 `omix_status`，读取与游戏内 AI 相同的 `gameContext`、`toolContext`，以及 `worldEpoch`、`instanceId` 和 `availableTools`。`omix_reference` 在线读取同一 Java 文档接口，离线回退到相同文档集的打包副本；也可通过 resource `omix://script/client-reference.md` 读取。参数约束和当前可用命令以最新实时工具定义为准。

完整工具可访问性沿用游戏内 AI 的执行约束：需要世界的工具必须进入游戏；Baritone 需要安装；`.ai`、`.chat` 仍不能通过命令工具调用；聊天会实际发送给服务器。容器操作必须使用最新 `snapshotId`，游戏独占、世界切换和取消规则对内外部 Agent 一致。没有为了外部访问增加绕过校验的原生命令入口。

升级客户端并重新启动后，新的开发包会导出到当前游戏目录。已有 MCP 配置继续有效；重新连接外部 MCP 进程以载入更新后的 `server.mjs` 和初始工具列表。

## 运行时自定义工具

通过脚本 `tools.register` 注册的 `custom_` 工具也会动态出现在 `tools/list`。轮询 `script_job` 至 loaded 后，MCP 在返回前刷新注册器并发送列表变化通知；框架应重新获取工具列表。工具卸载或出错停用后从后续列表移除。界面／其他 Agent 的变更最迟在下一轮后台发现时同步，也可调用 `omix_status` 立即刷新。详见 [自定义 AI Tools](custom-tools.md)。
