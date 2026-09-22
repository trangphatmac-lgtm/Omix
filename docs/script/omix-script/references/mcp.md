# 外部 Agent 与 stdio MCP

客户端启动即提供独立的本地工具桥，重启 AI Harness 不会关闭桥。连接描述文件是实际游戏目录 Omix/development/bridge.json，包含 loopback endpoint、随机 token、protocolVersion 和 instanceId；仅连接 127.0.0.1，HTTP 服务校验 Host、Bearer 并拒绝跨站 Origin。不要把 token 写入提示词或提交版本控制。

首次启动导出 Omix/scripts/.agent，包含 mcp/server.mjs、依赖、离线文档和 skills/omix-script。运行 `.script mcp` 复用客户端 Node 准备流程，生成使用绝对路径的 mcp-config.json。外部 MCP 配置示例：

```json
{"mcpServers":{"omix":{"command":"/absolute/path/to/node","args":["/game/Omix/scripts/.agent/mcp/server.mjs","--game-dir","/game"]}}}
```

必须指定 --game-dir，不能凭当前目录猜测实例。关闭游戏仍可启动 MCP，script_reference/script_api/script_templates 和 docs resources 离线可用，omix_status 明确报告 offline。连接恢复后自动发现原有22个游戏工具与脚本工具，MCP发送工具列表更新。

stdio 输出只用于协议，日志写 stderr。实现使用官方 @modelcontextprotocol/server SDK。Ctrl-C/断开/空闲会释放游戏工具会话。活动调用每20秒续租，Java租约60秒；每个MCP客户端独立agentId，多Agent读文档和源码不争用游戏独占，执行游戏操作遵循现有worldEpoch与owner规则。明确取消调用会请求取消，已发生的原生副作用无法撤回。源码写入使用 expectedHash 防止相互覆盖。

script_evaluate 接收方法体，需 return 对象，例如 `return mc.player == null ? "menu" : mc.player.getEntityPos();`，后台编译后在客户端线程短暂运行，不提供循环超时沙箱。不要提交阻塞/无限循环；注册持久功能应使用脚本文件。script_screenshot 返回PNG与保存路径；MCP将图片作为image content输出。

将导出的 skills/omix-script 目录复制到外部 Agent 的技能目录，或让 Agent 阅读 SKILL.md；不要复制单一 SKILL.md 而遗漏 references 与 examples。

`.agent/launch-mcp.sh`（macOS/Linux）或 `launch-mcp.cmd`（Windows）使用当前实例目录启动 stdio 服务。运行 `.script mcp` 后，启动器与配置使用客户端准备的 Node 绝对路径；下次启动客户端时保留仍有效的路径。也可手动执行 `node server.mjs --game-dir <实际游戏目录>`。
