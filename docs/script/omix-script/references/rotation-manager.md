# RotationManager 开发说明

RotationManager 不再读取具体模块或维护模块优先级列表。模块在 `RotationRequestEvent` 中提交不可变 `RotationRequest`，管理器统一选择并应用旋转。优先级和策略由请求发送者决定。

## 模块接入

```java
@EventTarget
public void onRotationRequest(RotationRequestEvent event) {
    if (!isEnabled() || mc.player == null || rotations == null) return;
    event.submit(RotationRequest.builder(getName(), rotations, 400)
            .speed(180)
            .silent(true)
            .movementCorrection(MovementCorrection.Silent)
            .build());
}
```

需要导入 `cn.omix.event.impl.RotationRequestEvent`、`cn.omix.management.rotation.RotationRequest` 和 `cn.omix.management.movement.MovementCorrection`。

模块先在 `LivingUpdateEvent` 计算角度。管理器的监听优先级为 999，在该回调内同步派发 `RotationRequestEvent`，收集完成后立即仲裁并应用。角度计算监听应在此之前执行（事件优先级小于 999）；旋转请求自身的 `priority` 与事件监听的 `@EventPriority` 无关。

每次收集创建新事件，请求仅对这一 tick 生效。下一 tick 不再提交即停止应用旋转；旋转数组缓存仍按旧流程保留到 pre-motion 再恢复镜头角度，避免提前影响放置模块；模块关闭后监听注销，不会在下一轮继续提交。切换世界或玩家/世界为空时清空所有已应用状态。只允许在客户端线程的收集回调中提交，事件完成仲裁后再提交会抛出异常。同一个 owner 在一轮内重复提交会替换此前请求。

## 请求字段

| 字段 | 含义 |
| --- | --- |
| `owner` | 稳定且唯一的来源标识，模块使用 `getName()`。 |
| `yaw / pitch` | 目标角度，单位度；构建时复制数组内的值。必须为有限数值，不强制限制 pitch，以兼容 Derp 的非安全 pitch。 |
| `priority` | 整数，数值越大越优先；相同时按 owner 的字符串自然顺序选择，避免依赖监听注册顺序。 |
| `speed` | 默认 180；平滑时使用原有随机微调和鼠标灵敏度处理。未指定 instant 时，0 表示直接应用，正数表示平滑。负数及非有限数值无效。 |
| `instant` | 可显式覆盖 speed 推导的模式。true 直接应用；false 始终使用原有 `speed + Math.random()` 平滑，包括速度为 0。Aura、Scaffold、ScaffoldX 显式使用 false，保留配置语义。 |
| `silent` | 默认 true，仅改变发送/渲染的旋转，不主动转动镜头；false 将请求控制的轴同步到镜头。 |
| `movementCorrection` | 默认 None；可选 Silent、Strict、Prediction。它与 silent 独立：前者控制移动输入，后者控制镜头。 |
| `axes` | 默认 BOTH；YAW_ONLY 保留镜头 pitch；PITCH_ONLY 从其他请求中选择优先级最高的 yaw 来源，没有来源则使用镜头 yaw。 |
| `continuousYaw` | 默认 false；将 yaw 转为距离上一次发送角度最近的等价整圈表示，并在释放时保持连续。 |

仲裁先选出一个总优先级最高的请求，使用该请求的速度、silent 和移动修正。仅当赢家是 PITCH_ONLY 时，才借用另一请求的目标 yaw 及连续性标志；低优先级的 pitch 请求不会覆盖高优先级的 BOTH/YAW_ONLY 请求。YAW_ONLY 始终保留镜头 pitch，不继承其他请求的 pitch；平滑阶段仍使用模块最初采样的 pitch 计算角度分配，平滑后再恢复镜头 pitch，与旧流程一致。非 silent 的 PITCH_ONLY 只同步镜头 pitch。

`RotationManager.getActiveRequest()` 可读取获胜请求（无请求时为 null），`isOwner(getName())` 可用于确保交互/数据包修正仍属于本模块。`isRotating()`、`getAppliedYaw(fallback)` 和现有旋转数组保留供消费方使用；消费方不应直接修改这些数组。原 `setRotations(...)` 已移除，新模块通过事件提交即可，无需修改管理器。

`RotationManager.release(request)` 只在 `request` 与当前获胜请求为同一对象时撤销控制，保留角度缓存至正常 pre-motion。ProjectileAura 用它实现取消/投掷后的及时释放，避免清除其他模块已经抢占的请求；未获胜或过期的请求不会影响管理器。

## 当前模块策略

