package injection.accessor;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    @Accessor("lastPlayerInput")
    PlayerInput omix$getLastPlayerInput();

    @Accessor("lastSprinting")
    boolean omix$wasSprinting();

    @Accessor(value = "lastYawClient")
    float getLastYaw();

    @Accessor(value = "lastPitchClient")
    float getLastPitch();
}
