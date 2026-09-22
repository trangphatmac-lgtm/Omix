// 最小模块：加载后在 ClickGUI 手动启用 Script Sprint。
void onLoad() {
    var sprint = modules.register("sprint", "Script Sprint", Category.Move);
    sprint.on(TickEvent.class, event -> {
        if (inWorld() && mc.player.forwardSpeed > 0 && !mc.player.isSneaking()) mc.player.setSprinting(true);
    });
}
