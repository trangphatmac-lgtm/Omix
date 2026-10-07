# Opai / Neverlose 与 Opai HUD

在 ClickGui 的 Mode 中选择 **Opai** 或 **Neverlose**，右 Shift 打开。原有模式与默认 Web 保留。HUD 的 Mode 独立选择 **Opai**，原有默认 Omix 保留。

界面绘制、字体、纹理、圆角、阴影、布局、弹簧动画来自本地 Samsara 项目，适配 Minecraft 1.21.11。模块、设置、绑定、隐藏状态与配置均读取 Omix 的原生对象，脚本模块的注册/移除也会同步。原生 hidden 仅影响模块列表 HUD，在 ClickGui 中仍可编辑这些模块。

## ClickGui

- Opai：左键切换模块，右键展开设置，中键绑定；拖动分类标题移动面板，右键标题折叠；滚轮纵向滚动，Shift + 滚轮横向移动。新增 Exploits 列以覆盖 Omix 的全部分类。
- Neverlose：侧栏选择分类，搜索支持模块和设置名，双列显示控件；模块标题右侧收起/展开，按键标签设置绑定。Misc 包含 World 和 Exploits；Targets 连接原生全局目标筛选。配置页支持创建、保存、加载和二次点击删除。
- Opai ClickGui 与 HUD 共用 **HUD → Opai Color**（ColorValue），实时同步分类面板、设置控件、配置面板及 HUD 编辑入口。ClickGui 原有 Lavender / Light Pink 的 Opai Color 配置项已移除；旧配置中的该字段会被忽略，以 HUD 的颜色为准。
- 布尔、数值、模式、多选设置使用原版控件；Omix 额外的颜色设置使用同风格的 Hue / Saturation / Brightness 滑块；文本和独立按键设置从下拉选项打开编辑框。敏感文本隐藏显示，Enter 保存，Esc 取消。
- 两套面板均管理当前游戏目录的 `Omix/configs`，沿用原生 JSON 与加密存储格式。Default 受保护，不能删除。Blank 创建关闭模块的配置副本，不改变当前正在运行的模块。
- 界面关闭保存 Default；位置/缩放随原生配置保存到 `_opaiClickGui` 和 `_opaiHud`。

## Opai HUD

Widgets 为原生多选配置，六项默认全选：

| Widget | 内容 |
| --- | --- |
| ArrayList | 已启用模块列表，保留进入/退出动画与平滑重排 |
| InventoryHUD | 27 格主背包，原始玻璃面板与物品数量 |
| TargetHUD | Aura 玩家目标、头像、血量与伤害拖尾，可显示护甲 |
| SessionHUD | 本次客户端游戏时间、击杀及胜利统计 |
| Potion Status | 英文状态效果名、等级、持续时间与原始图标；到期前 30 秒红色计时 |
| Dynamic Island | 居中状态栏、用户名、服务器、Ping、FPS；模块切换通知、Scaffold 状态、ChestStealer 收箱面板及转移动画 |

ArrayList mode 为 Default / Opai，初始 Opai。两个样式拥有独立设置：

| 配置名 | 默认值 | 说明 |
| --- | --- | --- |
| Opai Color | #BBC3FF | 原生 ColorValue，仅 Mode = Opai 时显示；统一调整 Opai HUD 与 Opai ClickGui 的主题强调色 |
| Bar mode | Left | Default 的侧边条：Left / Right / None |
| Lowercase / Opai Lowercase | true / false | 名称小写 |
| Show suffix / Opai Show suffix | true | 模块模式后缀 |
| Background / Opai Background | true | 背景 |
| Opai Shadow | true | 阴影 |
| Opai Right line | true | 右侧强调线 |
| Categories / Opai Categories | 全选 | Combat、Movement、Player、Visual、World、Exploits |
| Target Mode | Opai | Classic / Opai，目标 HUD 样式 |
| Target Show Armor | true | Opai 目标护甲图标 |
| Target Theme Color | false | Classic 血条使用主题颜色 |
| Target Outline | false | Classic 外框 |
| Target Show Win or Loss | false | Classic 显示与目标的血量比较 |
| Target Health Animation | false | Classic 血量拖尾 |

通过 ClickGui 的 HUD 编辑入口编辑位置：拖动可移动组件；按住左键至少 150 ms 后滚动改变缩放（0.5–2 倍），右键重置大小，Esc 保存退出。DynamicIsland 与 ArrayList 保留固定锚点，只能缩放。编辑器提供背包、玩家目标和药水示例，便于没有战斗目标时调整。

