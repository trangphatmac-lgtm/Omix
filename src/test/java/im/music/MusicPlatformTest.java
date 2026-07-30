package im.music;

import org.junit.jupiter.api.Test;
import net.ccbluex.liquidbounce.mcef.MCEFPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlatformTest {
    @Test
    void mapsAllSupportedOperatingSystemsAndArchitectures() {
        assertEquals(MusicPlatform.WINDOWS_X64, MusicPlatform.detect("Windows 11", "amd64"));
        assertEquals(MusicPlatform.WINDOWS_ARM64, MusicPlatform.detect("Windows 11", "aarch64"));
        assertEquals(MusicPlatform.MACOS_X64, MusicPlatform.detect("Mac OS X", "x86_64"));
        assertEquals(MusicPlatform.MACOS_ARM64, MusicPlatform.detect("Mac OS X", "arm64"));
        assertEquals(MusicPlatform.LINUX_X64, MusicPlatform.detect("Linux", "amd64"));
        assertEquals(MusicPlatform.LINUX_ARM64, MusicPlatform.detect("Linux", "aarch64"));
    }

    @Test
    void mapsEveryMcefPlatform() {
        assertEquals(MusicPlatform.WINDOWS_X64, MusicPlatform.fromMcef(MCEFPlatform.WINDOWS_AMD64));
        assertEquals(MusicPlatform.WINDOWS_ARM64, MusicPlatform.fromMcef(MCEFPlatform.WINDOWS_ARM64));
        assertEquals(MusicPlatform.MACOS_X64, MusicPlatform.fromMcef(MCEFPlatform.MACOS_AMD64));
        assertEquals(MusicPlatform.MACOS_ARM64, MusicPlatform.fromMcef(MCEFPlatform.MACOS_ARM64));
        assertEquals(MusicPlatform.LINUX_X64, MusicPlatform.fromMcef(MCEFPlatform.LINUX_AMD64));
        assertEquals(MusicPlatform.LINUX_ARM64, MusicPlatform.fromMcef(MCEFPlatform.LINUX_ARM64));
    }

    @Test
    void rejectsUnknownPlatforms() {
        assertThrows(IllegalStateException.class, () -> MusicPlatform.detect("Solaris", "amd64"));
        assertThrows(IllegalStateException.class, () -> MusicPlatform.detect("Linux", "riscv64"));
    }

    @Test
    void everyPlatformHasPinnedHttpsMetadata() {
        assertEquals(MusicPlatform.values().length, NodeRuntimeDescriptor.all().size());
        for (MusicPlatform platform : MusicPlatform.values()) {
            NodeRuntimeDescriptor descriptor = NodeRuntimeDescriptor.forPlatform(platform);
            assertEquals(NodeRuntimeDescriptor.VERSION, descriptor.version());
            assertEquals("https", descriptor.downloadUri().getScheme());
            assertTrue(descriptor.sha256().matches("[0-9a-f]{64}"));
            assertTrue(
                    descriptor.distributionPath().endsWith(".zip")
                            || descriptor.distributionPath().endsWith(".tar.gz")
            );
            assertTrue(descriptor.archiveEntry().endsWith(descriptor.executableName()));
        }
    }
}
