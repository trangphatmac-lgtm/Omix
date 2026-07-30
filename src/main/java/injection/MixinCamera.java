package injection;

import cn.omix.Client;
import cn.omix.module.impl.player.Freecam;
import cn.omix.module.impl.render.ViewClip;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    private Entity focusedEntity;

    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void moveBy(float x, float y, float z);

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void omix$clipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) {
            return;
        }

        ViewClip viewClip = client.getModuleManager().getModule(ViewClip.class);
        if ((viewClip != null && viewClip.isEnabled()) || Freecam.isActive()) {
            cir.setReturnValue(desiredCameraDistance);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void omix$freecamCamera(World world, Entity entity, boolean thirdPersonView, boolean inverseView,
                                     float tickDelta, CallbackInfo ci) {
        if (!Freecam.isActive()) return;

        this.thirdPerson = true;
        setPos(Freecam.getCameraPosition(tickDelta));
        setRotation(Freecam.getCameraYaw(), Freecam.getCameraPitch());

        if (!thirdPersonView) return;

        if (inverseView) {
            setRotation(Freecam.getCameraYaw() + 180.0F, -Freecam.getCameraPitch());
        }

        float distance = 4.0F;
        float scale = 1.0F;
        if (focusedEntity instanceof LivingEntity livingEntity) {
            scale = livingEntity.getScale();
            distance = (float) livingEntity.getAttributeValue(EntityAttributes.CAMERA_DISTANCE);
        }

        float finalDistance = scale * distance;
        if (focusedEntity != null && focusedEntity.hasVehicle()
                && focusedEntity.getVehicle() instanceof LivingEntity vehicle) {
            finalDistance = Math.max(
                    finalDistance,
                    vehicle.getScale() * (float) vehicle.getAttributeValue(EntityAttributes.CAMERA_DISTANCE)
            );
        }

        moveBy(-finalDistance, 0.0F, 0.0F);
    }
}
