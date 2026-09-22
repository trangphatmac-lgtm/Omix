# Omix Java 脚本

适用版本：Minecraft 1.21.11、Java 21、当前客户端锁定的 Yarn。API v1。脚本是可信的同进程 Java 代码，可以直接调用客户端和依赖；不是安全沙箱，也不兼容 Raven-bs 脚本。

## 五分钟开始

1. 打开 Render → Scripts，或输入 `.script open`。
2. 输入文件名 `MySprint`，选择 Sprint 模板，创建。文件位于实际游戏目录 `Omix/scripts/MySprint.java`。
3. 点击「检查」，等 job 变为 checked。源码保存、检查均不会运行。
4. 点击「加载」，等 job 变为 loaded，检查实际 generation。在 ClickGUI 中启用 Script Sprint。
5. 编辑、保存后点击「重载」。查看磁盘 hash、运行 hash 和日志，确认运行的是新版本。
6. 「卸载」释放该脚本受管资源，源码仍保留。删除源码是单独操作。

脚本只写字段、方法、内部类与 import，不写 package 或外层类。`void onLoad()` 注册功能；每个模块拥有独立开关与回调。一份文件可以注册多个模块、模式、命令、HUD。不要在 onLoad 中发包、移动或启用模块；需要提交后的动作使用 `script.afterCommit(...)`。

```java
void onLoad() {
    var feature = modules.register("hello", "Script Hello", Category.Player);
    feature.onEnable(() -> log("hello"));
}
```

Category 的实际成员以 api.json/编译器为准；最稳妥的入门代码是随附 Sprint 模板。

## 文档导航

- lifecycle.md：源码、编译、热加载、资源与异常
- api-guide.md / api.json / sdk.md：API 分组、实际声明与原生 util
- common-knowledge.md：现代 Minecraft 开发知识与线程
- modes.md / mode-hosts.json：整模块接管、返回值 hook
- tools.md / tools.json：开发工具及参数 schema
- mcp.md：外部 Agent、离线工具、会话管理
- examples/：可编译模板；omix-script/SKILL.md：Agent 入口
- validation.md：自动检查和需要真实游戏验证的项目

`.script` 支持 open/list/create/check/load/reload/unload/status/logs/mcp。`.script mcp` 准备 Node 运行时并输出外部配置路径。首次编译建立 named 类路径缓存，可能明显慢于后续检查，任务可查询和取消。新文件不会自动运行；启动仅恢复上次加载清单。
