package injection;

import cn.remix.Client;
import cn.remix.event.impl.PacketEvent;
import cn.remix.ui.screen.impl.proxy.ProxyScreen;
import cn.remix.util.IMinecraft;
import cn.remix.util.network.PacketUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class MixinClientConnection implements IMinecraft {

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void send(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (PacketUtil.getPackets().remove(packet)) return;
        if (PacketUtil.isBypassingEvents()) return;
        if (Client.instance == null || Client.instance.getEventManager() == null) return;

        PacketEvent event = new PacketEvent(packet, PacketEvent.Type.Send);
        Client.instance.getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        Packet<?> replacement = event.getPacket();
        if (replacement != null && replacement != packet) {
            ci.cancel();
            PacketUtil.runWithoutEvents(() -> {
                ((ClientConnection) (Object) this).send(replacement, listener, flush);
                return null;
            });
        }
    }

    @Mixin(targets = "net.minecraft.network.ClientConnection$1")
    public static class Proxy {
        @Inject(method = "initChannel", at = @At("TAIL"), remap = false)
        private void onInitChannel(Channel channel, CallbackInfo ci) {
            if (ProxyScreen.getProxy() != null) {
                channel.pipeline().addFirst("proxy", ProxyScreen.getProxy().getHandler());
            }
        }
    }
}
