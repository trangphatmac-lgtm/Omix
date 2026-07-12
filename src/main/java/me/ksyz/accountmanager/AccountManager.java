package me.ksyz.accountmanager;

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
import java.util.ArrayList;
import java.util.Optional;

public final class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(MinecraftClient.getInstance().runDirectory, "remix.accounts.json");
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
                System.err.println("Couldn't create remix.accounts.json: " + e.getMessage());
            }
        }
        load();
    }

    public static void load() {
        accounts.clear();
        loadFrom(FILE);
    }

    private static void loadFrom(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) {
                return;
            }
            JsonArray array = json.getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                accounts.add(new Account(
                        readString(object, "refreshToken"),
                        readString(object, "accessToken"),
                        readString(object, "username"),
                        Optional.ofNullable(object.get("unban")).map(JsonElement::getAsLong).orElse(0L),
                        readString(object, "clientId"),
                        readString(object, "scope")
                ));
            }
        } catch (Exception e) {
            System.err.println("Couldn't load " + file.getName() + ": " + e.getMessage());
        }
    }

    public static void save() {
        try {
            JsonArray array = new JsonArray();
            for (Account account : accounts) {
                JsonObject object = new JsonObject();
                object.addProperty("refreshToken", account.getRefreshToken());
                object.addProperty("accessToken", account.getAccessToken());
                object.addProperty("username", account.getUsername());
                object.addProperty("unban", account.getUnban());
                object.addProperty("clientId", account.getClientId());
                object.addProperty("scope", account.getScope());
                array.add(object);
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(FILE))) {
                writer.println(GSON.toJson(array));
            }
        } catch (IOException e) {
            System.err.println("Couldn't save remix.accounts.json: " + e.getMessage());
        }
    }

    private static String readString(JsonObject object, String name) {
        return Optional.ofNullable(object.get(name)).map(JsonElement::getAsString).orElse("");
    }
}
