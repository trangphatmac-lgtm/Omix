# NoFall / GrimPlus

在 NoFall 的 `Mode` 中选择 `GrimPlus`。实现来源为用户提供的 `NoFall-完整逆向分析交付.zip` 中的 `NoFallReadable.java` 与对应事件/Mixin 证据（原类 `NewZKMJNIC.ii11lliiiiii`）。阈值固定为 3.0，不使用 Distance、Delay 或 Grim2 的 Newest Grim 设置。

## 状态与顺序

仅在 PRE 移动事件中，先读取当前玩家的原生 `fallDistance`，再检查**上一轮 PRE 的采样值 ≥ 3.0**、当前事件 `onGround=true`、NoSlow 活动阶段为 false。三个条件满足时按原版顺序执行：

1. 当前移动事件 `onGround=false`。
2. 写入共享的 `static volatile` 毫秒时间戳。
3. 通过 `PacketUtil.sendPacket` 提交 `OnGroundOnly(true, player.horizontalCollision)`。
4. 待取消更新次数加一，设置待跳跃标志，将待冻结输入次数覆盖为 1。
5. 最后保存开始时读取的 fallDistance；未触发时同样保存。POST 不采样、不触发。

因此落地时当前 fallDistance 已为零仍能触发；上一轮为零、当前刚超过阈值则先采样。没有额外碰撞阻止条件，没有清除原生 fallDistance、修改速度/Y 坐标或直接调用 jump。附加包读取玩家实际的 horizontalCollision，不读取可被其他监听器修改的事件碰撞位。

新增 `PlayerUpdateEvent` 精确放在 `ClientPlayerEntity.tick` 调用 `AbstractClientPlayerEntity.tick` 之前，与逆向 Mixin 的位置一致。正数取消计数每次消耗一项，取消余下玩家 tick，包括本次输入与移动包发送；下一次实际到达输入事件时才消费输入标志。现有位于 tick HEAD 的 `UpdateEvent` 保持原事件位置。新取消路径恢复 HEAD 阶段可能替换的 Freecam 输入，避免把临时输入留到下一轮。

输入的三个条件独立执行：正数冻结计数使 forward/strafe 为零并减一；保存的 fallDistance ≥ 3 且玩家实际落地时取消当前潜行；待跳跃标志使当前输入 jumping=true 并清除。跳跃不会额外检查落地、NoSlow 或距离，不改原始按键 pressed 状态。NoFall 的这三个监听器使用事件总线默认优先级，对应原类未指定优先级的监听器。

## NoSlow、查询与生命周期

`NoSlowDown.isGrimActivePhase()` 仅在原生行为启用、模式为 Grim Full、当前玩家会话有效时查询状态机的 `state != NONE || activeNoSlow`。准备、使用、等待释放/停止与恢复阶段都会阻止新的 GrimPlus 落地触发；单纯开启模块但处于 NONE 阶段不会阻止，Vanilla 模式也不会阻止。它不依赖服务端元数据是否已经解除减速，避免把“活动阶段”误当成“当前减速已取消”。Omix 没有原客户端 Stuck 模块及旧协议查询服务，采用本项目原生 1.21.11 协议的活动阶段映射。

`NoFall.isGrimPlusTriggeredRecently()` 保留共享查询 `currentTimeMillis - lastTriggerMillis < 400`，不是触发冷却，不检查模块开关、不额外排除零时间戳。连续触发会累加取消次数；冻结计数仍覆盖为 1。保留 Java int/long 溢出和墙钟倒退行为。Omix 没有原 Scaffold 混淆 guard，运行时使用原版正常的 null 路径；辅助类保留可注入时钟/guard 的查询以校验非 null 分支（elapsed != 400）。不移植 DES/JNIC 加载器等与业务无关的混淆设施。

关闭 NoFall 或切出 GrimPlus 时按原顺序清空五项状态，不额外发包。切出模式视为禁用该实现。遵循原类自身行为，启用、WorldEvent 和服务端位置修正不额外重置 GrimPlus；其他模式的原有清理逻辑保持独立。

## 代码与验证范围

- 模式设置、生命周期及 Minecraft 适配：`src/main/java/cn/omix/module/impl/move/NoFall.java`。
- 业务状态机：`src/main/java/cn/omix/util/player/nofall/GrimPlusState.java`。
- 玩家更新事件接入：`src/main/java/injection/MixinClientPlayerEntity.java`。
- 测试与附件原始数据：`src/test/java/cn/omix/util/player/nofall/`、`src/test/resources/nofall-grimplus/`。

生产状态机使用真实 Omix 事件对象，对照附件 700 条 native 输入/输出，核对全部五项持久状态和副作用顺序；另将其中 108 步连续时序在不恢复中间状态的情况下重放。392 组时间/guard 组合检查文档定义的 JVM 语义，还覆盖共享时间戳、发包失败后的部分状态、采样先于发包及 NoSlow 各活动阶段。测试不执行附件脚本或原生库。

验证命令：`gradlew.bat test --tests cn.omix.util.player.nofall.* --tests cn.omix.util.player.noslow.*`；打包使用 `gradlew.bat remapJar`。

这是原类业务逻辑与本项目宿主的接入，不是整个原客户端框架的替换。附加落地包经过 Omix 现有发包、过滤和缓冲流程；后续常规移动包仍由现有 MotionEvent Mixin 按是否发生变化决定发送，其他模块也可能修改或取消事件。自动化结果和附件 native 数据不能保证服务端接受状态，尚未进行实机或多人服务器免摔验证。联机应核对 ≥3 格落地、水平碰撞、潜行落地、连续落地、NoSlow 使用/恢复阶段以及切出模式后的输入恢复。
