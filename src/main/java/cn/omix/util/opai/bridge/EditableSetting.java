package cn.omix.util.opai.bridge;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
public final class EditableSetting extends Setting implements ChoiceSetting {
 public EditableSetting(Value value){super(value);}
 public String[] options(){return new String[]{value instanceof KeyValue?"Bind key…":"Edit text…"};}
 public String selectionLabel(){return value instanceof KeyValue key?cn.omix.util.misc.KeyUtil.getKeyName(key.getValue()):((TextValue)value).isSensitive()?"••••••":((TextValue)value).getValue();}
 public boolean selected(int index){return false;}
 public void select(int index){var mc=net.minecraft.client.MinecraftClient.getInstance();mc.setScreen(new cn.omix.ui.opai.OpaiValueEditorScreen(mc.currentScreen,value));}
}
