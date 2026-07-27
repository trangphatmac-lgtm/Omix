package injection;

import cn.omix.Client;
import cn.omix.module.impl.exploits.ResourcepackSpoof;
import cn.omix.util.network.PacketUtil;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class MixinClientCommonNetworkHandler {

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void onResourcePackSend(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return;

        ResourcepackSpoof module = Client.instance.getModuleManager().getModule(ResourcepackSpoof.class);
        if (module == null || !module.isEnabled()) return;

        module.spoofed();
        UUID id = packet.id();
        sendStatus(id, ResourcePackStatusC2SPacket.Status.ACCEPTED);
        sendStatus(id, ResourcePackStatusC2SPacket.Status.DOWNLOADED);
        sendStatus(id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
        ci.cancel();
    }

    private void sendStatus(UUID id, ResourcePackStatusC2SPacket.Status status) {
        ResourcePackStatusC2SPacket packet = new ResourcePackStatusC2SPacket(id, status);
        PacketUtil.getPackets().add(packet);
        this.sendPacket(packet);
    }
}
