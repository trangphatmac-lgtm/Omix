package cn.omix.util.script;

import com.google.gson.*;
import cn.omix.util.node.NodeRuntimeManager;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;

/** Exports the versioned, bundled developer kit without installing packages at game startup. */
public final class ScriptDistribution {
    private ScriptDistribution() {}
    public static void export(Path scripts) throws IOException {
        Path target = scripts.resolve(".agent"); Files.createDirectories(target);
        try (InputStream resource = ScriptDistribution.class.getResourceAsStream("/assets/omix/script/developer-kit.zip")) {
            if (resource == null) throw new IOException("Bundled script developer kit missing");
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    Path output = target.resolve(entry.getName()).normalize();
                    if (!output.startsWith(target) || entry.getName().contains("..")) throw new IOException("Invalid kit entry");
                    if (entry.isDirectory()) continue;
                    Files.createDirectories(output.getParent()); Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        String node = "node";
        Path config = target.resolve("mcp-config.json");
        if (Files.isRegularFile(config)) {
            try {
                String previous = JsonParser.parseString(Files.readString(config)).getAsJsonObject()
                        .getAsJsonObject("mcpServers").getAsJsonObject("omix").get("command").getAsString();
                if (Path.of(previous).isAbsolute() && Files.isExecutable(Path.of(previous))) node = previous;
            } catch (RuntimeException ignored) { }
        }
        writeConfig(scripts, node);
    }
    private static Path writeConfig(Path scripts, String node) throws IOException {
        JsonObject server = new JsonObject(); server.addProperty("command", node);
        JsonArray args = new JsonArray(); args.add(scripts.resolve(".agent/mcp/server.mjs").toAbsolutePath().toString());
        args.add("--game-dir"); args.add(scripts.getParent().getParent().toAbsolutePath().toString()); server.add("args", args);
        JsonObject servers = new JsonObject(); servers.add("omix", server); JsonObject config = new JsonObject(); config.add("mcpServers", servers);
        Path output = scripts.resolve(".agent/mcp-config.json"); ScriptFiles.atomicWrite(output, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        Path launcher = scripts.resolve(".agent/launch-mcp.sh");
        String quotedNode = "'" + node.replace("'", "'\"'\"'") + "'";
        ScriptFiles.atomicWrite(launcher, "#!/bin/sh\nset -eu\nkit_dir=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\nexec " + quotedNode + " \"$kit_dir/mcp/server.mjs\" --game-dir \"$kit_dir/../../..\" \"$@\"\n");
        launcher.toFile().setExecutable(true, true);
        ScriptFiles.atomicWrite(scripts.resolve(".agent/launch-mcp.cmd"), "@echo off\r\n\"" + node.replace("%", "%%") + "\" \"%~dp0mcp\\server.mjs\" --game-dir \"%~dp0..\\..\\..\" %*\r\n");
        return output;
    }
    public static void prepareNode(Path scripts) {
        new NodeRuntimeManager(scripts.getParent().resolve("music")).prepareAsync(ignored -> {}).whenComplete((node, error) -> {
            String message;
            try { if (error != null) throw new IOException(error); message = "MCP configuration: " + writeConfig(scripts, node.toString()); }
            catch (Exception failure) { message = "MCP preparation failed: " + failure.getMessage(); }
            String text = message; MinecraftClient.getInstance().execute(() -> cn.omix.util.Util.log(text));
        });
    }
}
