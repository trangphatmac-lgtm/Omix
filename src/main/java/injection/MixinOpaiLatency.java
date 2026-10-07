package injection;

import cn.omix.util.opai.OpaiHud;
import cn.omix.util.opai.island.DynamicIslandLatency;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinOpaiLatency implements DynamicIslandLatency.Source {
    @Unique private final DynamicIslandLatency omix$islandLatency = new DynamicIslandLatency();

    @Inject(method = "tick", at = @At("TAIL"))
    private void omix$sample(CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        var listener = (ClientPlayNetworkHandler) (Object) this;
        if (mc.getNetworkHandler() != listener || mc.player == null || mc.isInSingleplayer()
                || !OpaiHud.enabled(OpaiHud.Widget.STATUS_BAR)) {
            omix$islandLatency.reset();
            return;
        }
        long token = omix$islandLatency.request(System.nanoTime() / 1_000_000L);
        if (token != 0) listener.sendPacket(new QueryPingC2SPacket(token));
    }

    @Inject(method = "onPingResult", at = @At("HEAD"), cancellable = true)
    private void omix$receive(PingResultS2CPacket packet, CallbackInfo ci) {
        if (omix$islandLatency.receive(packet.startTime(), System.nanoTime() / 1_000_000L)) ci.cancel();
    }

    @Override public int omix$getLivePing(long now, int fallback) {
        return omix$islandLatency.latency(now, fallback);
    }
}
