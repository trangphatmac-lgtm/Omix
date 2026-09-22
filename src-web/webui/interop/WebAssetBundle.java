package im.webui.interop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class WebAssetBundle {
    private static final String RESOURCE = "/assets/omix/webui/webui.zip";
    private final Map<String, byte[]> entries = new HashMap<>();

    WebAssetBundle() throws IOException {
        try (InputStream resource = WebAssetBundle.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) {
                throw new IOException("Missing bundled WebUI resource: " + RESOURCE);
            }
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = normalize(entry.getName());
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    zip.transferTo(output);
                    entries.put(name, output.toByteArray());
                }
            }
        }
    }

    byte[] get(String path) {
        String normalized = normalize(path);
        if (normalized.isEmpty()) {
            normalized = "index.html";
        }
        return entries.get(normalized);
    }

    private static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            return "";
        }
        return normalized;
    }
}
