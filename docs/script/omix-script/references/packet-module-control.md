# 数据包与其它模块控制

这些接口采用 Raven 风格的按名称访问方式，但使用当前 Minecraft 1.21.11 的 Packet 类与 Omix 模块生命周期，不兼容旧版 Raven 包包装类型。可执行模板：[PacketControl.java](examples/PacketControl.java)、[ModuleControl.java](examples/ModuleControl.java)。

## 发包与拦截

| API | 行为 |
| --- | --- |
| `packets.connected()` | 客户端线程查询当前 play 连接是否打开 |
| `packets.send(packet)` | 发送原生 C2S Packet，经过正常 PacketEvent，可被取消、替换或缓冲 |
| `packets.sendWithoutEvents(packet)` | 绕过所有 PacketEvent 监听器，包括其它模块和 Blink；仍进入包日志的 bypass 路径 |
| `packets.sendSequenced(sequence -> packet)` | 通过当前世界 PendingUpdateManager 分配正确交互序列号，经过正常事件 |
| `packets.sendSequencedWithoutEvents(sequence -> packet)` | 相同序列号处理，但绕过事件 |
| `packets.onSend(callback)` / `onReceive(callback)` | 在 onLoad 注册，返回可 close 的 Registration，卸载自动注销 |
| `packets.onSend(feature, callback)` / `onReceive(feature, callback)` | 仅 feature 启用期间调用；重载、停用后旧代次不再响应 |
| `packets.cancel(event)` | 同步取消当前事件；取消必须发生在回调返回前 |
| `packets.replace(event, packet)` | 替换当前发出的包；保留原连接的 listener/flush，不递归重新触发 PacketEvent |
| `packets.blink(feature)` / `delay(feature)` | 取得独立 owner，分别缓冲发出／收到的包；关闭句柄或停用 feature 时释放该 owner |

onSend/onReceive 均有 `(int priority, callback)` 与 `(feature, int priority, callback)` 重载。数值越小越先执行，网络快捷接口默认 **0**，早于 PacketManager 缓冲的 10（通用 events.on 默认仍为 10）。事件取消不会中止后续监听器，但 Blink/Delay 不再缓存已取消的包。若显式使用较晚优先级，已经被其它监听器缓存的包无法通过这次取消从它们的队列撤回。

```java
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

void onLoad() {
    var feature = modules.register("swing_filter", "Swing Filter", Category.Player);
    packets.onSend(feature, event -> {
        if (event.getPacket() instanceof HandSwingC2SPacket) {
            packets.cancel(event);
            // 或 packets.replace(event, new HandSwingC2SPacket(Hand.OFF_HAND));
        }
    });
}
```

主动发包必须在已加载代次的客户端线程调用，连接断开时报错，不再静默丢弃；序列号发包还需要进入世界。不要在 onLoad 发包。可在模块回调、命令、自定义 Tool 或受管任务中调用。`send` 表示提交给客户端连接，不能证明服务器接受或完成了动作。构造包时应选择当前协议阶段匹配的 C2S 类。

监听器保留真实触发线程；发送事件也可能来自网络线程。收包事件沿用现有 NetworkThreadUtils 管线，在需要切到主线程的包上触发，并非所有底层流量的总入口。跨线程修改模块或世界请通过 `tasks.client(...)`；把取消放进异步任务会太晚。需要跨线程观察时，在同步回调内提取不可变数据，再排队处理，不要延迟访问原 PacketEvent。

`replace` 目前只支持 **发包**，传入收包事件会明确报错，避免设置字段却不生效。收包可观察与取消。普通脚本监听器失败后停止该监听器；绑定 feature 的监听器失败遵循功能异常停用规则，均记录脚本日志。

Blink/Delay 复用现有共享核心与协议白名单，不是每脚本一份私人队列。关闭一个句柄不会强行释放其它 owner；最后一个 owner 释放时按核心规则处理队列。不要直接清空全局 manager 来结束单个脚本。跨 tick 发送可用 `tasks.afterTicks`，跨世界失效与卸载取消遵循任务规则。

## 模块开关与设置

按稳定 ID 或显示名查找模块，忽略大小写；每次调用重新查找，避免缓存热重载前的旧实例。未知模块／设置、类型错误、无效选项会抛出明确异常。

```java
modules.enable("Speed");
modules.disable("Speed");
modules.toggle("Speed");
modules.setEnabled("Speed", true);
boolean enabled = modules.isEnabled("Speed");
modules.setKey("Speed", 82);
int key = modules.getKey("Speed");

log(modules.settings("Aura")); // 名称、类型、可见性、数字范围/步长、模式/多选候选项
modules.setSlider("Aura", "Range", 3.0);
double range = modules.getSlider("Aura", "Range");
// BoolValue 的 Raven 风格快捷方法：getButton / setButton
```

开关经原生 `Module.setEnabled`，保留启停回调和事件注册；ModeValue 经原生 setter，保留脚本模式切换。成功修改会通知 Web ClickGUI 刷新，原生 UI 读取相同实例。

通用 `modules.getSetting(module, setting)` 返回独立 Gson JsonElement 快照；`modules.setSetting(module, setting, value)` 接受以下值：

| 设置类型 | 可写值 | 说明 |
| --- | --- | --- |
| BoolValue | boolean | 或严格 JSON boolean；不把字符串 "true" 自动转换 |
| NumberValue | Number | 必须有限，按原生 float 精度检查 min/max；步长是 UI 元数据，不强制重新量化 |
| ModeValue | String | 必须是现有选项，忽略大小写并使用规范名称 |
| TextValue | String | 直接读取会获得实际文本；settings 元数据不包含文本值 |
| KeyValue | 32 位整数 | 使用客户端现有按键编码；不会把小数截断成整数 |
| ColorValue | `java.awt.Color` 或有符号 ARGB int | 复用现有 RGB/HSB 设置，原生 ColorValue 不保存透明度 |
| MultiBoolValue | `Map<String,Boolean>` 或 JsonObject | 部分更新选项，先验证整个 patch 再修改；未知或重复选项报错 |

```java
modules.setSetting("模块名", "模式设置名", "候选模式");
modules.setSetting("模块名", "多选设置名", Map.of("Players", true));
modules.setSetting("模块名", "颜色设置名", new Color(80, 160, 255));
String mode = modules.getSetting("模块名", "模式设置名").getAsString();
```

这些便捷读写都要求运行代次有效且处于客户端线程。需要初始化时修改其它模块，请使用 `script.afterCommit(...)`；网络回调使用 `tasks.client(...)`。设置名称以 `modules.settings` 或实时模块文档为准，不复制示例占位名。

修改其它模块属于实际用户状态更改，**卸载脚本不会自动恢复**，也不会立即自动写配置文件；持久化遵循已有配置保存流程。需要临时控制时自行记录，在代次仍有效时显式恢复，避免覆盖用户之后手动进行的修改；卸载/onUnload 阶段已经失效，不能再通过这些便捷 API 改动其它模块。其它模块状态也不属于加载失败时自动回滚的受管注册资源。原生 `modules.get/list` 和 manager 访问仍保留。
