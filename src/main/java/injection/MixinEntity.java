package injection;

import cn.omix.event.impl.LookEvent;
import cn.omix.event.impl.StrafeEvent;
import cn.omix.module.impl.player.Freecam;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.MovementUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity implements IMinecraft {

    @Shadow
    public abstract Vec3d getRotationVector(float pitch, float yaw);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    public float lastYaw;

    @Shadow
    public float lastPitch;

    @Inject(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void getRotationVec(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (((Object) this) instanceof ClientPlayerEntity) {
            float yaw = this.getYaw();
            float pitch = this.getPitch();
            float prevYaw = this.lastYaw;
            float prevPitch = this.lastPitch;

            LookEvent event = new LookEvent(new float[]{yaw, pitch}, new float[]{prevYaw, prevPitch});
            instance.getEventManager().call(event);

            float getYaw = MathHelper.lerp(tickDelta, event.getLastRotation()[0], event.getRotation()[0]);
            float getPitch = MathHelper.lerp(tickDelta, event.getLastRotation()[1], event.getRotation()[1]);

            cir.setReturnValue(this.getRotationVector(getPitch, getYaw));
        }
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void omix$freecamTurn(double deltaYaw, double deltaPitch, CallbackInfo ci) {
        if ((Object) this != mc.player || !Freecam.isActive()) return;

        Freecam.turn(deltaYaw, deltaPitch);
        ci.cancel();
    }

    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d updateVelocity(Vec3d movementInput, float speed, float yaw) {
        if (((Object) this) instanceof ClientPlayerEntity) {
            StrafeEvent event = new StrafeEvent(yaw, speed);
            instance.getEventManager().call(event);

            if (event.isCancelled()) {
                return Vec3d.ZERO;
            }

            return MovementUtil.movementInputToVelocity(movementInput, event.getFriction(), event.getYaw());
        }

        return MovementUtil.movementInputToVelocity(movementInput, speed, yaw);
    }
}
