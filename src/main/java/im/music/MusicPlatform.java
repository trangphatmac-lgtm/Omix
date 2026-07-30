package im.music;

import java.util.Locale;
import net.ccbluex.liquidbounce.mcef.MCEFPlatform;

public enum MusicPlatform {
    WINDOWS_X64("windows-x64"),
    WINDOWS_ARM64("windows-arm64"),
    MACOS_X64("macos-x64"),
    MACOS_ARM64("macos-arm64"),
    LINUX_X64("linux-x64"),
    LINUX_ARM64("linux-arm64");

    private final String id;

    MusicPlatform(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean windows() {
        return this == WINDOWS_X64 || this == WINDOWS_ARM64;
    }

    public static MusicPlatform current() {
        return fromMcef(MCEFPlatform.getPlatform());
    }

    public static MusicPlatform fromMcef(MCEFPlatform platform) {
        if (platform == null) {
            throw new IllegalStateException("MCEF did not recognize this music runtime platform");
        }
        return switch (platform) {
            case WINDOWS_AMD64 -> WINDOWS_X64;
            case WINDOWS_ARM64 -> WINDOWS_ARM64;
            case MACOS_AMD64 -> MACOS_X64;
            case MACOS_ARM64 -> MACOS_ARM64;
            case LINUX_AMD64 -> LINUX_X64;
            case LINUX_ARM64 -> LINUX_ARM64;
        };
    }

    public static MusicPlatform detect(String osName, String architecture) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.contains("amd64") || arch.contains("x86_64") || arch.equals("x64");
        if (!arm64 && !x64) {
            throw new IllegalStateException("Unsupported music runtime architecture: " + architecture);
        }
        if (os.contains("win")) return arm64 ? WINDOWS_ARM64 : WINDOWS_X64;
        if (os.contains("mac") || os.contains("darwin")) return arm64 ? MACOS_ARM64 : MACOS_X64;
        if (os.contains("linux")) return arm64 ? LINUX_ARM64 : LINUX_X64;
        throw new IllegalStateException("Unsupported music runtime operating system: " + osName);
    }
}
