import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;

private final List<String> allAds = List.of(
        "[SilenceFix48.0无需脱盒] 免费且最强的客户端 我们也有布吉岛客户端呢 点击右边的代码heshuyou.xyz 来获取！",
        "SilenceFix Best Config Free 1055680656 ><",
        "[SilenceFix48.0无需脱盒] 我们免费提供高强度客户端混淆 zkm+jnic且正版 我们也有布吉岛客户端呢 点击右边的代码heshuyou.xyz 来获取！",
        "[欣欣公益48.0无需脱盒] 全网首发最强的起床跑吃跑喝 我们也有布吉岛客户端呢 免费获取点击右边的代码heshuyou.xyz ><",
        "SilenceFix Best The Config Free 1055680656 ><",
        "[欣欣公益48.0无需脱盒] 全网首发内置进服 无需脱盒 我们也有布吉岛客户端呢 免费获取点击右边的代码heshuyou.xyz ><",
        "[SilenceFix48.0无需脱盒] 你的付费客户端被公益追着打 好丢人哇T-T 我们也有布吉岛客户端呢 点击右边的代码heshuyou.xyz 来获取！",
        "[欣欣公益48.0无需脱盒] 全网首发空岛破甲 我们也有布吉岛客户端呢 免费获取点击右边的代码heshuyou.xyz ><",
        "[SilenceFix48.0无需脱盒] 你的付费客户端被公益追着打哇 好丢人T-T 我们也有布吉岛客户端呢 点击右边的代码heshuyou.xyz 来获取！",
        "SilenceFix Best The Config Free 1055680656 ><",
        "[SilenceFix48.0无需脱盒] 我们免费提高强度供客户端混淆 zkm+jnic且正版 我们也有布吉岛客户端呢 点击右边的代码heshuyou.xyz 来获取！",
        "欣欣公益48.0 全天免费的内置进服花雨庭 学生党可以放学游玩花雨庭！快来免费获取吧 我们也有最强的布吉岛 免费点击代码heshuyou.xyz ><",
        "欣欣公益48.0 全天免费的内置进服花雨庭 看到了就赶快加入我们一起免费使用并获取吧 我们也有最强的布吉岛 免费点击代码heshuyou.xyz ><",
        "SilenceFix Best The Config Free 1055680656 ><",
        "欣欣公益48.0 全天免费的内置进服花雨庭 全网独家起床20CPS最强客户端 不服同装备对刀一下吗 同距离无敌 我们也有最强的布吉岛 免费点击heshuyou.xyz ><"
);

private final BoolValue fake = new BoolValue("fake", true);
private final BoolValue back = new BoolValue("back", true);
// Seconds: default 3, range 0.5–300, step 0.5.
private final NumberValue delay = new NumberValue("delay", 3.0F, 0.5F, 300.0F, 0.5F);
private int currentIndex = 0;
private long lastMessageNanos;
private Object lastWorld;

void onLoad() {
    var sprint = modules.register("ConfigSWBEST", "------空岛高性能模式------", Category.Render);
    sprint.setting(fake);
    sprint.setting(back);
    sprint.setting(delay);
    sprint.onEnable(this::resetTimer);
    sprint.onDisable(() -> {
        lastWorld = null;
        lastMessageNanos = 0L;
    });
    sprint.on(TickEvent.class, event -> tickMessages());
}

private void resetTimer() {
    lastWorld = mc.world;
    lastMessageNanos = System.nanoTime();
}

private void tickMessages() {
    long now = System.nanoTime();
    if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
        lastWorld = null;
        lastMessageNanos = now;
        return;
    }
    if (lastWorld != mc.world) {
        resetTimer();
        return;
    }
    if (mc.currentScreen instanceof ChatScreen
            || (!back.getValue() && (!mc.isWindowFocused() || !mc.mouse.isCursorLocked()))) {
        lastMessageNanos = now;
        return;
    }
    long delayNanos = (long) (delay.getValue().doubleValue() * 1_000_000_000L);
    if (now - lastMessageNanos < delayNanos) return;

    String message = "@" + allAds.get(currentIndex);
    if (fake.getValue()) {
        mc.inGameHud.getChatHud().addMessage(
                Text.literal("<" + mc.player.getName().getString() + "> " + message));
    } else {
        mc.getNetworkHandler().sendChatMessage(message);
    }
    currentIndex = (currentIndex + 1) % allAds.size();
    lastMessageNanos = now;
}
