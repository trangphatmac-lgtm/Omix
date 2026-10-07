package cn.omix.util.opai.render;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImage;
/** Original PNGs are normal resource textures so reload and sampler lifetimes remain native. */
public final class ModTextures {
 public static Identifier register(String path){return Identifier.of("omix","opai/"+path);}
 public static NativeImage read(Identifier id){
  String path=id.getPath().startsWith("opai/")?id.getPath():"opai/"+id.getPath();
  try(var in=ModTextures.class.getResourceAsStream("/assets/omix/"+path)){return in==null?null:NativeImage.read(in);}
  catch(java.io.IOException e){throw new IllegalStateException("Cannot read "+id,e);}
 }
}
