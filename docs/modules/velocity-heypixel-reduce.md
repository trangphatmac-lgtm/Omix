# Velocity / Heypixel Reduce

在 Velocity 的 Mode 中选择 `Heypixel Reduce`。此模式移植提供的 OpenUitems `HeypixelTestVelocity.java`（Heypixel Test），原有 Normal、Packet、Reduce、Grim Full 保持各自流程。

## 设置

| 设置 | 默认值 | 范围 / 行为 |
| --- | --- | --- |
| AutoAttackCount | true | 自动选择攻击次数 |
| AttackCount | 4 | 0–20，步长 1，仅关闭自动次数时显示 |
| AttackMode | PerTick | PerTick 每 tick 一次；OneTime 同 tick 耗尽队列 |
| AlinkTargetRange | 10 | 0–20 格，步长 0.1 |
| AlinkMaxDelay | 60 | 0–200 个 MoveInput tick，步长 1 |
| RequireKillAura | false | 要求 Omix Aura 开启且已选择目标 |
| Debug | false | 在聊天栏显示缓冲开始、结束原因和攻击次数 |

上述设置仅在 Heypixel Reduce 模式显示。

## 原版流程

1. 仅自身 `EntityDamageS2CPacket` 设置受伤标记。随后自身速度包消费标记；其他实体的伤害/速度包不消费。使用物品、RequireKillAura 条件未满足、没有追踪目标或攻击次数为 0 时不启动。LongJump / Fly 开启时跳过本模式收包处理，对应原版的 `isInvalid()`。
2. 优先选择准星下未移除、非自身、通过 AntiBot 的 LivingEntity；否则搜索范围内最近的 LivingEntity。附近目标按实体位置距离 ≤ 3 格才赋为攻击目标，准星目标不额外做这个检查。RequireKillAura 只作门槛，不强制使用 Aura 的目标。
3. 强度按 `sqrt((vx*8000)^2 + (vy*8000)^2)` 计算，保留原版 X/Y 分量而非 X/Z。自动次数分别为：`<1000 → 0`、`[1000,2000) → 3`、`[2000,10000) → 4`、`≥10000 → 5`。关闭自动次数时读取 AttackCount。
4. 有攻击目标且正在疾跑时，速度包正常应用并设置攻击队列。否则进入 Alink，缓存本次速度包，重设计时器与释放原因。缓存期间继续缓存 CommonPing、EntityS2C、EntityPosition、EntityPositionSync；聊天消息正常通过，其他包仍按原分支处理，不是全量网络暂停。
5. 重生/加入游戏包标记 `disconnect`，位置修正包标记 `flag`，这些包本身放行。MoveInput 先递减倒计时并重新找目标，然后依次检查超时、飞行/旁观、追踪位置缺失或超范围；失败标记释放原因。找到攻击目标时仅将该次输入改为 forward=1、strafe=0，标记成功，不强制开启疾跑或跳跃。
6. 下个 Tick 首部先按 FIFO 回放缓存。成功释放时重新读取当前攻击次数，异常原因不新建攻击队列；随后按 OneTime / PerTick 执行。目标为空或已移除时清空攻击次数；原版不在攻击阶段重新检查距离、物品使用或疾跑。
7. 每次攻击前转向目标眼睛，调用原版 attackEntity、主手挥手，再额外将 X/Z 速度乘 0.6，保持 Y。这里的 0.6 叠加在原版攻击和其他模块的处理之后。耗尽次数清空目标及速度强度；attacking 在每个有效 Tick 首部重置，只在执行攻击的 tick 为 true。

原版目标选择的边界行为也保留：已有追踪实体从附近候选循环排除；只有找到其他候选时才比较追踪位置；找到超过 3 格的候选时不会主动清空先前的攻击目标。相对移动包直接累加原始 delta，没有额外除以 4096；同步位置直接替换，不根据相对标志重新解码。这些行为可能不符合常规目标追踪预期，移植未擅自修正。

## Omix 适配

- 辅助实现位于 `src/main/java/cn/omix/util/player/velocity/HeypixelReduce.java`；设置、事件和生命周期由 Velocity 转发。
- PreGameTick 对应 Omix 的 MinecraftClient.tick HEAD / TickEvent；输入的 sideways 对应 strafe。受包状态与 tick 状态通过同步方法串行访问。
- 原版外部 AntiBot / KillAura / Flight 分别接入 Omix AntiBot / Aura / Fly；过滤结果由 Omix AntiBot 的设置决定，没有移植另一个项目的整个 AntiBot 模块。
- 原版鼠标转头器将实际玩家转向目标、另存镜头方向。Omix 使用瞬时静默 RotationRequest（优先级 500，高于 Aura 的 400），Strict 移动方向；攻击调用期间临时应用角度，并在 finally 恢复镜头。后续移动包和渲染由现有 RotationManager 处理，其仲裁和帧间表现遵循 Omix。
- EntityPosition 使用本版本的 `entityId()` / `change().position()`，不依赖原版对旧映射名称的反射。
- 关闭模块、切出模式时回放剩余队列并重置；回放在客户端线程执行，单个包异常不中断剩余包。世界更换时丢弃旧队列并重置，不将旧世界击退应用到新世界。异步回放也校验连接、世界及玩家对象。
- `isAttacking()`、`getHitSelectSkips()`、`consumeHitSelectSkip()` 保留状态接口。hitSelectSkips 在有效触发时设为次数，仅消费接口会递减，不随攻击自动减少。Omix 没有同名 HitSelect 设置；原版 KillAura 的这部分分支也未引用 HeypixelTestVelocity，因此未新增 Aura 攻击跳过逻辑。Omix 现有 Aura 会读取 isAttacking 调整 CPS。
- 后缀显示 `Heypixel Reduce`；缓冲期间附加已等待的 `nTicks`。

## 验证

自动测试覆盖自动次数阈值、X/Y 与 Z 分量差异、手动次数覆盖、默认值、设置可见性及无世界时的空队列生命周期。

游戏内还需验证以下场景（自动测试不代表服务器实测）：

- 近距离疾跑受击，分别观察 PerTick 与 OneTime 的攻击次数和水平速度衰减；未先收到伤害包时不触发。
- 无近身目标或未疾跑时进入 Alink，聊天正常显示，目标同步和 Ping 缓存，成功后按原顺序回放；确认只在成功释放时写前进输入。
- 等待期内分别触发最大延迟（含 0）、超范围、旁观/飞行和服务器位置修正，检查释放原因；未触发成功时不新建攻击队列。
- 缓冲中关闭模块、切换模式、重生或切世界，检查没有旧包残留；查看 RequireKillAura、AntiBot、Aura 和其他旋转模块同时启用时的行为。
