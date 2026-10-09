package injection;

import cn.omix.event.impl.Render2DEvent;
import cn.omix.module.impl.render.HUD;
import cn.omix.module.impl.render.KillEffect;
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

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"))
    private net.minecraft.text.Text omix$translateSidebarTitle(net.minecraft.text.Text text) {
        return cn.omix.util.translation.TranslationHooks.scoreboard(text);
    }

    // The stream mapper is a synthetic method; match its invocation, not its unstable generated name.
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"))
    private net.minecraft.text.MutableText omix$translateSidebarEntry(net.minecraft.text.MutableText text) {
        return cn.omix.util.translation.TranslationHooks.scoreboard(text).copy();
    }

    @Unique
    private final GuiRenderState cachedHudState = new GuiRenderState();

    @Unique
    private final TimerUtil timer = new TimerUtil();

    @Unique private net.minecraft.client.world.ClientWorld sigma$cachedWorld;
    @Unique private int sigma$width, sigma$height, sigma$scale, sigma$blurGeneration;
    @Unique private int sigma$framebufferWidth, sigma$framebufferHeight;

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight(), scale = mc.getWindow().getScaleFactor();
        int framebufferWidth = mc.getWindow().getFramebufferWidth(), framebufferHeight = mc.getWindow().getFramebufferHeight();
        int blurGeneration = cn.omix.util.sigma.SigmaBlur.generation();
        if (instance.getModuleManager().getModule(HUD.class).getHudMode().is("Opai") || sigma$cachedWorld != mc.world || sigma$width != width || sigma$height != height || sigma$scale != scale || sigma$blurGeneration != blurGeneration
                || sigma$framebufferWidth != framebufferWidth || sigma$framebufferHeight != framebufferHeight
                || timer.hasTimeElapsed(1000L / instance.getModuleManager().getModule(HUD.class).getHudFps().getValue())) {
            sigma$cachedWorld = mc.world; sigma$width = width; sigma$height = height; sigma$scale = scale; sigma$blurGeneration = blurGeneration;
            sigma$framebufferWidth = framebufferWidth; sigma$framebufferHeight = framebufferHeight;
            timer.reset();
            cachedHudState.clear();
            DrawContext cacheContext = new DrawContext(mc, cachedHudState, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            instance.getEventManager().call(new Render2DEvent(cacheContext, tickCounter.getTickProgress(false)));
        }

        cachedHudState.forEachSimpleElement(context.state::addSimpleElement, GuiRenderState.LayerFilter.ALL);
        cachedHudState.forEachTextElement(context.state::addText);
        cachedHudState.forEachItemElement(context.state::addItem);
        cachedHudState.forEachSpecialElement(context.state::addSpecialElement);
        cn.omix.util.sigma.SigmaRearView.get().draw(context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void omix$killMemeOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        KillEffect killEffect = instance.getModuleManager().getModule(KillEffect.class);
        if (killEffect != null) killEffect.renderMeme(context);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        if (cn.omix.util.opai.OpaiHud.enabled(cn.omix.util.opai.OpaiHud.Widget.POTION_STATUS) || hud.isNativeBehaviorActive() && hud.getHudMode().is("Omix") && hud.getNoPotionIcons().getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"))
    private void omix$sigmaMoveScoreboard(DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, cn.omix.util.sigma.SigmaHud.scoreboardOffset(objective));
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("RETURN"))
    private void omix$sigmaRestoreScoreboard(DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        context.getMatrices().popMatrix();
    }

    // The first fill is the title background. Its bounds already include vanilla's
    // final measurements, including translation and NickHider replacements.
    @org.spongepowered.asm.mixin.injection.ModifyArgs(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0))
    private void omix$avoidClassicModuleList(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,
            DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        if (!hud.isNativeBehaviorActive() || !hud.getHudMode().is("Classic") || mc.getDebugHud().shouldShowDebugHud()) return;
        int rows = (int) objective.getScoreboard().getScoreboardEntries(objective).stream()
                .filter(entry -> !entry.hidden()).limit(15).count();
        var bounds = new cn.omix.util.render.HudSidebarLayout.Bounds(
                (int) args.get(0), (int) args.get(1), (int) args.get(2), (int) args.get(3) + rows * 9 + 1);
        var offset = cn.omix.util.render.HudSidebarLayout.avoidClassicModules(bounds,
                context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().translate(offset.x(), offset.y());
    }
}
