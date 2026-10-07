package injection.accessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(DrawContext.class)
public interface OpaiDrawContextAccessor {
 @Invoker("drawTexturedQuad")
 void omix$blitTinted(RenderPipeline pipeline, Identifier texture, int x0, int x1, int y0, int y1,
   float u0, float u1, float v0, float v1, int color);
}
