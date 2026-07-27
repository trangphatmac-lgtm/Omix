package cn.omix.config;

import cn.omix.Client;
import cn.omix.util.IMinecraft;
import lombok.Getter;

import java.io.File;

@Getter
public abstract class Config implements IMinecraft {
    private static final File DIRECTORY = new File(Client.name, "configs");

    private final String name;
    private final File file;

    public Config(final String name) {
        if (!DIRECTORY.exists() && !DIRECTORY.mkdirs()) {
            Client.logger.debug("Failed to create configs directory: {}", DIRECTORY.getPath());
        }

        this.name = name;
        this.file = new File(DIRECTORY, name.toLowerCase() + ".json");
    }

    public static File getDirectory() {
        return DIRECTORY;
    }

    public abstract void save();
    public abstract void load();
}
