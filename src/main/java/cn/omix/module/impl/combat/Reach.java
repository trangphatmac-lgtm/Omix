package cn.omix.module.impl.combat;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.player.MovementUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.concurrent.ThreadLocalRandom;

public final class Reach extends Module {
    private final NumberValue minRange = new NumberValue("Min Range", 3.0, 3.0, 6.0, 0.05);
    private final NumberValue maxRange = new NumberValue("Max Range", 3.0, 3.0, 6.0, 0.05);
    private final NumberValue chance = new NumberValue("Chance", 100, 0, 100, 1);
    private final BoolValue onlyMoving = new BoolValue("Only Moving", false);
    private final BoolValue onlySprint = new BoolValue("Only Sprint", false);

    private ClientPlayerEntity sampledPlayer;
    private ClientWorld sampledWorld;
    private int sampledTick;
    private double chanceRoll;
    private double rangeRoll;

    public Reach() {
        super("Reach", Category.Combat);
    }

    public double getRange(double vanillaRange) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.player.isSpectator()) {
            return vanillaRange;
        }
        // Share the sample between targeting and attack checks, independently of FPS.
        if (sampledPlayer != mc.player || sampledWorld != mc.world || sampledTick != mc.player.age) {
            sampledPlayer = mc.player;
            sampledWorld = mc.world;
            sampledTick = mc.player.age;
            chanceRoll = ThreadLocalRandom.current().nextDouble();
            rangeRoll = ThreadLocalRandom.current().nextDouble();
        }
        if (onlyMoving.getValue() && !MovementUtil.isMoving()
                || onlySprint.getValue() && !mc.player.isSprinting()
                || chanceRoll * 100.0 >= chance.getValue()) {
            return vanillaRange;
        }
        double lower = Math.min(minRange.getValue(), maxRange.getValue());
        double upper = Math.max(minRange.getValue(), maxRange.getValue());
        return Math.max(vanillaRange, lower + (upper - lower) * rangeRoll);
    }

    @Override
    public void onEnable() {
        clearSample();
    }

    @Override
    public void onDisable() {
        clearSample();
    }

    private void clearSample() {
        sampledPlayer = null;
        sampledWorld = null;
    }
}