Potion Status 保留原版支持的 17 类图标；没有原版资源的效果不生成组件。Session 的击杀按本地攻击后收到的死亡状态包或观察到的目标死亡计数，胜利按服务器标题匹配；不是服务端权威战绩。

DynamicIsland 的 Ping 使用当前连接的原生 ping/pong，停止使用组件后停止采样；识别 Hypixel 侧栏页脚并保留当前连接的服务器显示名。ChestStealer 实际收箱时会展开为原版 27/54 格面板，物品、数量、九宫格阴影和转移反馈使用原始资源；手持物品或背包无法接收物品时恢复原生容器。关闭/操作行为仍使用 Omix 的 ChestStealer，不增加 Samsara 的收箱策略设置。

Inventory 先绘制背景，再绘制全部物品模型，最后在独立的顶层绘制数量、耐久条和冷却遮罩，避免模型遮挡数量文字；拖动与缩放沿用同一分层顺序。

Samsara 的 BedAura 在当前客户端没有对应模块，保留 DynamicIsland 的挖掘进度状态与绘制接口 `DynamicIslandManager.postBreaking`，不生成虚假的挖掘数据。

HUD 的 Opai Color 会实时更新 ArrayList 的后缀/侧线、TargetHUD 的血条/血量、DynamicIsland 的品牌/图标/进度/开关，Opai ClickGui 分类面板/设置控件/配置面板/编辑入口，以及编辑器选框；Default 列表样式和 Classic 目标的主题色选项也读取同一颜色。中性背景、白色正文、药水效果色、信息/成功/警告通知色及启停红绿状态色保留各自的含义。该值随原生普通/加密配置保存，加载后立即生效。

Opai / Neverlose 的 NanoVG 字体均配置项目内置 MiSans 中文回退字体；TargetHUD 等原生字体绘制也使用同一回退资产。中文模块名、设置、搜索内容、配置名和通知不会替换原有英文字体，测量与绘制共享回退字形。无需依赖系统安装中文字体。

Opai 分类面板的所属 Category 与绘制层级分离。拖动、点击置顶、展开设置和加载位置不会改变模块归属；运行时新增/移除模块按面板固定分类同步。

## 实现与验证

界面 Screen 位于 `cn.omix.ui.opai` / `cn.omix.ui.neverlose`；所有适配器、布局、绘制及 HUD 辅助逻辑位于 `cn.omix.util.opai`。使用与 1.21.11 相同版本的 LWJGL NanoVG，通过 GUI 提交后的 OpenGL 阶段绘制；在原生物品绘制前采集玻璃背景。

验证命令：`./gradlew test remapJar`（458 项测试通过，其中 Opai 相关测试 64 项）。在独立的 1.21.11 客户端与临时世界中验证两套界面、六组件编辑器、左键拖动/长按滚轮缩放/右键复位、54 格收箱动画、100 个原生模块设置适配、加密配置往返及布局恢复；未使用用户现有存档。补充验证中文模块/设置/搜索/灵动岛通知、30 次实际分类拖动后的 180 次归属检查，以及 HUD 主题色在 NONE / NORMAL / HEAVY 三种配置格式中的保存与恢复。Inventory 在 0.5 / 1 / 2 倍缩放下检查了 27 个物品与数量/耐久覆盖层的实际渲染层级；验证了已打开的 Opai ClickGui 实时同步 HUD 颜色（HUD 关闭时也生效），旧 ClickGui 配色字段不会覆盖 HUD 颜色。70 个移植字体/贴图/附带资源与 Samsara 原文件逐一哈希相同。实机验证平台为 macOS Apple Silicon / OpenGL 4.1；Jar 同时包含六种平台的 NanoVG 原生库，其他平台未实机运行。视觉还原应在相同窗口像素尺寸、GUI 缩放与对应组件配置下比较；模块数量、分类和实时游戏数据采用 Omix 的实际状态。

原始资源和来源说明位于 `assets/omix/opai/NOTICE.txt`，Samsara 的许可证随包保留在 `META-INF/licenses/samsara-gpl-3.0.txt`。仅移植这两种界面及 HUD 用到的资源，没有引入 Samsara 的启动界面或其余玩法模块。
