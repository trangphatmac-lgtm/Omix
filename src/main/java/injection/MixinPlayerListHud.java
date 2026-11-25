package injection;

import com.llamalad7.mixinextras.sugar.Local;
import cn.omix.util.world.adjustdetect.AdjustNameMarker;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

@Mixin(PlayerListHud.class)
public abstract class MixinPlayerListHud {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void omix$markDetectedPlayer(PlayerListEntry entry, CallbackInfoReturnable<Text> result) {
        result.setReturnValue(AdjustNameMarker.prefix(entry.getProfile().id(), result.getReturnValue()));
    }

    // The third fill in render is vanilla's per-player row background. The first
    // two fills are the header and the table frame.
    @ModifyArgs(method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 2))
    private void omix$highlightDetectedPlayer(Args args, @Local(index = 5) List<PlayerListEntry> entries,
                                              @Local(index = 22) int row) {
        if (row < entries.size() && AdjustNameMarker.marked(entries.get(row).getProfile().id()))
            args.set(4, 0x32FF6363);
    }
}
