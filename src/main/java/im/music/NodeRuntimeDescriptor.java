package im.music;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record NodeRuntimeDescriptor(
        String version,
        MusicPlatform platform,
        String distributionPath,
        String sha256,
        String archiveEntry
) {
    public static final String VERSION = "24.18.0";
    private static final URI OFFICIAL_ROOT = URI.create("https://nodejs.org/dist/v" + VERSION + "/");
    private static final Map<MusicPlatform, NodeRuntimeDescriptor> DESCRIPTORS = createDescriptors();

    public URI downloadUri() {
        return OFFICIAL_ROOT.resolve(distributionPath);
    }

    public List<URI> downloadUris() {
        List<URI> result = new ArrayList<>();
        result.add(downloadUri());
        String mirror = System.getProperty("omix.music.nodeMirror", "").trim();
        if (!mirror.isEmpty()) {
            URI fallback = normalizeMirror(mirror).resolve(distributionPath);
            if (!fallback.equals(result.getFirst())) result.add(fallback);
        }
        return List.copyOf(result);
    }

    public String executableName() {
        return platform.windows() ? "node.exe" : "node";
    }

    public static NodeRuntimeDescriptor current() {
        return forPlatform(MusicPlatform.current());
    }

    public static NodeRuntimeDescriptor forPlatform(MusicPlatform platform) {
        NodeRuntimeDescriptor descriptor = DESCRIPTORS.get(platform);
        if (descriptor == null) {
            throw new IllegalStateException("No Node runtime descriptor for " + platform);
        }
        return descriptor;
    }

    public static Map<MusicPlatform, NodeRuntimeDescriptor> all() {
        return Map.copyOf(DESCRIPTORS);
    }

    private static URI normalizeMirror(String value) {
        URI mirror = URI.create(value.endsWith("/") ? value : value + "/");
        if (!"https".equalsIgnoreCase(mirror.getScheme())) {
            throw new IllegalArgumentException("Node runtime mirror must use HTTPS");
        }
        return mirror;
    }

    private static Map<MusicPlatform, NodeRuntimeDescriptor> createDescriptors() {
        Map<MusicPlatform, NodeRuntimeDescriptor> values = new EnumMap<>(MusicPlatform.class);
        values.put(MusicPlatform.WINDOWS_X64, archive(
                MusicPlatform.WINDOWS_X64,
                "node-v" + VERSION + "-win-x64.zip",
                "0ae68406b42d7725661da979b1403ec9926da205c6770827f33aac9d8f26e821",
                "node-v" + VERSION + "-win-x64/node.exe"
        ));
        values.put(MusicPlatform.WINDOWS_ARM64, archive(
                MusicPlatform.WINDOWS_ARM64,
                "node-v" + VERSION + "-win-arm64.zip",
                "f274669adb93b1fd0fbf8f21fd078609e9dcc84333d4f2718d2dde3f9a161a01",
                "node-v" + VERSION + "-win-arm64/node.exe"
        ));
        values.put(MusicPlatform.MACOS_X64, archive(
                MusicPlatform.MACOS_X64,
                "node-v" + VERSION + "-darwin-x64.tar.gz",
                "dfd0dbd3e721503434df7b7205e719f61b3a3a31b2bcf9729b8b91fea240f080",
                "node-v" + VERSION + "-darwin-x64/bin/node"
        ));
        values.put(MusicPlatform.MACOS_ARM64, archive(
                MusicPlatform.MACOS_ARM64,
                "node-v" + VERSION + "-darwin-arm64.tar.gz",
                "e1a97e14c99c803e96c7339403282ea05a499c32f8d83defe9ef5ec66f979ed1",
                "node-v" + VERSION + "-darwin-arm64/bin/node"
        ));
        values.put(MusicPlatform.LINUX_X64, archive(
                MusicPlatform.LINUX_X64,
                "node-v" + VERSION + "-linux-x64.tar.gz",
                "783130984963db7ba9cbd01089eaf2c2efb055c7c1693c943174b967b3050cb8",
                "node-v" + VERSION + "-linux-x64/bin/node"
        ));
        values.put(MusicPlatform.LINUX_ARM64, archive(
                MusicPlatform.LINUX_ARM64,
                "node-v" + VERSION + "-linux-arm64.tar.gz",
                "6b4484c2190274175df9aa8f28e2d758a819cb1c1fe6ab481e2f95b463ab8508",
                "node-v" + VERSION + "-linux-arm64/bin/node"
        ));
        return values;
    }

    private static NodeRuntimeDescriptor archive(
            MusicPlatform platform,
            String path,
            String sha256,
            String archiveEntry
    ) {
        return new NodeRuntimeDescriptor(VERSION, platform, path, sha256, archiveEntry);
    }
}
