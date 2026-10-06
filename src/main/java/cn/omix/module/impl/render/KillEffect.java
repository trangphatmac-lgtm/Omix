package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.render.KillMemeOverlay;
import cn.omix.util.sound.WavSounds;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
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
    private final BoolValue sound = new BoolValue("Sound", false);
    private final ModeValue soundMode = new ModeValue("Sound Mode", "XinXin", () -> sound.getValue(),
            "XinXin", "bing-bing-bing");
    private final BoolValue meme = new BoolValue("Meme", false);
    private final NumberValue memeDuration = new NumberValue("Meme Duration", 3, 1, 10, .5F,
            () -> meme.getValue());
    private final KillMemeOverlay memeOverlay = new KillMemeOverlay();
    private LivingEntity target;

    public KillEffect() {
        super("KillEffect", Category.Render);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!meme.getValue()) memeOverlay.clear();
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
        memeOverlay.clear();
    }

    @Override
    public void onDisable() {
        target = null;
        memeOverlay.clear();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!meme.getValue() || mc.world == null) {
            memeOverlay.clear();
            return;
        }
        memeOverlay.project(event, System.nanoTime());
    }

    /** Drawn after the HUD, outside its FPS-limited cache, so the world anchor stays current. */
    public void renderMeme(DrawContext context) {
        if (!isNativeBehaviorActive() || !meme.getValue() || mc.world == null || mc.player == null) {
            memeOverlay.clear();
            return;
        }
        memeOverlay.draw(context, System.nanoTime());
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

        if (meme.getValue()) {
            memeOverlay.spawn(killedEntity.getX(), killedEntity.getBodyY(.5), killedEntity.getZ(),
                    System.nanoTime(), memeDuration.getValue());
        }

        if (sound.getValue()) {
            WavSounds.play(WavSounds.Channel.KILL_EFFECT, soundMode.is("bing-bing-bing")
                    ? "/assets/omix/sounds/killeffect/bing-bing-bing.wav"
                    : "/assets/omix/sounds/xinxin/kill.wav");
        }

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
