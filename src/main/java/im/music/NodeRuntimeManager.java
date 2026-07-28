package im.music;

import im.webui.backend.BrowserPreparationProgress;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NodeRuntimeManager {
    private static final String MARKER = "runtime.info";
    private final Path root;
    private final HttpClient httpClient;

    public NodeRuntimeManager(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<Path> prepareAsync(Consumer<BrowserPreparationProgress> progress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return prepare(progress);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, command -> Thread.ofVirtual().name("Omix-Music-Node").start(command));
    }

    public Path prepare(Consumer<BrowserPreparationProgress> progress) throws Exception {
        return prepare(NodeRuntimeDescriptor.current(), progress);
    }

    Path prepare(
            NodeRuntimeDescriptor descriptor,
            Consumer<BrowserPreparationProgress> progress
    ) throws Exception {
        Path runtimeDirectory = runtimeDirectory(descriptor);
        Path executable = runtimeDirectory.resolve(descriptor.executableName());
        progress.accept(BrowserPreparationProgress.indeterminate("Checking Node.js runtime"));
        if (verify(runtimeDirectory, descriptor)) {
            progress.accept(BrowserPreparationProgress.determinate("Node.js runtime ready", 1.0F));
            return executable;
        }

        Files.createDirectories(root.resolve("downloads"));
        Files.createDirectories(root.resolve("runtime"));
        String sourceName = Path.of(descriptor.distributionPath()).getFileName().toString();
        Path partial = root.resolve("downloads").resolve(sourceName + ".part");
        Path temporary = root.resolve("runtime").resolve(
                ".node-v" + descriptor.version() + "-" + descriptor.platform().id() + "-" + UUID.randomUUID()
        );
        try {
            downloadAndVerify(descriptor, partial, progress);
            Files.createDirectories(temporary);
            Path temporaryExecutable = temporary.resolve(descriptor.executableName());
            progress.accept(BrowserPreparationProgress.indeterminate("Extracting Node.js runtime"));
            if (descriptor.distributionPath().endsWith(".zip")) {
                extractZipEntry(partial, descriptor.archiveEntry(), temporaryExecutable);
            } else {
                extractTarGzipEntry(partial, descriptor.archiveEntry(), temporaryExecutable);
            }
            makeExecutable(temporaryExecutable);
            verifyVersion(temporaryExecutable, descriptor.version());
            String binaryHash = sha256(temporaryExecutable);
            Files.writeString(
                    temporary.resolve(MARKER),
                    descriptor.version() + "\n"
                            + descriptor.platform().id() + "\n"
                            + descriptor.sha256() + "\n"
                            + binaryHash + "\n",
                    StandardCharsets.UTF_8
            );
            if (Files.exists(runtimeDirectory)) {
                deleteRecursively(runtimeDirectory);
            }
            moveDirectory(temporary, runtimeDirectory);
            Files.deleteIfExists(partial);
            progress.accept(BrowserPreparationProgress.determinate("Node.js runtime ready", 1.0F));
            return runtimeDirectory.resolve(descriptor.executableName());
        } catch (Exception exception) {
            Files.deleteIfExists(partial);
            deleteRecursively(temporary);
            throw exception;
        }
    }

    public boolean verify(Path runtimeDirectory, NodeRuntimeDescriptor descriptor) {
        try {
            Path executable = runtimeDirectory.resolve(descriptor.executableName());
            Path marker = runtimeDirectory.resolve(MARKER);
            if (!Files.isRegularFile(executable) || !Files.isRegularFile(marker)) {
                return false;
            }
            String[] values = Files.readString(marker, StandardCharsets.UTF_8).strip().split("\\R");
            if (values.length != 4
                    || !descriptor.version().equals(values[0])
                    || !descriptor.platform().id().equals(values[1])
                    || !descriptor.sha256().equals(values[2])
                    || !values[3].equals(sha256(executable))) {
                return false;
            }
            verifyVersion(executable, descriptor.version());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized void clearCurrent() throws IOException {
        deleteRecursively(runtimeDirectory(NodeRuntimeDescriptor.current()));
    }

    private Path runtimeDirectory(NodeRuntimeDescriptor descriptor) {
        return root.resolve("runtime")
                .resolve("node-v" + descriptor.version() + "-" + descriptor.platform().id())
                .normalize();
    }

    private void download(
            NodeRuntimeDescriptor descriptor,
            java.net.URI source,
            Path destination,
            Consumer<BrowserPreparationProgress> progress
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(source)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "Omix-Music-Node/" + descriptor.version())
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("Node.js download returned HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        long read = 0L;
        try (InputStream input = new BufferedInputStream(response.body());
             var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                if (length == 0) continue;
                output.write(buffer, 0, length);
                read += length;
                progress.accept(BrowserPreparationProgress.file(
                        "Downloading Node.js " + descriptor.version(),
                        read,
                        total
                ));
            }
        }
        if (total > 0L && read != total) {
            throw new IOException("Node.js download was truncated: " + read + " / " + total);
        }
    }

    private void downloadAndVerify(
            NodeRuntimeDescriptor descriptor,
            Path destination,
            Consumer<BrowserPreparationProgress> progress
    ) throws Exception {
        Exception lastFailure = null;
        for (java.net.URI source : descriptor.downloadUris()) {
            try {
                Files.deleteIfExists(destination);
                download(descriptor, source, destination, progress);
                verifyChecksum(destination, descriptor.sha256());
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                Files.deleteIfExists(destination);
            }
        }
        throw lastFailure == null ? new IOException("No Node.js download source configured") : lastFailure;
    }

    private static void verifyChecksum(Path file, String expected) throws Exception {
        String actual = sha256(file);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IOException("Node.js checksum mismatch");
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                if (length > 0) digest.update(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void extractTarGzipEntry(Path archive, String expectedEntry, Path destination)
            throws IOException {
        try (InputStream raw = Files.newInputStream(archive);
             InputStream gzip = new GZIPInputStream(new BufferedInputStream(raw))) {
            byte[] header = new byte[512];
            while (readFully(gzip, header)) {
                if (allZero(header)) break;
                String name = tarString(header, 0, 100);
                long size = tarOctal(header, 124, 12);
                int type = header[156] & 0xFF;
                if (name.equals(expectedEntry) && (type == 0 || type == '0')) {
                    Files.createDirectories(destination.getParent());
                    try (var output = Files.newOutputStream(destination)) {
                        copyExactly(gzip, output, size);
                    }
                    skipExactly(gzip, padding(size));
                    return;
                }
                skipExactly(gzip, size + padding(size));
            }
        }
        throw new IOException("Node executable was not present in archive: " + expectedEntry);
    }

    private static void extractZipEntry(Path archive, String expectedEntry, Path destination)
            throws IOException {
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(archive))
        )) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (!name.equals(expectedEntry)) continue;
                if (entry.isDirectory()) {
                    throw new IOException("Node executable archive entry is a directory");
                }
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        throw new IOException("Node executable was not present in archive: " + expectedEntry);
    }

    private static boolean readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) return offset == 0 ? false : throwTruncated();
            offset += read;
        }
        return true;
    }

    private static boolean throwTruncated() throws IOException {
        throw new IOException("Truncated tar header");
    }

    private static boolean allZero(byte[] value) {
        for (byte item : value) if (item != 0) return false;
        return true;
    }

    private static String tarString(byte[] value, int offset, int length) {
        int end = offset;
        while (end < offset + length && value[end] != 0) end++;
        return new String(value, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long tarOctal(byte[] value, int offset, int length) {
        long result = 0L;
        int end = offset + length;
        int index = offset;
        while (index < end && (value[index] == 0 || value[index] == ' ')) index++;
        while (index < end && value[index] >= '0' && value[index] <= '7') {
            result = (result << 3) + (value[index++] - '0');
        }
        return result;
    }

    private static long padding(long size) {
        return (512L - (size % 512L)) % 512L;
    }

    private static void copyExactly(InputStream input, java.io.OutputStream output, long count)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = count;
        while (remaining > 0L) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("Truncated tar entry");
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipExactly(InputStream input, long count) throws IOException {
        long remaining = count;
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            if (input.read() < 0) throw new IOException("Truncated tar entry");
            remaining--;
        }
    }

    private static void makeExecutable(Path executable) throws IOException {
        try {
            Files.setPosixFilePermissions(executable, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
        } catch (UnsupportedOperationException ignored) {
            if (!executable.toFile().setExecutable(true, true) && !executable.toFile().canExecute()) {
                throw new IOException("Unable to make Node.js executable");
            }
        }
    }

    private static void verifyVersion(Path executable, String expectedVersion) throws Exception {
        Process process = new ProcessBuilder(executable.toString(), "--version")
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Node.js version check timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0 || !output.equals("v" + expectedVersion)) {
            throw new IOException("Unexpected Node.js version: " + output);
        }
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteRecursively(Path target) throws IOException {
        if (target == null || !Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
