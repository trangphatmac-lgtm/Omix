package cn.omix.util.node;

import org.junit.jupiter.api.Test;
import net.ccbluex.liquidbounce.mcef.MCEFPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodePlatformTest {
    @Test
    void mapsAllSupportedOperatingSystemsAndArchitectures() {
        assertEquals(NodePlatform.WINDOWS_X64, NodePlatform.detect("Windows 11", "amd64"));
        assertEquals(NodePlatform.WINDOWS_ARM64, NodePlatform.detect("Windows 11", "aarch64"));
        assertEquals(NodePlatform.MACOS_X64, NodePlatform.detect("Mac OS X", "x86_64"));
        assertEquals(NodePlatform.MACOS_ARM64, NodePlatform.detect("Mac OS X", "arm64"));
        assertEquals(NodePlatform.LINUX_X64, NodePlatform.detect("Linux", "amd64"));
        assertEquals(NodePlatform.LINUX_ARM64, NodePlatform.detect("Linux", "aarch64"));
    }

    @Test
    void mapsEveryMcefPlatform() {
        assertEquals(NodePlatform.WINDOWS_X64, NodePlatform.fromMcef(MCEFPlatform.WINDOWS_AMD64));
        assertEquals(NodePlatform.WINDOWS_ARM64, NodePlatform.fromMcef(MCEFPlatform.WINDOWS_ARM64));
        assertEquals(NodePlatform.MACOS_X64, NodePlatform.fromMcef(MCEFPlatform.MACOS_AMD64));
        assertEquals(NodePlatform.MACOS_ARM64, NodePlatform.fromMcef(MCEFPlatform.MACOS_ARM64));
        assertEquals(NodePlatform.LINUX_X64, NodePlatform.fromMcef(MCEFPlatform.LINUX_AMD64));
        assertEquals(NodePlatform.LINUX_ARM64, NodePlatform.fromMcef(MCEFPlatform.LINUX_ARM64));
    }

    @Test
    void rejectsUnknownPlatforms() {
        assertThrows(IllegalStateException.class, () -> NodePlatform.detect("Solaris", "amd64"));
        assertThrows(IllegalStateException.class, () -> NodePlatform.detect("Linux", "riscv64"));
    }

    @Test
    void everyPlatformHasPinnedHttpsMetadata() {
        assertEquals(NodePlatform.values().length, NodeRuntimeDescriptor.all().size());
        for (NodePlatform platform : NodePlatform.values()) {
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
