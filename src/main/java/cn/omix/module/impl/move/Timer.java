package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.ChatScreenEvent;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.render.HUD;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.move.TimerBalance;
import cn.omix.util.move.TimerBalanceHud;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Locale;

public class Timer extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Classic", "Classic", "Balance");
    private final NumberValue speed = new NumberValue("Speed", 1.0F, 0.01F, 5.0F, 0.01F, () -> mode.is("Classic"));
    private final ModeValue releaseButton = new ModeValue("Release Button", "Middle", () -> mode.is("Balance"), "Middle", "Side 1", "Side 2");
    private final NumberValue boostSpeed = new NumberValue("Boost Speed", 1.6F, 1.05F, 3.0F, 0.05F, () -> mode.is("Balance"));
    private final NumberValue maxBalance = new NumberValue("Max Balance", 9.0F, 1.0F, 12.0F, 1.0F, () -> mode.is("Balance"));
    // Hidden values let the existing configuration layer persist chat-drag placement.
    private final NumberValue balanceHudX = new NumberValue("Balance HUD X", -1, -1, 1, 0.0001F, () -> false);
    private final NumberValue balanceHudY = new NumberValue("Balance HUD Y", -1, -1, 1, 0.0001F, () -> false);
    private final TimerBalanceHud balanceHud = new TimerBalanceHud(balanceHudX, balanceHudY);
    private float balanceSpeed = 1.0F;
    private final TimerBalance balance = new TimerBalance(new TimerBalance.Host() {
        public boolean hasPlayer() { return mc.player != null; }
        public boolean isTimerEnabled() { return isNativeBehaviorActive(); }
        public boolean isLongJumpEnabled() { return getModule(LongJump.class).isEnabled(); }
        public boolean hasKillAuraTarget() { return getModule(Aura.class).getTarget() != null; }
        public boolean forwardPressed() { return mc.options.forwardKey.isPressed(); }
        public boolean backPressed() { return mc.options.backKey.isPressed(); }
        public boolean leftPressed() { return mc.options.leftKey.isPressed(); }
        public boolean rightPressed() { return mc.options.rightKey.isPressed(); }
        public int mouseButtonState(int button) { return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button); }
        public float scaledWindowWidth() { return mc.getWindow().getScaledWidth(); }
        public float scaledWindowHeight() { return mc.getWindow().getScaledHeight(); }
        public TimerBalance.DragPosition createDrag(String id, float x, float y) {
            balanceHud.initialize(x, y);
            return balanceHud;
        }
        public void setTimerMultiplier(float multiplier) { balanceSpeed = multiplier; }
        public Color themeColor() { return new Color(getModule(HUD.class).getColor(), true); }
        public void baseOnDisable() { }
    });

    public Timer() {
        super("Timer", Category.Move);
    }

    @Override
    public void onEnable() {
        TimerSpeedUtil.setTimerOverride(() -> mode.is("Balance") ? balanceSpeed
                : mc.player == null || mc.world == null ? 1.0F : speed.getValue(), () -> mode.is("Balance"));
    }

    @Override
    public void onDisable() {
        balance.onDisable();
        balanceHud.stopDragging();
        // Balance's original disable writes 1x; retain Classic's existing release semantics.
        if (mode.is("Balance")) TimerSpeedUtil.reset();
        TimerSpeedUtil.clearTimerOverride();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        syncBalanceSettings();
        balance.onPlayerUpdate();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        syncBalanceSettings();
        balanceHud.render(event.getContext(), balance);
    }

    @EventTarget
    public void onChatScreen(ChatScreenEvent event) {
        if (mode.is("Balance")) balanceHud.drag(event.getMouseX(), event.getMouseY());
    }

    private void syncBalanceSettings() {
        balance.settings.mode = mode.getValue();
        balance.settings.gameSpeed = speed.getValue();
        balance.settings.releaseButton = releaseButton.getValue();
        balance.settings.boostSpeed = boostSpeed.getValue();
        balance.settings.maxBalance = maxBalance.getValue();
    }

    @Override
    public String getSuffix() {
        return mode.is("Balance") ? "Balance" : String.format(Locale.ROOT, "%.2fx", speed.getValue());
    }
}
