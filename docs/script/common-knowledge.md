# Omix 客户端开发知识（1.21.11）

## 事件与线程

事件总线精确匹配事件类，保留发起线程，同类监听器按 priority 升序执行，取消不会自动终止后续观察者。Event 可取消不等于所有事件发起点都消费取消状态：查询 injection 中的消费点，不凭 setCancelled 名称猜测。

TickEvent 为客户端周期；LivingUpdateEvent 内模块计算意图，RotationManager 在较后阶段发出 RotationRequestEvent 收集并选出请求，再提供 Motion/发包阶段使用的角度。详细顺序和优先级以 rotation-manager.md 与事件声明/调用处为准；game-tools.md 包含既有游戏工具的参数与使用限制，两份资料均随离线包分发。

PacketEvent Received 在网络线程同步执行，取消须在当前回调完成前设置；不要在这里读取/修改世界实体、UI或等待主线程。复制所需字段为不可变快照，再用 events.observe/tasks.client。发包事件保留调用方线程。接收包对象替换不代表底层消费点一定应用替换；当前接收管线支持同步取消，发送端替换需查具体入口。

## 旋转和移动

使用 RotationRequest.builder(owner, float[]{yaw,pitch},priority)；owner 使用 feature.id 避免冲突。数值越大的旋转 priority 越优先（与事件监听 priority 的排序方向不同），相同优先级按 owner稳定排序。请求只对当前收集周期有效。MovementCorrection.Silent 等模式由现有管理器处理，不重复修正输入，也不要同时手动改玩家 yaw、MotionEvent 和移动包造成多个角度来源。

## 交互和协议

1.21.11 不是1.8：不使用 C03/C0F/C08 或旧 transaction ID。用当前 Yarn 类、连接和交互管理器。修改选中快捷栏后让交互管理器同步槽位；物品使用/方块放置须遵循当前预测 sequence、命中位置和碰撞语义。优先 interactItem/interactBlock 等入口，不凭旧教程拼包。

只在客户端线程触碰玩家、世界和 GUI；没有世界时 mc.player/world/interactionManager 可为空。AI读取文档、源码和编译不要求进世界；截图取当前屏幕。需要玩家的逻辑检查 inWorld()，世界切换取消旧计划。

## 渲染

2D 绘制使用当前 Render2DEvent 的 DrawContext 或 HUD draw 参数；3D 使用当前 Render3DEvent，不缓存到后台线程或下一帧。遵循已有 Render3D/字体/动画辅助类状态约定。拖动 HUD 由聊天界面处理；size 必须对应实际绘制区域。

## 所有权与卸载

Blink、Delay、临时 Timer 使用独立 owner；释放自己的 owner，不调用全局停止或 reset。异步完成时用受管 tasks.client，检查代次与世界。模式接管会退出内置生命周期并隔离内置事件；直接返回行为必须通过已声明 ModeHooks，不能假设下拉模式名称自动改写任意原生方法。

## 开发循环

读 API 和当前源码 hash → 小步写入 → check → 轮询 job → 修复诊断 → load/reload → 确认 loaded/generation → 查看 logs、状态、截图 → 验证 → 释放会话。编译通过不能证明服务端接受操作，保存成功不能证明功能生效。
