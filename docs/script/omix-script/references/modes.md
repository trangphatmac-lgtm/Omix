# 扩展现有模块的模式

`modes.register("id", "Speed", "Script Hop")` 返回独立 ModeHandle。现有主 Mode 沿用原入口；AutoTool/ChestESP 使用 Implementation，Chams 使用 Render Mode；没有主模式的模块临时增加 Behavior，内置项 Built-in。生成的 mode-hosts.json 列出实际内置宿主；脚本注册的新模块应直接在自己的实现中管理模式，不作为其他脚本的宿主，以免卸载依赖后保留失效实例。

切入脚本模式：调用内置 onDisable 并注销其事件，然后激活脚本模式。切回：停用脚本及其受管资源，再恢复内置 onEnable。模块的 enabled 表示用户开关，isNativeBehaviorActive 表示内置行为当前可运行。直接 Mixin 消费入口以此隔离内置逻辑。

卸载当前模式时恢复此前内置选项并关闭可关闭模块。PathFinder、Targets 常驻提供者恢复 Built-in。模式设置在对应模式下可见，名称不得与宿主或其他模式设置冲突。重复加载相同脚本稳定 ID时恢复选项和设置。

## 直接返回值

ModeHooks 提供类型化输入和输出：IS_TEAM、COMPUTE_PATH、ENTITY_REACH、XRAY_BLOCK、RENDER_HUD、INTERCEPT、SLOWDOWN。query 未提供或回调失败返回安全默认值，不运行被接管的内置算法。

- Targets：SELECT_TARGET 接收实体和本次查询的过滤开关。
- AntiBot：IS_BOT 输入 LivingEntity。
- GhostHand：BLOCK_REACH、BLOCK_TARGET，以及 INTERCEPT("throughWalls")。
- Chams：RENDER_ENTITY、ENTITY_LAYER、ENTITY_COLOR，覆盖选择、渲染层和颜色。
- NoFog：INTERCEPT("fog")，默认为保留原版雾。
- Teams：IS_TEAM 输入 LivingEntity，返回是否队友。
- PathFinder：COMPUTE_PATH 输入 PathQuery，返回 PathResult（路径不可变快照）。
- Reach：ENTITY_REACH 输入原版距离，返回所需距离。
- Xray：XRAY_BLOCK 输入 Block，返回渲染选择；TERRAIN_OPACITY 控制透明度，INTERCEPT("fullMode") 控制 full 模式决策。
- HUD 拖动项：RENDER_HUD 接收 DrawContext。
- KeepSprint：INTERCEPT 的 shouldKeepSprint/shouldOverrideHitSlowdown/shouldCancelJump 与 SLOWDOWN。
- ChestArua：INTERCEPT 的 handleManualUse/isRotationActive/isManualRotationActive/isSprintSuppressed/shouldBlockOtherInteraction。

原生方法没有 hook 时，以禁用内置消费者/恢复原版为默认；扩展新的同步查询应先在 ModeHooks 声明并接入消费点，再更新此文档与测试。脚本可以直接访问原生 API，但绕过这些消费点的自定义原生调用由脚本作者负责。
