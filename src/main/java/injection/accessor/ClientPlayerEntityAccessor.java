package injection.accessor;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    @Invoker("sendSprintingPacket")
    void omix$sendSprintingPacket();

    @Accessor(value = "lastYawClient")
    float getLastYaw();

    @Accessor(value = "lastPitchClient")
    float getLastPitch();
}
