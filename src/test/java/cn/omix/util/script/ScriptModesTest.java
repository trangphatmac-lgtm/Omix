package cn.omix.util.script;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.*;
import cn.omix.script.api.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptModesTest {
    static class Host extends Module {
        int starts, stops;
        Host(String id) { super(id, Category.Player); setId(id); }
        @Override protected void enable() { if (getScriptMode() == null) starts++; else getScriptMode().activate(); }
        @Override protected void disable() { if (getScriptMode() == null) stops++; else getScriptMode().deactivate(); }
    }
    private ScriptContext context() { return new ScriptContext("Test", 1, Path.of("unused"), new ScriptLog(), ScriptSource.wrap("Test", "")); }
    @Test void syntheticBehaviorTakesOverAndUnloadingSelectedModeRestoresBuiltinAndDisables() {
        Host host = new Host("NoMainMode"); ScriptContext context = context();
        ModeHandle mode = new ModeHandle(context, "mode", host, "Script"); var setting = mode.setting(new BoolValue("Script option", true));
        context.prepared(); context.install(); host.setEnabled(true);
        var registration = ModeHost.install(mode); assertEquals("Behavior", host.getValues().getFirst().getName());
        assertFalse(setting.isVisible()); ModeHost.select(host, "Script");
        assertEquals(1, host.stops); assertSame(mode, host.getScriptMode()); assertFalse(host.isNativeBehaviorActive()); assertTrue(mode.active()); assertTrue(setting.isVisible());
        ModeHost.select(host, "Built-in"); assertNull(host.getScriptMode()); assertTrue(host.isNativeBehaviorActive()); assertEquals(2, host.starts);
        ModeHost.select(host, "Script"); registration.close(); registration.close();
        assertFalse(host.isEnabled()); assertNull(host.getScriptMode()); assertTrue(host.getValues().isEmpty()); context.close();
    }
    @Test void mainModeRestoresPreviousBuiltinAndSettingConflictLeavesHostUntouched() {
        Host host = new Host("Speed"); var selector = new ModeValue("Mode", "Original", "Original", "Other"); host.getValues().add(selector);
        var context = context(); var mode = new ModeHandle(context,"mode",host,"Script"); mode.setting(new BoolValue("Mode",true));
        assertThrows(IllegalArgumentException.class, () -> ModeHost.install(mode)); assertEquals(1, host.getValues().size()); assertEquals(2, selector.getModes().length);
        var another = context(); var good = new ModeHandle(another,"mode",host,"Script"); another.prepared();another.install();
        var registration = ModeHost.install(good); selector.setValue("Other"); selector.setValue("Script"); registration.close();
        assertEquals("Other",selector.getValue()); assertArrayEquals(new String[]{"Original","Other"},selector.getModes());another.close(); context.close();
    }
    @Test void scriptStateRestoresSettingsBeforeEnabling() {
        Host old = new Host("Same"); old.getValues().add(new BoolValue("Flag",true)); old.setKey(88);old.setHidden(true);old.setEnabled(true);
        var state = ScriptState.capture(old);
        Host next = new Host("Same") { @Override protected void enable() { assertTrue(((BoolValue)getValues().getFirst()).getValue()); super.enable(); } };
        next.getValues().add(new BoolValue("Flag",false)); ScriptState.restore(next,state);
        assertTrue(next.isEnabled());assertEquals(88,next.getKey());assertTrue(next.isHidden());assertEquals(1,next.starts);
    }
    @Test void typedQueriesUseScriptProviderAndStopAfterGenerationCloses() {
        Host host = new Host("Reach");var context=context();var mode=new ModeHandle(context,"reach",host,"Script");
        mode.hook(ModeHooks.ENTITY_REACH, range -> range + 1);context.prepared();context.install();var registration=ModeHost.install(mode);
        ModeHost.select(host,"Script");host.setEnabled(true);assertEquals(4.0,ModeHost.query(host,ModeHooks.ENTITY_REACH,3.0,3.0));
        context.close();assertEquals(3.0,ModeHost.query(host,ModeHooks.ENTITY_REACH,3.0,3.0));registration.close();
    }
}
