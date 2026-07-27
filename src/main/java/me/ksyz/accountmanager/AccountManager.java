package me.ksyz.accountmanager;

import cn.omix.security.SafeStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ksyz.accountmanager.auth.Account;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(MinecraftClient.getInstance().runDirectory, "omix.accounts.json");
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );
    private static boolean initialized;
    public static final ArrayList<Account> accounts = new ArrayList<>();

    private AccountManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Events.register();
        if (!FILE.exists()) {
            try {
                if ((FILE.getParentFile().exists() || FILE.getParentFile().mkdirs()) && FILE.createNewFile()) {
                    save();
                }
            } catch (IOException e) {
                System.err.println("Couldn't create omix.accounts.json: " + e.getMessage());
            }
        }
        load();
    }

    public static void load() {
        accounts.clear();
        if (loadFrom(FILE)) {
            save();
        }
    }

    private static boolean loadFrom(File file) {
        boolean migrationNeeded = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) {
                return false;
            }
            JsonArray array = json.getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                String storedRefreshToken = readString(object, "refreshToken");
                String storedAccessToken = readString(object, "accessToken");
                accounts.add(new Account(
                        SafeStorage.decrypt(storedRefreshToken),
                        SafeStorage.decrypt(storedAccessToken),
                        readString(object, "username"),
                        Optional.ofNullable(object.get("unban")).map(JsonElement::getAsLong).orElse(0L),
                        readString(object, "clientId"),
                        readString(object, "scope")
                ));
                migrationNeeded |= needsMigration(storedRefreshToken)
                        || needsMigration(storedAccessToken);
            }
        } catch (Exception e) {
            System.err.println("Couldn't load " + file.getName() + ": " + e.getMessage());
            return false;
        }
        return migrationNeeded;
    }

    public static void save() {
        try {
            JsonArray array = new JsonArray();
            for (Account account : accounts) {
                JsonObject object = new JsonObject();
                object.addProperty("refreshToken", SafeStorage.encrypt(account.getRefreshToken()));
                object.addProperty("accessToken", SafeStorage.encrypt(account.getAccessToken()));
                object.addProperty("username", account.getUsername());
                object.addProperty("unban", account.getUnban());
                object.addProperty("clientId", account.getClientId());
                object.addProperty("scope", account.getScope());
                array.add(object);
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(FILE))) {
                writer.println(GSON.toJson(array));
            }
            try {
                Files.setPosixFilePermissions(FILE.toPath(), OWNER_ONLY_PERMISSIONS);
            } catch (UnsupportedOperationException ignored) {
                // POSIX permissions are not available on every supported platform.
            }
        } catch (IOException e) {
            System.err.println("Couldn't save omix.accounts.json: " + e.getMessage());
        }
    }

    private static boolean needsMigration(String value) {
        return value != null
                && !value.isEmpty()
                && (!SafeStorage.isEncrypted(value) || SafeStorage.hasLegacyHeader(value));
    }

    private static String readString(JsonObject object, String name) {
        return Optional.ofNullable(object.get(name)).map(JsonElement::getAsString).orElse("");
    }
}
