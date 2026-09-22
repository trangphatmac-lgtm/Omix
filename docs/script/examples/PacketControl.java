import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

void onLoad() {
    var feature = modules.register("packet_control", "Script Packet Control", Category.Player);
    var cancelSwing = feature.setting(new BoolValue("Cancel swing", false));
    // Priority 0 runs before PacketManager's buffering at 10. Callback is synchronous.
    packets.onSend(feature, event -> {
        if (!(event.getPacket() instanceof HandSwingC2SPacket)) return;
        if (cancelSwing.getValue()) packets.cancel(event);
        // Alternative: packets.replace(event, new HandSwingC2SPacket(Hand.OFF_HAND));
    });
    commands.register("script-swing", args -> {
        packets.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
        // packets.sendWithoutEvents(...) bypasses ALL PacketEvent listeners, including Blink.
    }, "script-swing");
    // For interactions, let the actual world allocate a prediction sequence:
    // packets.sendSequenced(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
    // Register receive listeners with packets.onReceive(feature, event -> ...).
    // They run on the original network thread. Use tasks.client for module/world changes.
}
