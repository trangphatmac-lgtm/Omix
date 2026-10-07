package cn.omix.ui.opai;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.module.impl.render.ClickGui;
import cn.omix.util.opai.render.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import static org.lwjgl.nanovg.NanoVG.*;
/** Native value editing uses the same palette/fonts as the imported controls. */
public final class OpaiValueEditorScreen extends Screen implements NanoGui {
 private final Screen parent;private final Value value;private TextFieldWidget editor;
 public OpaiValueEditorScreen(Screen parent,Value value){super(Text.literal(value.getName()));this.parent=parent;this.value=value;}
 @Override protected void init(){if(value instanceof TextValue text){editor=new TextFieldWidget(textRenderer,width/2-140,height/2-6,280,20,Text.literal(value.getName()));editor.setMaxLength(32767);editor.setText(text.getValue());editor.setFocused(true);}}
 @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
 @Override public void render(DrawContext c,int x,int y,float delta){}
 @Override public void renderNano(){var p=ClickGui.currentOpaiPalette();float x=width/2f-156,y=height/2f-45;NVGRenderer.roundedRect(x,y,312,90,7,p.body());var f=FontRepository.getFont("googlesans-medium");f.drawString(value.getName(),x+12,y+16,10,p.text(),false,NVG_ALIGN_LEFT|NVG_ALIGN_MIDDLE);String label=value instanceof KeyValue?"Press a key • Delete clears • Esc cancels":((TextValue)value).isSensitive()?"•".repeat(Math.min(40,editor.getText().length())):editor.getText();NVGRenderer.scissor(x+12,y+29,288,24,()->f.drawString(label,x+12,y+41,10,p.text(),false,NVG_ALIGN_LEFT|NVG_ALIGN_MIDDLE));f.drawString("Enter saves • Esc cancels",x+12,y+72,8,p.accent(),false,NVG_ALIGN_LEFT|NVG_ALIGN_MIDDLE);}
 @Override public boolean keyPressed(KeyInput input){if(input.key()==GLFW.GLFW_KEY_ESCAPE){close();return true;}if(value instanceof KeyValue key){key.setValue(input.key()==GLFW.GLFW_KEY_DELETE||input.key()==GLFW.GLFW_KEY_BACKSPACE?-1:input.key());close();return true;}if(input.key()==GLFW.GLFW_KEY_ENTER||input.key()==GLFW.GLFW_KEY_KP_ENTER){((TextValue)value).setValue(editor.getText());close();return true;}return editor.keyPressed(input);}
 @Override public boolean charTyped(CharInput input){return editor!=null&&editor.charTyped(input);}
 @Override public boolean mouseClicked(Click click,boolean twice){if(value instanceof KeyValue key&&key.isMouseAllowed()){key.setValue(-100+click.button());close();return true;}return editor!=null&&editor.mouseClicked(click,twice);}
 @Override public void close(){client.setScreen(parent);cn.omix.util.opai.bridge.ConfigManager.saveQuietly();}
 @Override public boolean shouldPause(){return false;}
}
