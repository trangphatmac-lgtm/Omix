package cn.omix.util.sigma;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigmaProfileStorageTest {
    @Test void profileNamesCannotEscapeTheDirectoryOrTargetWindowsDevices() {
        for (String name : new String[]{"", " ", "../x", "..\\x", "/root", "a:b", "a\nname", "a.", "aux", "CON.txt", "LPT1", "com9.json", " padded", "padded "})
            assertFalse(SigmaProfileStorage.validName(name), name);
        for (String name : new String[]{"Default Copy 1", "中文配置", "console", "combat.v2", "COM10"})
            assertTrue(SigmaProfileStorage.validName(name), name);
    }
}
