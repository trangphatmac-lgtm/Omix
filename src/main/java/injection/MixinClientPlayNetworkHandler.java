package injection;

import cn.omix.event.impl.PlayerPositionLookEvent;
import cn.omix.util.IMinecraft;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler implements IMinecraft {

    @Inject(
            method = "onPlayerPositionLook",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void afterPlayerPositionApplied(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (mc.player == null) return;

        instance.getEventManager().call(new PlayerPositionLookEvent(new Vec3d(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
        )));
    }
}
