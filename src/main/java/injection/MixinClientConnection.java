package injection;

import cn.omix.Client;
import cn.omix.event.impl.PacketEvent;
import cn.omix.ui.screen.impl.proxy.ProxyScreen;
import cn.omix.util.IMinecraft;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.network.PacketLogHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.PacketListener;
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
        ClientConnection connection = (ClientConnection) (Object) this;
        if (PacketUtil.getPackets().remove(packet) || PacketUtil.isBypassingEvents()) {
            PacketLogHooks.sent(connection, packet, false, true);
            return;
        }
        if (Client.instance == null || Client.instance.getEventManager() == null) return;

        PacketEvent event = new PacketEvent(packet, PacketEvent.Type.Send);
        Client.instance.getEventManager().call(event);

        PacketLogHooks.sent(connection, event.getPacket() == null ? packet : event.getPacket(), event.isCancelled(), false);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        Packet<?> replacement = event.getPacket();
        if (replacement != null && replacement != packet) {
            ci.cancel();
            PacketUtil.runWithoutEvents(() -> {
                PacketLogHooks.replacement(() -> connection.send(replacement, listener, flush));
                return null;
            });
        }
    }

    @WrapOperation(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V"))
    private void omix$observeReceived(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        PacketLogHooks.receive((ClientConnection) (Object) this, packet, () -> original.call(packet, listener));
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
