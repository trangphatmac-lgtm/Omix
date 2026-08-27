package injection;

import cn.omix.Client;
import cn.omix.event.impl.RotationAppliedEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.impl.player.ChestArua;
import cn.omix.util.IMinecraft;
import cn.omix.util.Util;
import im.webui.WebUiRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.InactivityFpsLimiter;
import net.minecraft.client.world.ClientWorld;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient implements IMinecraft {
    @Final
    @Shadow
    public GameOptions options;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;instance:Lnet/minecraft/client/MinecraftClient;", shift = At.Shift.AFTER, opcode = Opcodes.PUTSTATIC))
    private void preInit(CallbackInfo ci) {
        Client.instance = new Client();
        Client.logger = LogManager.getLogger(Client.name);
    }

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;inGameHud:Lnet/minecraft/client/gui/hud/InGameHud;", shift = At.Shift.AFTER, opcode = Opcodes.PUTFIELD))
    private void postInit(CallbackInfo ci) {
        Client.instance.init();
    }

    @Inject(method = "stop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;close()V", shift = At.Shift.AFTER))
    private void stop(CallbackInfo ci) {
        instance.shutdown();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void omix$stopWebUiBeforeGraphics(CallbackInfo ci) {
        WebUiRuntime.getInstance().stop();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isOnGround()) {
            Util.offGroundTicks = 0;
            Util.onGroundTicks++;
        } else {
            Util.onGroundTicks = 0;
            Util.offGroundTicks++;
        }

        instance.getEventManager().call(new TickEvent());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V", shift = At.Shift.BEFORE))
    private void beforeHandleInputEvents(CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;
        instance.getEventManager().call(new RotationAppliedEvent());
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void omix$chestAruaManualUse(CallbackInfo ci) {
        if (instance == null || instance.getModuleManager() == null) return;

        ChestArua chestArua = instance.getModuleManager().getModule(ChestArua.class);
        if (chestArua != null && chestArua.handleManualUse()) {
            ci.cancel();
        }
    }

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At("HEAD"))
    private void setWorld(ClientWorld world, CallbackInfo ci) {
        instance.getEventManager().call(new WorldEvent(world));
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/InactivityFpsLimiter;update()I"))
    private int render(InactivityFpsLimiter instance) {
        return options.getMaxFps().getValue();
    }
}
