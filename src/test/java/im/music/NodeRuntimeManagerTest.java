package im.music;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRuntimeManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void downloadsVerifiesAndReusesCachedRuntime() throws Exception {
        MusicPlatform platform = MusicPlatform.detect(
                System.getProperty("os.name"),
                System.getProperty("os.arch")
        );
        Assumptions.assumeFalse(platform.windows());
        String entry = "node-v" + NodeRuntimeDescriptor.VERSION + "-fixture/bin/node";
        byte[] archive = executableZip(entry);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = fixtureServer(archive, requests);
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/node.zip";
            NodeRuntimeDescriptor descriptor = new NodeRuntimeDescriptor(
                    NodeRuntimeDescriptor.VERSION,
                    platform,
                    url,
                    sha256(archive),
                    entry
            );
            NodeRuntimeManager manager = new NodeRuntimeManager(temporaryDirectory);

            Path first = manager.prepare(descriptor, ignored -> {});
            Path second = manager.prepare(descriptor, ignored -> {});

            assertEquals(first, second);
            assertTrue(Files.isExecutable(first));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsChecksumMismatchAndRemovesPartialDownload() throws Exception {
        MusicPlatform platform = MusicPlatform.detect(
                System.getProperty("os.name"),
                System.getProperty("os.arch")
        );
        Assumptions.assumeFalse(platform.windows());
        String entry = "node-v" + NodeRuntimeDescriptor.VERSION + "-fixture/bin/node";
        byte[] archive = executableZip(entry);
        HttpServer server = fixtureServer(archive, new AtomicInteger());
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/node.zip";
            NodeRuntimeDescriptor descriptor = new NodeRuntimeDescriptor(
                    NodeRuntimeDescriptor.VERSION,
                    platform,
                    url,
                    "0".repeat(64),
                    entry
            );
            NodeRuntimeManager manager = new NodeRuntimeManager(temporaryDirectory);

            assertThrows(
                    Exception.class,
                    () -> manager.prepare(descriptor, ignored -> {})
            );
            Path downloads = temporaryDirectory.resolve("downloads");
            if (Files.isDirectory(downloads)) {
                try (var files = Files.list(downloads)) {
                    assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
                }
            }
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer fixtureServer(byte[] body, AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/node.zip", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static byte[] executableZip(String entryName) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(("#!/bin/sh\nprintf 'v" + NodeRuntimeDescriptor.VERSION + "'\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../../outside"));
            zip.write("must-not-extract".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value)
        );
    }
}
