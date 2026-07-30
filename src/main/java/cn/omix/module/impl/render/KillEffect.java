package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public final class KillEffect extends Module {
    public static int killedTimes = 0;

    private final BoolValue lightning = new BoolValue("Lightning", true);
    private final BoolValue explosion = new BoolValue("Explosion", true);
    private final BoolValue blood = new BoolValue("Blood", true);
    private LivingEntity target;

    public KillEffect() {
        super("KillEffect", Category.Render);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (target == null || mc.world == null) return;

        if (!mc.world.hasEntity(target) || target.getHealth() <= 0.0F) {
            playEffects(target);
            target = null;
            killedTimes++;
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        target = null;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            target = livingEntity;
        }
    }

    private void playEffects(LivingEntity killedEntity) {
        if (mc.world == null) return;

        if (lightning.getValue()) {
            LightningEntity lightningEntity = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
            lightningEntity.refreshPositionAfterTeleport(killedEntity.getX(), killedEntity.getY(), killedEntity.getZ());
            lightningEntity.setId((int) (-Math.random() * 100000.0));
            mc.world.addEntity(lightningEntity);
            playGlobalSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
        }

        if (explosion.getValue()) {
            for (int i = 0; i <= 8; i++) {
                mc.particleManager.addEmitter(killedEntity, ParticleTypes.FLAME);
            }
            playGlobalSound(SoundEvents.ITEM_FIRECHARGE_USE);
        }

        if (blood.getValue()) {
            Vec3d velocity = killedEntity.getVelocity();
            BlockStateParticleEffect redstoneFragment = new BlockStateParticleEffect(
                    ParticleTypes.BLOCK,
                    Blocks.REDSTONE_BLOCK.getDefaultState()
            );

            for (int i = 0; i < 10; i++) {
                mc.world.addParticleClient(
                        redstoneFragment,
                        killedEntity.getX(),
                        killedEntity.getBodyY(0.5),
                        killedEntity.getZ(),
                        velocity.x + nextFloat(-0.5F, 0.5F),
                        velocity.y + nextFloat(-0.5F, 0.5F),
                        velocity.z + nextFloat(-0.5F, 0.5F)
                );
            }
        }
    }

    private void playGlobalSound(SoundEvent sound) {
        mc.getSoundManager().play(new PositionedSoundInstance(
                sound.id(),
                SoundCategory.MASTER,
                1.0F,
                1.0F,
                SoundInstance.createRandom(),
                false,
                0,
                SoundInstance.AttenuationType.NONE,
                0.0,
                0.0,
                0.0,
                true
        ));
    }

    public static float nextFloat(float startInclusive, float endInclusive) {
        if (startInclusive == endInclusive || endInclusive - startInclusive <= 0.0F) {
            return startInclusive;
        }
        return (float) (startInclusive + (endInclusive - startInclusive) * Math.random());
    }

    public double easeInOutCirc(double x) {
        return x < 0.5
                ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * x, 2.0))) / 2.0
                : (Math.sqrt(1.0 - Math.pow(-2.0 * x + 2.0, 2.0)) + 1.0) / 2.0;
    }
}
