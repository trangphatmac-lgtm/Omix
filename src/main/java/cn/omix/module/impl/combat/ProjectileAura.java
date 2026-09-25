package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.combat.projectile.ProjectileAuraEngine;
import cn.omix.util.combat.projectile.ProjectileAuraHost;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class ProjectileAura extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Egg & Snowball", "Egg & Snowball", "Rod", "Auto");
    private final NumberValue range = new NumberValue("Range", 12, 4, 30, .1);
    private final BoolValue dynamicDelay = new BoolValue("Dynamic Delay", true, () -> !mode.is("Rod"));
    private final NumberValue throwDelay = new NumberValue("Throw Delay", 500, 50, 1000, 50, () -> !mode.is("Rod"));
    private final NumberValue rodTimeout = new NumberValue("Rod Timeout", 300, 100, 1000, 10,
            () -> mode.is("Rod") || mode.is("Auto"));
    private final BoolValue requiresKillAura = new BoolValue("Requires KillAura", true);
    private final BoolValue pauseDuringAttack = new BoolValue("Pause During Attack", true);
    private final BoolValue silent = new BoolValue("Silent", true);

    private final ProjectileAuraHost host = new ProjectileAuraHost(this);
    private ProjectileAuraEngine engine = new ProjectileAuraEngine(host);

    public ProjectileAura() {
        super("ProjectileAura", Category.Combat);
    }

    @EventTarget
    @EventPriority(4)
    public void onUpdate(UpdateEvent event) {
        if (host.contextChanged()) discardContext();
        syncSettings();
        host.beginUpdate();
        engine.onUpdate();
        host.finishUpdate(engine.ownedRotation);
        setSuffix(mode.getValue());
    }

    @EventTarget
    public void onRotationRequest(RotationRequestEvent event) {
        if (isNativeBehaviorActive()) host.submitRotation(event, engine.ownedRotation);
    }

    @EventTarget
    @EventPriority(0)
    public void onInput(HandleInputEvent event) {
        if (host.contextChanged()) discardContext();
        syncSettings();
        engine.onInput();
        host.finishUpdate(engine.ownedRotation);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost()) host.flushSlotRelease();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        discardContext();
    }

    @Override
    public void onDisable() {
        // Like the reference, disabling does not send a reel-in interaction.
        engine.onDisable();
        host.finishUpdate(null);
        host.flushSlotRelease();
    }

    public ItemStack renderStack(Hand hand, ItemStack original) {
        if (!isNativeBehaviorActive() || host.contextChanged()) return original;
        engine.settings.silent = silent.getValue();
        return host.unwrap(engine.onRenderHand(ProjectileAuraHost.hand(hand), host.wrap(original)));
    }

    private void discardContext() {
        host.discard();
        engine = new ProjectileAuraEngine(host);
    }

    private void syncSettings() {
        var settings = engine.settings;
        settings.mode = mode.is("Rod") ? ProjectileAuraEngine.Mode.ROD
                : mode.is("Auto") ? ProjectileAuraEngine.Mode.AUTO : ProjectileAuraEngine.Mode.EGG_AND_SNOWBALL;
        settings.range = range.getValue();
        settings.dynamicDelay = dynamicDelay.getValue();
        settings.throwDelayMs = throwDelay.getValue();
        settings.rodTimeoutMs = rodTimeout.getValue();
        settings.requiresKillAura = requiresKillAura.getValue();
        settings.pauseDuringAttack = pauseDuringAttack.getValue();
        settings.silent = silent.getValue();
    }
}
