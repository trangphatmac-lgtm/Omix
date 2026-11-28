# AntiCheat（Adjust HackerDetector 移植）

`AntiCheat` 位于 World 分类，可用 `.toggle AntiCheat` 或界面开启。模块仅在多人世界工作，观察其他玩家收到的实体移动、元数据、动画、装备、受伤、物品生成、方块变化和破坏进度包。所有相关包在客户端主线程应用后交给检测器，不取消或修改原始数据包。

原 OpenB4 检测器及 Raven 扩展已移除。当前检测器参考 Adjust 客户端的 `HackerDetector`、`TrackedPlayer` 和 `CheckDispatcher`，对应十一项检测和阈值如下：

| 检测项 | 达到作弊标记所需违规次数 | 依据 |
| --- | ---: | --- |
| Velocity | 4 | 受到其他实体伤害后缺少水平位移 |
| Auto-Block | 3 | 长时间使用物品的元数据组合 |
| Scaffold | 3 | 方块放置前的快速转向和换物品时间 |
| Scaffold 2 | 10 | 斜向搭路时的放置、蹲下节奏 |
| Sprint | 5 | 疾跑方向与移动方向不符 |
| Friction | 5 | 转向后移动方向异常持续 |
| Rotation | 1 | 收到超过正常范围的俯仰角 |
| Auto-Block 2 | 4 | 使用物品、格挡和疾跑状态组合 |
| Auto-Block 3 | 6 | 使用物品时挥手的时间组合 |
| Nuker | 4 | 被破坏方块周围没有可达空间 |
| Inventory | 3 | 极短间隔内连续生成掉落物 |

检测器实现位于 `src/main/java/cn/omix/util/world/adjustdetect/`，模块入口位于 `src/main/java/cn/omix/module/impl/world/AntiCheat.java`。切世界或关闭模块时清空跟踪和违规次数。默认忽略好友与队友，可通过 `Ignore Friendly` 关闭。`Show All Detections` 默认显示每次违规；关闭后仅显示已经达到作弊阈值的违规。聊天栏按玩家 UUID 仅保留该玩家最新的一条 AntiCheat 提示：新的检测出现时移除上一次提示，再将新提示显示在最下方；其他玩家和普通聊天记录不受影响。`Name Marker` 默认开启，玩家达到任一检测阈值后，在原版名牌、自定义 NameTags 和 Tab 列表的名字前添加红色 `!`，Tab 对应玩家行同时显示淡红色高亮。标记仅用于显示，关闭模块或该选项后立即消失。提示代表客户端启发式结果，可能误报。

Adjust 原实现依赖 Minecraft 1.8 的实体跟踪器、剑格挡、Hypixel 游戏状态、好友标签和聊天通知。当前版本使用 1.21.11 数据包、实体状态及盾牌格挡语义；Velocity 依据现代伤害包中的来源实体 ID 过滤环境伤害。由于协议和服务器数据不同，检测结果不能与原版逐包等同。`AutoL` 的 Only Cheaters 仍可按 UUID 查询分数：任一检测达到 Adjust 的作弊阈值返回 100，未达到时按最高单项阈值比例返回 0–99。Aura 的 Smart 优先选择 AntiCheat 当前已判定为作弊的玩家，不依赖 Name Marker 显示选项；未开启 AntiCheat 时使用原有排序。

编译验证：`./gradlew compileJava --offline`。实际多人服务器效果尚需联机验证。
