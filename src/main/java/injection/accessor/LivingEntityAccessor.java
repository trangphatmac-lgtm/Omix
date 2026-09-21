package injection.accessor;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("LIVING_FLAGS")
    static TrackedData<Byte> omix$getLivingFlags() {
        throw new AssertionError("Mixin accessor");
    }

    @Accessor("jumpingCooldown")
    int getJumpingCooldown();

    @Accessor("jumpingCooldown")
    void setJumpingCooldown(int jumpingCooldown);
}
