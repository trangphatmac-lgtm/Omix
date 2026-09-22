# 脚本 API

完整可搜索声明在 api.json / sdk.md；下面说明入口和约束。自动变量为 mc、client、script、game、movement、inventory、modules、modes、commands、tools、events、tasks、packets、render、ui、storage、managers、fisproxy、nativeAccess。

| 入口 | 能力 |
| --- | --- |
| game | player/world/entities/block/crosshair/targets/friend/rotationTo/path/useItem/useBlock/attack，世界和线程校验 |
| movement | moving/speed/blocksPerSecond/strafe/stop/velocity，复用 MovementUtil |
| inventory | snapshot 返回栈副本、find、select、click，使用当前容器 syncId |
| modules.register(id,name,Category) | ModuleHandle，稳定 ID 为 script:文件ID/功能ID，独立开关、按键、设置、生命周期 |
| modules.enable/disable/toggle/setEnabled/isEnabled | 按模块名或稳定 ID 操作其它模块，执行原生启停生命周期 |
| modules.settings/getSetting/setSetting | 发现并读写全部 Value 类型；getButton/setButton、getSlider/setSlider 提供快捷访问，setKey/getKey 控制模块按键；见 packet-module-control.md |
| modes.register(id,moduleId,name) | ModeHandle 接管现有模块，typed hook，设置只在选中时显示 |
| commands.register(usage,action,aliases...) | 本地命令，参数不含命令名，completion、卸载；commands.run/complete 接入现有命令 |
| tools.register(id,description,parameters,callback) | 自定义 AI 工具，ToolHandle，JSON schema 校验、主线程执行、热重载；见 custom-tools.md |
| ui.hud(id,name,draw) | 拖动 HUD，模块开关，size、position，坐标持久化；聊天界面拖动 |
| feature.setting(Value) | BoolValue、NumberValue、ModeValue、TextValue、KeyValue、ColorValue、MultiBoolValue，复用原生条件显示 |
| feature.on(Event.class,priority,callback) | 功能启用时的同步监听；同事件按数值从小到大，默认 10 |
| events.on / observe | 代次监听；observe 的快照函数在源线程执行，observer 在客户端线程执行 |
| tasks.client / afterTicks / async | 主线程、按 tick 延迟、后台 Callable；卸载取消/拒绝迟到操作 |
| packets.send / sendWithoutEvents / sendSequenced / sendSequencedWithoutEvents | 主线程、连接校验；普通/无事件发包与世界交互序列号 |
| packets.onSend/onReceive/cancel/replace | 同步监听与取消；支持发包替换，默认优先级 0，可绑定 feature；见 packet-module-control.md |
| packets.blink(feature) / delay(feature) | 本次功能启用周期独立 owner，不重置其他模块/脚本的所有权 |
| timer(feature,speed) | TimerSpeedUtil 临时 owner，释放后由其他 owner/原模块决定速度 |
| rotate(event,request) | RotationRequestEvent 同步仲裁，不永久改全局旋转 |
| render.text/rect/box | DrawContext 2D 与 Render3DEvent 3D 封装；必须在对应渲染回调内使用 |
| ui.page(id,html,messages) | WebPageHandle.open 打开 CEF 页面，JS omixScript.send(JSON) 请求/响应，Java回调在主线程，卸载失效旧代次 |
| ui.screen / notify / open | 原生 Screen、通知、WebUI 路由 |
| storage.read/write | JSON 数据按脚本和安全 key 隔离 |
| managers | Rotation、Packet、Target、Friend、Config、Command、Font 原生管理器 |
| fisproxy | 当前 FisProxyManager，status/services/start/stop/operations 等异步接口 |
| nativeAccess | Yarn 类名和成员名反射映射辅助，避免生产环境硬编码 named 反射 |

原生工具类全部位于 cn.omix.util，使用显式 import；api.json 索引工具声明和具体包。玩家、世界、实体、背包、交互可直接通过 mc 和 Minecraft 公共 API；移动、射线、方块、物品、数学、计时、路径、字体、动画优先复用现有 util/manager。切勿把原生对象长期缓存过世界切换。

设置名称在一个模块内唯一，脚本模式设置也必须避免覆盖内置设置；建议使用“脚本名称 / 参数”。显示名称与命令别名会做冲突检查；稳定 ID不依赖显示名称。onLoad 构造后不要追加设置或替换处理器。
