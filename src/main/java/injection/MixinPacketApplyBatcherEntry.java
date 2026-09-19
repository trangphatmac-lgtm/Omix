package injection;

import cn.omix.module.impl.move.NoSlowDown;
import cn.omix.util.IMinecraft;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercept on the client thread before application, without throwing a network-thread exception. */
@Mixin(targets = "net.minecraft.network.PacketApplyBatcher$Entry")
public abstract class MixinPacketApplyBatcherEntry<T extends PacketListener> implements IMinecraft {
    @Shadow @Final private T listener;
    @Shadow @Final private Packet<T> packet;

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void omix$bufferNoSlow(CallbackInfo ci) {
        if (!mc.isOnThread() || listener != mc.getNetworkHandler()) return;
        var grim = NoSlowDown.activeGrim();
        if (grim != null && listener.accepts(packet) && grim.receive(packet, listener)) ci.cancel();
    }
}
