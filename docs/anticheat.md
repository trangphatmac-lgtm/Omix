# AntiCheat 移植说明

`AntiCheat` 位于 **World** 分类，可通过 `.toggle AntiCheat` 或 ClickGUI/WebUI 开启。仅在多人世界检测，默认开启 AutoBlock、Legit Scaffold、NoSlow、Nuker、Bed Nuker 和 Scaffold；六个实验性检测默认关闭。每个玩家、每个检测项默认间隔 10 秒提示一次。

现已另行增加 HypixelACV2 Raven-bS 脚本的 **101 项检测**，与本文原有 12 项合计 113 项。开启默认关闭的 `Raven Checks` 总开关后运行，提供五组独立子开关及 VL/冷却配置。也已移植累计嫌疑评分、默认 N 键切换的 CheatUI、50/80/100 分提醒和可选评分举报门槛；原有 12 项的有效告警各加默认 8 分。清单、权重、配置与适配边界见 [Raven 检测扩展](anticheat-raven.md)。下文未注明 Raven 的规则描述原有 12 项。

这是对远端实体和服务器更新的启发式观察。客户端不能看到其他玩家完整的输入或服务器校验过程，因此提示使用 `suspected`，不代表确定作弊。尤其挖掘阶段、网络插值、游戏机制和地图刷新可能产生误报。完整配置表见 [World 模块](modules/world.md#anticheat)。

## 参考与适配

参考文件为用户提供的 OpenB4 `xyz.daikirai.module.other.AntiCheatModule`，原代码使用 Minecraft 1.8/Forge API。当前实现针对 Minecraft 1.21.11/Fabric：

| 原实现 | Omix 适配 |
| --- | --- |
| `onPreMotion` 与内部 Tracker | 客户端 `TickEvent`；每个实体独立 `AntiCheatTracker`，首次观察只建立基线 |
| Button/Slider 设置与 `link()` | 标准 BoolValue/NumberValue；模块管理器注册后供界面、命令和配置系统使用 |
| S25 / S23 / S22 | BlockBreakingProgress / BlockUpdate / ChunkDeltaUpdate S2C 包 |
| 原版剑格挡 | 现代 `isBlocking()`，通常为盾牌格挡；不恢复 1.8 剑格挡机制 |
| BotCheck / Friends / sameTeam | 当前 AntiBot、FriendManager、原版队伍和 Teams 模式 |
| LatencyAlerts.lastPacket | 已有 PacketLogEvent 的未取消接收时间，仅用于 Blink 新鲜度判断 |
| Hypixel 状态服务 | 实际服务器目标地址（含 FisProxy）与英文侧边栏线索 |
| BedNuker 跟随 Nuker | 保留 Nuker 总开关，另加 Bed Nuker 子开关，并验证真正的床头尾 |
| 单条待发 report/party 字符串 | 两个有界队列，每队最多 64 个玩家；举报优先，共享每秒一条限制 |

自动举报、队伍消息默认关闭，仅允许目标主机为 `hypixel.net` 或其子域名时发送。只使用游戏档案中的合法玩家名组装命令，文本提示保留悬停信息和点击 WDR 的入口。界面打开时暂缓自动发送；目标离开/变为忽略对象、关闭对应选项或重置世界后取消待发送动作。每个 UUID 在本次世界会话中只自动举报一次。可启用 `Suspicion Report`，让自动举报还必须达到 `Report at %`（默认 100）；修改门槛会在发送前重新校验排队目标的分数。

`Disable outside minigames` 默认关闭。开启后要求 Hypixel 主机和进行中侧边栏线索，如 `Diamond II`、`Next Event:`、`Refill`、`Time Left:`、`Survivors:`、`Beds Broken:` 或 `Round:`，遇到 `lobby`、`waiting`、`starting in`、`replay` 则跳过。这不是 OpenB4 完整的 Hypixel 游戏状态枚举；未识别或本地化侧边栏可能导致不检测。

原版 `Add cheaters as enemy` 没有移植：Omix 目前只有好友系统，没有敌人标记及消费该标记的功能。原版 `ScriptEvents.antiCheatFlag` 也没有同名的 Omix 脚本事件，不添加无效设置或空回调。模块仍可通过现有通用模块控制接口配置。

## 状态与线程

所有检测辅助类放在 `src/main/java/cn/omix/util/world/anticheat/`，对应测试放在 `src/test/java/cn/omix/util/world/anticheat/`。模块本体位于 `src/main/java/cn/omix/module/impl/world/AntiCheat.java`。

方块钩子接在 ClientPlayNetworkHandler 的 `forceMainThread` 返回后、原版修改世界之前。网络线程只更新接收时间戳，不访问世界/玩家或修改挖掘表；原版处理前已取消的包不会进入方块检测。增量包在任何方块应用前读取旧状态，位置复制为不可变 BlockPos。检测本身不取消、替换或重发游戏包。

移动跟踪按实体实例隔离，离开可见/有效玩家集合后删除；切世界、切换本地玩家、关闭模块清空检测记录。累计嫌疑分及阈值提醒按 UUID 保存，在同一连接内换图/重生时保留，断线、换连接或关闭模块清空。单 tick 水平单轴位移达到 3 格或竖直位移达到 8 格时丢弃连续动作证据，避免传送沿用旧样本。好友始终忽略，队友过滤由独立选项控制，飞行/游泳等正常特殊移动状态不参与原有检测。

挖掘表修正了参考中的 `breakingBlocks.remove(anim)` 键类型错误，按位置消费一次；必须由同一实体对同一位置结束动画才能关联阶段。结束记录只保留 1 秒，进行中记录保留 2 秒，两个表各限制 1024 条。Nuker 的最近玩家推断限制在方块中心 6 格内，不再把远处玩家归因为破坏者；仍不能证明破坏归属。

## 验证

使用 `compileJava` 验证现代 API 与 Mixin 编译；单元测试覆盖动作阈值、开关、传送重置、NoFall 伤害/禁用清理、挖掘位置关联与过期、冷却隔离、Hypixel 域名和侧边栏过滤，以及评分累加/封顶、一次性阈值、UUID/连接隔离、排行榜、颜色档位和 Raven 有效告警计分。通过当前映射 JAR 的字节码核对三个方块处理方法均先调用 `forceMainThread` 再应用更新。

尚未进行实际服务器联机验证；远端实体插值、跨版本代理和服务器自定义游戏规则对检测准确率的影响需要实测。自动举报默认关闭。
