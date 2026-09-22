// Speed 的主 Mode 增加脚本模式；切入后完全退出内置 Speed 行为。
void onLoad() {
    var mode = modes.register("hop", "Speed", "Script Hop");
    var strength = mode.setting(new NumberValue("Script Hop / Vertical", 0.42, 0.1, 0.8, 0.01));
    mode.on(TickEvent.class, event -> {
        if (!inWorld() || !mc.player.isOnGround() || mc.player.forwardSpeed == 0) return;
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, strength.getValue(), velocity.z);
    });
}