| 模块/场景 | 默认优先级 | 特殊策略 |
| --- | ---: | --- |
| LongJump 起跳 / 收集 motion | 1200 | 起跳离地上升即开始 Silent 转至镜头 yaw + 180° 和 Target Pitch，并启用 Silent 移动修正；达到高度且旋转到位后发射。交互 tick 固定精确角度，后续 tick 收齐 motion 后释放请求。0 速度立即应用。 |
| NoFall Grim | 1100 | 立即覆盖 pitch=90，继承 yaw；Derp 服务端旋转活跃时不提交（Client Only 不阻止）。控制窗口内移动包 pitch=90 修正仍独立执行，保留旧行为，不检查仲裁归属。 |
| ChestArua Manual 待交互 | 1000 | 立即应用，保持 yaw 连续。 |
| Derp | 900 | 立即应用，silent，无移动修正；Client Only 开启时不提交请求，仅通过 RenderRotationEvent 改变本地模型渲染。 |
| AntiLava | 800 | 速度 180，移动修正跟随选项。 |
| AutoBlockIn | 700 | 直接应用已平滑的角度，Silent 移动修正。 |
| ScaffoldX | 600 | 速度、移动修正由模块提供。 |
| Scaffold 持续旋转 | 500 | 速度、移动修正由模块提供。 |
| ProjectileAura | 450 | 速度 180，静默、无移动修正；保留同一请求对象至下一次玩家更新，最多续交两个更新周期。执行前要求请求身份仍相同；交互期间临时应用已仲裁角度并在 finally 恢复镜头。 |
| Aura | 400 | 速度、移动修正由模块提供。 |
| ChestArua 自动 | 300 | 速度 180，保持 yaw 连续。 |
| TargetStrafe Legit | 200 | YAW_ONLY、Strict 移动修正，silent 跟随 Silent Aim。 |
| Speed Prediction | 100 | 立即应用，Prediction 移动修正。 |

这些数值位于各模块的提交代码中，不是新的用户配置项。默认顺序延续原管理器策略。Scaffold 的 On tick 放置事务、NoFall 的其他模式及临时交互发包保持各自现有流程；本接口接管此前由 RotationManager 管理的持续旋转。

## 行为兼容边界

LongJump 的 motion 收集与顶点释放由 `src/main/java/cn/omix/util/LongJumpMotionQueue.java` 管理，模块通过公共接口调用；旋转请求仍由 LongJump 模块提交。

LongJump 首次使用在 `RotationAppliedEvent` 的正常交互阶段使用上一玩家 tick 已发送的旋转，由原版交互管理器同步槽位并右键：Fireball 沿 Silent 角度射线检测交互距离内的方块，再调用 `interactBlock`；Windcharge 调用 `interactItem`。未命中方块时在开启 Timer 前退出并提示。交互走正常发包事件链，临时设置 yaw/pitch，并在 finally 中恢复镜头。

Multi 收到上一发 motion 后，客户端线程直接尝试连续使用；若原生 tick 尚未发出 `CLIENT_TICK_END`，由渲染帧在其结束后继续。只观察原有 tick-end，不创建移动包或额外 tick-end，也不推进玩家、世界或冷却管理器 tick。整个收集过程保持 0.02x，直到收齐 motion 或退出才清除倍率。后续使用按物品冷却组件及服务器冷却包确定真实时间期限，过期后通过原版交互管理器发送交互，即使本地冷却显示仍受慢速 tick 影响。每个续用必须有新的 motion 响应授权，渲染帧仅重试尚未到期的预约，不会连续重复发包。

`LongJumpUseSchedule` 保存上一发的 `LongJumpAim`；收集期间旋转请求、最终 pre-motion 和带转头的发送包均复用这组精确浮点值。Scaffold/ScaffoldX 在收集期间及交互所在 tick 暂停切槽与放置。

保留默认优先顺序、零速平滑、TargetStrafe 的单轴平滑顺序，以及无请求时到 pre-motion 才恢复缓存的时机。NoFall Grim 的发包层 pitch 修正与持续旋转仲裁仍是独立流程。

有意保留的生命周期变化：切换世界或玩家/世界为空时清空旋转状态，避免沿用上一世界的目标角度；Scaffold 的 Nearest/Hypixel 在缓存为空时以当前玩家视角兜底。这一点与旧管理器保留缓存的行为不同。

## 验证

`RotationRequestTest` 覆盖优先级与稳定平局、同来源替换、单轴合成、连续性继承、参数验证、角度快照、显式零速平滑与立即应用的区分，以及真实 EventManager 注销监听后下一 tick 不再收到请求。

兼容性复核还将改造前的管理器与当前管理器、当前模块的请求回调放入隔离桩环境，固定随机增量，对照了 65,536 组模块启停与参数组合，包括 Look/Strafe/Jump/MoveInput/Render/Motion 消费、无请求及释放阶段，结果一致；NoFall Grim 发包回调与改造前文本一致。该对照检查平滑调用参数与调用顺序，未替代真实游戏的鼠标灵敏度、服务器或切世界联调。
