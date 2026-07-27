package injection;

import cn.omix.event.impl.Render2DEvent;
import cn.omix.module.impl.render.HUD;
import cn.omix.util.IMinecraft;
import cn.omix.util.misc.TimerUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud implements IMinecraft {

    @Unique
    private final GuiRenderState cachedHudState = new GuiRenderState();

    @Unique
    private final TimerUtil timer = new TimerUtil();

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (timer.hasTimeElapsed(1000L / instance.getModuleManager().getModule(HUD.class).getHudFps().getValue())) {
            timer.reset();
            cachedHudState.clear();
            DrawContext cacheContext = new DrawContext(mc, cachedHudState, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            instance.getEventManager().call(new Render2DEvent(cacheContext, tickCounter.getTickProgress(false)));
        }

        cachedHudState.forEachSimpleElement(context.state::addSimpleElement, GuiRenderState.LayerFilter.ALL);
        cachedHudState.forEachTextElement(context.state::addText);
        cachedHudState.forEachItemElement(context.state::addItem);
        cachedHudState.forEachSpecialElement(context.state::addSpecialElement);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        if (hud.isEnabled() && hud.getHudMode().is("Omix") && hud.getNoPotionIcons().getValue()) {
            ci.cancel();
        }
    }
}
