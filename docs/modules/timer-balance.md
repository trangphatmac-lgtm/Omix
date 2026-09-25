# Timer Balance

Timer 的 `Mode` 可选 `Classic / Balance`。默认保留 Classic，已有配置的 `Speed` 名称、默认 1、0.01–5 范围和实时修改行为保持兼容。切到 Balance 后使用下列参数：

| 设置 | 默认 | 范围 / 含义 |
| --- | --- | --- |
| Release Button | Middle | Middle / Side 1 / Side 2；GLFW 索引 2 / 3 / 4，按住消耗余额 |
| Boost Speed | 1.6 | 1.05–3，步长 0.05 |
| Max Balance | 9 | 1–12，步长 1；单位为逐次更新积累的浮点预算 |

## 原版状态机

根据用户提供的 `Timer-完整逆向分析交付.zip` 中 `TimerReadable.java`、`TimerMixinReadable.java` 和报告移植，按本地玩家 `UpdateEvent`（玩家 tick 的 HEAD）结算一次：

1. 玩家不存在时直接返回，不写倍率或修改状态。
2. HUD 透明度按 `opacity += (enabled - opacity) * 0.12f` 更新。
3. LongJump 开启时本模块输出 1 倍，余额保持不变，也不执行容量夹紧。
4. 按住触发键且 `balance > 0.1f` 时，若余额至少为 `Boost Speed - 1`，输出 Boost Speed 并扣除此成本；否则输出 1 倍且不充能。
5. 不满足加速尝试条件、Aura 的目标为 null 且未充满时，方向键任一按下则输出 `0.92f`，否则输出 `0.8f`；余额加上 `1.0f - 倍率`。检查目标本身，不额外检查 Aura 开关；不通过速度向量、跳跃键或疾跑键判断移动。
6. 其余情况输出 1 倍。Balance 分支最后才将余额夹紧到 `[0, Max Balance]`。

使用原始 float 运算和严格比较，不引入 epsilon、double 或按真实时间结算。默认静止从空余额充满需要 46 次更新，有方向键时需要 113 次。从满额按住可加速 15 次；从空余额一直按住会先充到约 0.2，再因不足一次加速成本停在 1 倍。松开才能继续充能。Aura 目标只阻止充能，不阻止消耗已有余额。

降低容量先执行本次分支，再夹紧，所以旧余额可能先支付一次加速。Classic / LongJump 暂停余额结算；关闭、重新开启、切模式、换世界不清空余额或 HUD 插值。余额仅存在当前模块实例的内存中，不写配置。关闭 Balance 恢复共享基础倍率 1 并释放模块覆盖。事件按 Omix 生命周期在关闭后注销，因此不额外制造原框架未保证的关闭淡出。

## 时钟与宿主兼容

Balance 倍率不为 1 时，在 `RenderTickCounter.Dynamic.beginRenderTick(long, boolean)` 的 HEAD 使用逆向公式：`dynamicDeltaTicks = (float)(now - lastTimeMillis) / tickTime * multiplier`，累加 tickProgress，截取整数 tick 并保留余数，取消原方法。与原版一致，该分支不使用 boolean 参数，也不经过动态 tick 时长运算；倍率为 1 时继续原方法。Classic 继续使用 Omix 原有时钟重定向。

保留 Omix 的临时倍率仲裁：LongJump 等持有临时覆盖时，临时倍率优先，并使用 Omix 原有时钟路径；Balance 不抢占 LongJump 的 0.02x。Timer 自身仍会在 LongJump 开启期间暂停预算。这是宿主兼容约定；逆向样本的共享全局倍率字段本身没有此仲裁。未增加发包缓存、TPS 读取、服务器纠正处理或额外 tick。

## 余额 HUD

Balance 自带 HUD，不依赖 HUD 模块开关或其显示模式。初始位置为构造时屏幕中心向左 70、向下 120，拖动区域 140×32。打开聊天后按住左键拖动；隐藏的 `Balance HUD X/Y` Value 保存相对屏幕位置，-1 表示尚未移动的初始位置。

保留原版背景颜色 `(28,27,31,220)`、轨道颜色 `(73,69,79,100)`、圆角 12、116×4 进度条、标题/百分比布局、字体请求大小 8、每帧进度插值 0.15、中心缩放 `0.85 + opacity*0.15`，以及 0.001 填充、0.9 文字强调、0.98 辉光阈值。数字显示实际百分比，条宽使用平滑值。主题色来自 Omix HUD。Alpha 保留源颜色透明度并向下截断。

字体和绘制后端适配为 Omix：使用 MiSans，圆角使用 GUI 几何，Skija 模糊阴影使用同色柔边几何近似。布局和绘制逻辑已移植，不声称与原客户端字体、模糊效果逐像素一致。字符串解密和 AutoTrap 的异常混淆 guard 不属于新增模式功能，未引入 Omix。

## 验证

`TimerBalanceTest` 使用交付包原始时间线回放 2,000 次更新/绘制，在 285 个采样点精确比较余额、倍率、透明度和进度插值；另测充满次数、持续按住、浮点阈值、扣费、目标阻止充能、缺少玩家、关闭保留状态、容量变化、鼠标映射和 HUD 调用顺序。`TimerSpeedUtilTest` 检查 Balance 路径与现有临时覆盖互不抢占。

交付时间线的原始验证来自逆向成果，本项目测试不等于再次执行原始 JAR，也不替代游戏内 GLFW、GPU、Mixin 加载和服务器验证。
