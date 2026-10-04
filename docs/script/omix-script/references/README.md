# Omix Java 脚本

适用版本：Minecraft 1.21.11、Java 21、当前客户端锁定的 Yarn。API v1。脚本是可信的同进程 Java 代码，可以直接调用客户端和依赖；不是安全沙箱，也不兼容 Raven-bs 脚本。

## 五分钟开始

1. 打开 Render → Scripts，或输入 `.script open`。
2. 输入文件名 `MySprint`，选择 Sprint 模板，创建。文件位于实际游戏目录 `Omix/scripts/MySprint.java`。
3. 点击「检查」，等 job 变为 checked。源码保存、检查均不会运行。
4. 点击「加载」，等 job 变为 loaded，检查实际 generation。在 ClickGUI 中启用 Script Sprint。
5. 编辑、保存后点击「重载」。查看磁盘 hash、运行 hash 和日志，确认运行的是新版本。
6. 「卸载」释放该脚本受管资源，源码仍保留。删除源码是单独操作。

脚本只写字段、方法、内部类与 import，不写 package 或外层类。`void onLoad()` 注册功能；每个模块拥有独立开关与回调。一份文件可以注册多个模块、模式、命令、HUD 和 AI Tools。不要在 onLoad 中发包、移动或启用模块；需要提交后的动作使用 `script.afterCommit(...)`。

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
- packet-module-control.md：发包拦截、取消/替换、序列号发包与其它模块设置读写
- custom-tools.md：注册可被游戏内 AI／外部 MCP 调用的自定义工具
- tools.md / tools.json：开发工具及参数 schema
- mcp.md：外部 Agent、离线工具、会话管理
- examples/：可编译模板；omix-script/SKILL.md：Agent 入口
- validation.md：自动检查和需要真实游戏验证的项目

`.script` 支持 open/list/create/check/load/reload/unload/status/logs/mcp。`.script mcp` 准备 Node 运行时并输出外部配置路径。首次编译建立 named 类路径缓存，可能明显慢于后续检查，任务可查询和取消。新文件不会自动运行；启动仅恢复上次加载清单。


## 第三方 Mod 依赖

默认编译依赖仅包含 Minecraft、Omix、Fabric 以及本客户端构建所需的库（包括 MCEF）。清单在构建时生成；运行时只选择其中已加载的 Mod、它们的必需依赖及内嵌库，不再收集所有已安装 Mod。启动器 Java 类路径也按清单筛选，避免无关 Mod 被重新带入。

需要调用其它 Mod 的 API 时，在脚本开头、第一条 import／字段／方法之前声明 Fabric Mod ID：

```java
// @depends sodium, iris
```

可用逗号或空格分隔，也可写多行 `// @depends`。填写 Mod ID，不是 JAR 文件名或路径。声明只影响当前脚本，会自动包含这些 Mod 的必需 `depends` 和内嵌库，不自动加入 `recommends`／`suggests`。缺少已声明的 Mod 会在「收集依赖」阶段明确报错；不会下载、安装或启动新的 Mod。

不调用第三方 Mod API 的脚本无需修改。移除声明后重新检查／加载，会恢复默认依赖范围；其它脚本不继承它的声明。`script_evaluate` 也可在待执行代码开头使用同样的注释。块注释、字符串、文本块或 Java 代码之后的注释不作为依赖声明。

这是编译依赖选择，脚本仍是可信的同进程 Java 代码，不构成运行时权限隔离。

## 编译性能与任务恢复

- Java 编译和类名映射在独立 Java 进程执行，使用游戏自带的 Java 运行时；不要求安装外部 JDK。编译进程最多使用 768 MiB 堆和 2 个可用 CPU，避免映射抢占游戏进程资源。
- 每次编译进程限时 180 秒。超时会终止进程并将任务标记为 `failed`，错误包含停止时的阶段；后续任务可以继续运行。取消排队任务不会启动编译；取消正在编译的任务会终止实际进程，释放队列。取消和失败不会替换已加载的脚本。
- 同一脚本再次加载/重载会取消尚未应用的旧加载请求。已经进入 `applying` 的生命周期仍在客户端线程执行。
- 首次编译会在 `.cache/classpath` 创建本地 named 类路径。仅提取当前 JVM 选中的类，避免 multi-release JAR 为多个 Java 版本重复建立全量映射图；依赖或 mappings 变化会产生新的缓存。
- 依赖路径支持空格、中文、方括号及 `%`、`#`、`+`、`!` 等字符，包括 Windows 上的 `Release 2.2.11` 游戏目录以及 `[钠] sodium-fabric-….jar`、`[钠 · 扩展] sodium-extra-fabric-….jar` 文件名。读取 Fabric JAR 根路径时保留已有 URI 转义，并补齐文件路径所需的方括号等转义，避免在「收集依赖」阶段报 `Illegal character in path`；嵌套 JAR 仍提取为编译器可读取的本地依赖。
- 会话内保留最近 8 份成功编译结果（单份不超过 2 MiB）。源码不变时，编译检查后加载、再次检查和重载可复用字节码；各运行代次仍有独立 JAR 和类加载器。源码变化会重新编译，重启后首次编译重新验证依赖。
- 编译诊断显示当前阶段与耗时。`script_job` 增加 `phase`、`startedAt`、`finishedAt`（毫秒时间戳，未开始/未结束为 0）；原有任务状态不变。`waiting_client` 表示编译已完成，正在等待客户端线程应用。
- 阶段进度写入失败（例如 Windows 正在读取进度文件时拒绝替换）不会中断编译；界面可能暂时保留上一阶段，最终编译结果与错误诊断仍正常返回。

编译超时只保护编译和映射过程。脚本仍是可信的同进程 Java 代码，`onLoad`、字段初始化、`evaluate` 等运行代码应避免死循环或阻塞客户端线程。
